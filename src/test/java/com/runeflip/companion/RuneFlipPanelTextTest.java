package com.runeflip.companion;

import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The completed-offers summary must never render corrupted text: names are
 * sanitized (control chars out, whitespace collapsed, length capped) and the
 * relative time stays ultra-short so rows never wrap in the narrow sidebar.
 * Static helpers only — no Swing is instantiated here.
 */
public class RuneFlipPanelTextTest
{
	@Test
	public void cleanNamesPassThroughUnchanged()
	{
		assertEquals("Amulet of power", RuneFlipPanel.sanitizeName("Amulet of power"));
		assertEquals("Cosmic rune", RuneFlipPanel.sanitizeName("  Cosmic rune  "));
	}

	@Test
	public void controlAndReplacementCharactersAreRemoved()
	{
		assertEquals("Abyssal whip",
			RuneFlipPanel.sanitizeName("Abyssal whip\u0007\uFFFD"));
		assertEquals("Rune axe", RuneFlipPanel.sanitizeName("Rune\n\taxe"));
	}

	@Test
	public void emptyOrNullBecomesPlaceholder()
	{
		assertEquals("Unknown item", RuneFlipPanel.sanitizeName(null));
		assertEquals("Unknown item", RuneFlipPanel.sanitizeName("   "));
		assertEquals("Unknown item", RuneFlipPanel.sanitizeName(""));
	}

	@Test
	public void absurdlyLongNamesAreCappedWithEllipsis()
	{
		StringBuilder longName = new StringBuilder();
		for (int i = 0; i < 30; i++)
		{
			longName.append("very ");
		}
		String out = RuneFlipPanel.sanitizeName(longName.toString());
		assertTrue(out.length() <= 40);
		assertTrue(out.endsWith("…"));
		assertFalse(out.contains("  "));
	}

	@Test
	public void priceEdgeTargetLineShowsWikiVsRuneFlipTargets()
	{
		RuneFlipData.PriceEdge edge = new RuneFlipData.PriceEdge();
		edge.wikiLowPrice = 119L;
		edge.wikiHighPrice = 130L;
		edge.recommendedBuyPrice = 122L;
		edge.recommendedSellPrice = 129L;
		edge.profitPerItem = 5L;
		edge.confidence = 84;
		edge.recommendation = "QUICK";

		String line = RuneFlipPanel.priceEdgeTargetLine(edge);
		assertTrue(line.contains("Wiki buy"));
		assertTrue(line.contains("119 gp"));
		assertTrue(line.contains("quick buy 122 gp"));
		assertTrue(line.contains("target sell"));
		assertTrue(line.contains("129 gp"));

		String profit = RuneFlipPanel.priceEdgeProfitLine(edge);
		assertTrue(profit.contains("+5 gp/item after tax"));
		assertTrue(profit.contains("conf 84"));
	}

	@Test
	public void priceEdgeLinesDegradeHonestly()
	{
		// Pre-0.7.1 backend: no block at all — nothing extra is rendered.
		assertEquals(null, RuneFlipPanel.priceEdgeTargetLine(null));
		assertEquals(null, RuneFlipPanel.priceEdgeProfitLine(null));

		// NOT_RECOMMENDED: recommended prices are null — say so, never invent.
		RuneFlipData.PriceEdge notRecommended = new RuneFlipData.PriceEdge();
		notRecommended.recommendation = "NOT_RECOMMENDED";
		String line = RuneFlipPanel.priceEdgeTargetLine(notRecommended);
		assertTrue(line.contains("not recommended"));
		assertEquals(null, RuneFlipPanel.priceEdgeProfitLine(notRecommended));
	}

