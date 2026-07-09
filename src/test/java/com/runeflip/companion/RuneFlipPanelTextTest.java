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
	public void topFlipStatsLineShowsLegsExpectedProfitAndRoi()
	{
		RuneFlipData.FastFlipItem flip = new RuneFlipData.FastFlipItem();
		flip.suggestedBuyPrice = 100L;
		flip.suggestedSellPrice = 108L;
		flip.estimatedProfit = 600L;
		flip.roi = 0.06;

		String line = RuneFlipPanel.topFlipStatsLine(flip);
		assertTrue(line.contains("Buy"));
		assertTrue(line.contains("100 gp"));
		assertTrue(line.contains("Sell"));
		assertTrue(line.contains("108 gp"));
		// Whole-flip expected profit (not per-item) and ROI (locale-agnostic:
		// the decimal separator depends on the JVM default locale).
		assertTrue(line.contains("+600 gp"));
		assertTrue(line.contains("ROI 6"));
		assertTrue(line.contains("%"));
	}

	@Test
	public void shortDisclaimerIsTheCompactComplianceRule()
	{
		assertEquals("Review manually.", RuneFlipPanel.SHORT_DISCLAIMER);
	}

	@Test
	public void strategySummaryRendersTheBackendDescriptionVerbatim()
	{
		RuneFlipData.FastFlipStrategy strategy = new RuneFlipData.FastFlipStrategy();
		strategy.timeframeMinutes = 30;
		strategy.riskLevel = "MEDIUM";
		strategy.isDefault = Boolean.FALSE;
		strategy.description = "30m timeframe · risk up to MEDIUM";

		assertEquals("Strategy: 30m timeframe · risk up to MEDIUM",
			RuneFlipPanel.strategySummaryLine(strategy));

		// Pre-0.8.0 backend (no echo) or an empty description: no line at all.
		assertEquals(null, RuneFlipPanel.strategySummaryLine(null));
		strategy.description = "   ";
		assertEquals(null, RuneFlipPanel.strategySummaryLine(strategy));
	}

	@Test
	public void actionLineRendersLabelAndReasonVerbatimWithTypeColor()
	{
		RuneFlipData.RecommendedAction action = new RuneFlipData.RecommendedAction();
		action.actionType = "MODIFY_BUY";
		action.actionLabel = "Modify buy";
		action.actionReason = "Buy offer at 9,000 gp sits below the recommended 10,050 gp.";
		action.reviewOnly = Boolean.TRUE;

		String line = RuneFlipPanel.actionLine(action);
		assertTrue(line.contains("Action:"));
		assertTrue(line.contains("<b>Modify buy</b>"));
		assertTrue(line.contains("9,000 gp"));
		assertTrue(line.contains("10,050 gp"));
		// MODIFY_* renders in the gold "review" tone.
		assertTrue(line.contains("#e3b75d"));
	}

	@Test
	public void actionLineDegradesHonestly()
	{
		// Pre-0.8.2 backend: no action block — nothing extra is rendered.
		assertEquals(null, RuneFlipPanel.actionLine(null));

		// An empty label produces no line (never an "Action:" with no verb).
		RuneFlipData.RecommendedAction blank = new RuneFlipData.RecommendedAction();
		blank.actionLabel = "   ";
		assertEquals(null, RuneFlipPanel.actionLine(blank));

		// A label without a reason still renders, just without the "— …" tail.
		RuneFlipData.RecommendedAction labelOnly = new RuneFlipData.RecommendedAction();
		labelOnly.actionType = "HOLD";
		labelOnly.actionLabel = "Hold";
		String line = RuneFlipPanel.actionLine(labelOnly);
		assertTrue(line.contains("<b>Hold</b>"));
		assertFalse(line.contains("—"));
	}

	@Test
	public void actionColorsMapByTypeAndFallBackToMuted()
	{
		assertEquals("#4cba86", RuneFlipPanel.actionColorHex("BUY_NEW"));
		assertEquals("#9fb6ef", RuneFlipPanel.actionColorHex("SELL_EXISTING"));
		assertEquals("#9fb6ef", RuneFlipPanel.actionColorHex("HOLD"));
		assertEquals("#e3b75d", RuneFlipPanel.actionColorHex("MODIFY_SELL"));
		assertEquals("#e26a5e", RuneFlipPanel.actionColorHex("ABORT_BUY"));
		assertEquals("#e26a5e", RuneFlipPanel.actionColorHex("AVOID"));
		assertEquals("#878d9c", RuneFlipPanel.actionColorHex(null));
		assertEquals("#878d9c", RuneFlipPanel.actionColorHex("BANANA"));
	}

	// ── Assisted Offer Setup (v0.8.3) — opt-in gating ────────────────────────

	private static RuneFlipData.RecommendedAction buyNew(Long price, Long qty)
	{
		RuneFlipData.RecommendedAction a = new RuneFlipData.RecommendedAction();
		a.actionType = "BUY_NEW";
		a.actionLabel = "Buy new";
		a.reviewOnly = Boolean.TRUE;
		a.targetPrice = price;
		a.targetQuantity = qty;
		return a;
	}

	@Test
	public void assistedSetupHiddenUnlessOptedIn()
	{
		RuneFlipData.RecommendedAction action = buyNew(96L, 1000L);
		// Config OFF (the default): buttons never show, even with a target.
		assertFalse(RuneFlipPanel.showAssistedSetup(action, false));
		// Config ON + a concrete target price: buttons show.
		assertTrue(RuneFlipPanel.showAssistedSetup(action, true));
	}

	@Test
	public void assistedSetupHiddenWhenNoTargetOrNoAction()
	{
		// HOLD / ABORT / AVOID carry no target price → no buttons even when ON.
		RuneFlipData.RecommendedAction hold = new RuneFlipData.RecommendedAction();
		hold.actionType = "HOLD";
		hold.reviewOnly = Boolean.TRUE;
		hold.targetPrice = null;
		assertFalse(RuneFlipPanel.showAssistedSetup(hold, true));
		// No action at all (pre-0.8.2 backend) → nothing to set up.
		assertFalse(RuneFlipPanel.showAssistedSetup(null, true));
	}

	@Test
	public void assistedSetupRequiresReviewOnlyInvariant()
	{
		// Defensive: an action that is somehow not reviewOnly must never get
		// setup buttons (the compliance invariant is the gate).
		RuneFlipData.RecommendedAction notReview = buyNew(96L, 1000L);
		notReview.reviewOnly = Boolean.FALSE;
		assertFalse(RuneFlipPanel.showAssistedSetup(notReview, true));
		notReview.reviewOnly = null;
		assertFalse(RuneFlipPanel.showAssistedSetup(notReview, true));
	}

	@Test
	public void copyQuantityOnlyWhenTargetQuantityPresent()
	{
		assertTrue(RuneFlipPanel.showCopyQuantity(buyNew(96L, 1000L)));
		// Price Edge actions carry a price but no quantity → no Copy qty.
		assertFalse(RuneFlipPanel.showCopyQuantity(buyNew(96L, null)));
		assertFalse(RuneFlipPanel.showCopyQuantity(null));
	}

	@Test
	public void assistedSetupNoteStatesPrepareOnly()
	{
		// The compliance copy must say values are prepared, not executed.
		assertTrue(RuneFlipPanel.ASSISTED_SETUP_NOTE.contains("prepares values only"));
		assertTrue(RuneFlipPanel.ASSISTED_SETUP_NOTE.contains("review and confirm"));
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
