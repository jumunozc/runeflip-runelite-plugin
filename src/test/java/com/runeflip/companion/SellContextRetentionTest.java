package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Sell-context retention (v0.8.12) — the pure decisions behind the buy→sell
 * flow fix: the setup side detection, the side-aware fetch query, and the
 * panel rules that keep the sell targets visible (instead of "No RuneFlip
 * target yet") when the player needs them to close a flip.
 */
public class SellContextRetentionTest
{
	// ── side detection (read-only GE_OFFER_CREATION_TYPE varbit) ────────────

	@Test
	public void offerCreationTypeMapsToTheApiSide()
	{
		assertEquals("BUY", GeItemSelection.sideOf(0));
		assertEquals("SELL", GeItemSelection.sideOf(1));
		// Anything unexpected stays the pre-v0.8.12 default.
		assertEquals("BUY", GeItemSelection.sideOf(-1));
		assertEquals("BUY", GeItemSelection.sideOf(7));
	}

	@Test
	public void sideParamAppendsToTheStrategyQuery()
	{
		assertEquals("side=SELL",
			RuneFlipCompanionPlugin.withSideParam("", "SELL"));
		assertEquals("side=BUY",
			RuneFlipCompanionPlugin.withSideParam(null, "BUY"));
		assertEquals("timeframeMinutes=30&riskLevel=LOW&side=SELL",
			RuneFlipCompanionPlugin.withSideParam(
				"timeframeMinutes=30&riskLevel=LOW", "SELL"));
	}

	// ── header title per side ────────────────────────────────────────────────

	@Test
	public void headerTitleNamesTheSide()
	{
		assertEquals("SELECTED GE ITEM · SELL",
			RuneFlipPanel.selectedHeaderTitle("SELL"));
		assertEquals("SELECTED GE ITEM · BUY",
			RuneFlipPanel.selectedHeaderTitle("BUY"));
		assertEquals("SELECTED GE ITEM", RuneFlipPanel.selectedHeaderTitle(null));
	}

	// ── fixtures ─────────────────────────────────────────────────────────────

	private static RuneFlipData.FastFlipItemContextResponse notRecommendedWithSellTargets()
	{
		RuneFlipData.FastFlipItemContextResponse res =
			new RuneFlipData.FastFlipItemContextResponse();
		res.itemId = 560;
		res.recommended = Boolean.FALSE;
		res.notRecommendedReason = "Margin no longer clears the profit floors.";
		res.priceEdge = new RuneFlipData.PriceEdge();
		res.priceEdge.wikiLowPrice = 1_000L;
		res.priceEdge.wikiHighPrice = 1_060L;
		res.priceEdge.safeSellPrice = 1_055L;
		res.priceEdge.quickSellPrice = 1_020L;
		res.priceEdge.recommendedSellPrice = 1_049L;
		res.priceEdge.tax = 21L;
		res.priceEdge.profitPerItem = -3L;
		return res;
	}

	// ── the fix: recommended=false keeps the SELL card ───────────────────────

	@Test
	public void sellSetupKeepsTheFullCardWhenSellTargetsExist()
	{
		RuneFlipData.FastFlipItemContextResponse res = notRecommendedWithSellTargets();
		// The real-play bug: this used to collapse to "No RuneFlip target yet"
		// exactly when the player needed the sell target to close the flip.
		assertTrue(RuneFlipPanel.showFullContextCard(res, "SELL"));
		// The buy side keeps the honest refusal — never open a NEW flip on a
		// rejected item.
		assertFalse(RuneFlipPanel.showFullContextCard(res, "BUY"));
		assertFalse(RuneFlipPanel.showFullContextCard(res, null));
	}

	@Test
	public void recommendedItemsAlwaysShowTheFullCardOnBothSides()
	{
		RuneFlipData.FastFlipItemContextResponse res = notRecommendedWithSellTargets();
		res.recommended = Boolean.TRUE;
		assertTrue(RuneFlipPanel.showFullContextCard(res, "BUY"));
		assertTrue(RuneFlipPanel.showFullContextCard(res, "SELL"));
		assertTrue(RuneFlipPanel.showFullContextCard(res, null));
	}

	@Test
	public void sellSetupWithNoSellDataStillDegradesHonestly()
	{
		RuneFlipData.FastFlipItemContextResponse res =
			new RuneFlipData.FastFlipItemContextResponse();
		res.recommended = Boolean.FALSE;
		assertFalse(RuneFlipPanel.showFullContextCard(res, "SELL"));
		assertFalse(RuneFlipPanel.showFullContextCard(null, "SELL"));
		assertFalse(RuneFlipPanel.hasSellTargets(res));
	}

	@Test
	public void sellTargetsAreFoundInComparisonOrPriceEdge()
	{
		RuneFlipData.FastFlipItemContextResponse res =
			new RuneFlipData.FastFlipItemContextResponse();
		res.targetComparison = new RuneFlipData.TargetComparison();
		res.targetComparison.targetSell = 1_049L;
		assertTrue(RuneFlipPanel.hasSellTargets(res));

		RuneFlipData.FastFlipItemContextResponse edgeOnly =
			new RuneFlipData.FastFlipItemContextResponse();
		edgeOnly.priceEdge = new RuneFlipData.PriceEdge();
		edgeOnly.priceEdge.quickSellPrice = 1_020L;
		assertTrue(RuneFlipPanel.hasSellTargets(edgeOnly));
	}

	// ── sell lines + the low/negative edge warning ───────────────────────────

	@Test
	public void sellLinesShowTargetsTaxAndAfterTaxEdge()
	{
		RuneFlipData.FastFlipItemContextResponse res = notRecommendedWithSellTargets();
		String targets = RuneFlipPanel.sellTargetsLine(res.priceEdge);
		assertTrue(targets.contains("Safe sell"));
		assertTrue(targets.contains("1,055 gp"));
		assertTrue(targets.contains("Quick sell"));
		assertTrue(targets.contains("1,020 gp"));

		String tax = RuneFlipPanel.sellTaxProfitLine(res.priceEdge);
		assertTrue(tax.contains("Tax"));
		assertTrue(tax.contains("21 gp"));
		assertTrue(tax.contains("/item after tax"));
		// The negative edge renders in the red tone.
		assertTrue(tax.contains("#e06767"));

		assertNull(RuneFlipPanel.sellTargetsLine(null));
		assertNull(RuneFlipPanel.sellTaxProfitLine(new RuneFlipData.PriceEdge()));
	}

	@Test
	public void lowOrNegativeEdgeShowsTheWarningPositiveDoesNot()
	{
		RuneFlipData.FastFlipItemContextResponse res = notRecommendedWithSellTargets();
		assertTrue(RuneFlipPanel.showLowEdgeWarning(res)); // -3 gp/item

		res.priceEdge.profitPerItem = 0L;
		assertTrue(RuneFlipPanel.showLowEdgeWarning(res));

		res.priceEdge.profitPerItem = 5L;
		assertFalse(RuneFlipPanel.showLowEdgeWarning(res));

		// Unknown edge falls back to the whole-flip figure, then to silence.
		res.priceEdge.profitPerItem = null;
		res.expectedProfit = -100L;
		assertTrue(RuneFlipPanel.showLowEdgeWarning(res));
		res.expectedProfit = null;
		assertFalse(RuneFlipPanel.showLowEdgeWarning(res));

		assertEquals("Low or negative edge after tax. Review manually.",
			RuneFlipPanel.LOW_EDGE_WARNING);
	}
}