	@Test
	public void priceEdgeDisclaimerPrefersTheBackendText()
	{
		RuneFlipData.FastFlipItem withEdge = new RuneFlipData.FastFlipItem();
		withEdge.priceEdge = new RuneFlipData.PriceEdge();
		withEdge.priceEdge.disclaimer = "Targets are estimates. Review manually.";
		RuneFlipData.FastFlipItem withoutEdge = new RuneFlipData.FastFlipItem();

		assertEquals("Targets are estimates. Review manually.",
			RuneFlipPanel.priceEdgeDisclaimer(
				java.util.Arrays.asList(withoutEdge, withEdge), 2));
		// Targets without text fall back to the fixed disclaimer.
		withEdge.priceEdge.disclaimer = null;
		assertEquals(RuneFlipPanel.PRICE_EDGE_DISCLAIMER,
			RuneFlipPanel.priceEdgeDisclaimer(
				java.util.Arrays.asList(withEdge), 1));
		// No targets at all: no extra disclaimer line.
		assertEquals(null, RuneFlipPanel.priceEdgeDisclaimer(
			java.util.Arrays.asList(withoutEdge), 1));
		assertEquals(null, RuneFlipPanel.priceEdgeDisclaimer(null, 3));
	}

	@Test
	public void shortTimeAgoUsesCompactUnits()
	{
		long now = Instant.parse("2026-07-06T12:00:00Z").toEpochMilli();
		assertEquals("now",
			RuneFlipPanel.shortTimeAgo("2026-07-06T11:59:40Z", now));
		assertEquals("5m",
			RuneFlipPanel.shortTimeAgo("2026-07-06T11:55:00Z", now));
		assertEquals("3h",
			RuneFlipPanel.shortTimeAgo("2026-07-06T09:00:00Z", now));
		assertEquals("2d",
			RuneFlipPanel.shortTimeAgo("2026-07-04T10:00:00Z", now));
	}

	@Test
	public void fastFlipSpeedLabelsAreHumanReadableAndNeverGuess()
	{
		assertEquals("Very fast", RuneFlipPanel.speedLabel("VERY_FAST"));
		assertEquals("Fast", RuneFlipPanel.speedLabel("FAST"));
		assertEquals("Moderate", RuneFlipPanel.speedLabel("MODERATE"));
		assertEquals("Slow", RuneFlipPanel.speedLabel("SLOW"));
		assertEquals("Unknown", RuneFlipPanel.speedLabel("UNKNOWN"));
		assertEquals("Unknown", RuneFlipPanel.speedLabel(null));
		assertEquals("Unknown", RuneFlipPanel.speedLabel("WARP_SPEED"));
	}

	@Test
	public void fastFlipNullFiguresRenderAsDashNotZero()
	{
		assertEquals("—", RuneFlipPanel.gpOrDash(null));
		assertTrue(RuneFlipPanel.gpOrDash(1_500L).endsWith(" gp"));
		assertEquals("—", RuneFlipPanel.profitPerItemLabel(null));
		assertTrue(RuneFlipPanel.profitPerItemLabel(120L).startsWith("+"));
		assertFalse(RuneFlipPanel.profitPerItemLabel(-5L).startsWith("+"));
	}

	@Test
	public void fastFlipRiskColorsFallBackToMutedForUnknownLevels()
	{
		assertEquals("#4cba86", RuneFlipPanel.riskColorHex("LOW"));
		assertEquals("#e3b75d", RuneFlipPanel.riskColorHex("MEDIUM"));
		assertEquals("#e8894a", RuneFlipPanel.riskColorHex("HIGH"));
		assertEquals("#e26a5e", RuneFlipPanel.riskColorHex("AVOID"));
		assertEquals("#878d9c", RuneFlipPanel.riskColorHex(null));
		assertEquals("#878d9c", RuneFlipPanel.riskColorHex("BANANA"));
	}

	@Test
	public void badTimestampsRenderNothingInsteadOfGarbage()
	{
		long now = Instant.parse("2026-07-06T12:00:00Z").toEpochMilli();
		assertEquals("", RuneFlipPanel.shortTimeAgo("not-a-date", now));
		assertEquals("", RuneFlipPanel.shortTimeAgo(null, now));
	}
}
