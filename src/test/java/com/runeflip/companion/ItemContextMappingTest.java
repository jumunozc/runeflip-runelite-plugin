package com.runeflip.companion;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * GET /fast-flip/item/:itemId JSON → DTO mapping and the panel's selected-item
 * text helpers (v0.8.4). The plugin renders every figure and every comparison
 * string verbatim, so the mapping must survive both a recommended item and the
 * honest "no target yet" degradation without inventing anything.
 */
public class ItemContextMappingTest
{
	private final Gson gson = new Gson();

	// Small prices on purpose: the panel formats gp via quantityToStackSize,
	// which is exact only below 10,000 (10,300 would render "10K"). The same
	// convention the existing panel-text tests use.
	private static final String RECOMMENDED_JSON = "{"
		+ "\"generatedAt\": \"2026-07-09T12:00:00.000Z\","
		+ "\"itemId\": 561,"
		+ "\"itemName\": \"Nature rune\","
		+ "\"iconUrl\": \"https://example.test/icon/561.png\","
		+ "\"priceCapturedAt\": \"2026-07-09T11:59:00.000Z\","
		+ "\"recommended\": true,"
		+ "\"notRecommendedReason\": null,"
		+ "\"priceEdge\": {"
		+ "  \"wikiLowPrice\": 119, \"wikiHighPrice\": 130, \"spread\": 11,"
		+ "  \"safeBuyPrice\": 119, \"safeSellPrice\": 130,"
		+ "  \"quickBuyPrice\": 122, \"quickSellPrice\": 127,"
		+ "  \"buyEdgeGp\": 3, \"sellEdgeGp\": 3,"
		+ "  \"recommendedBuyPrice\": 122, \"recommendedSellPrice\": 127,"
		+ "  \"tax\": 2, \"profitPerItem\": 3, \"roi\": 0.0245,"
		+ "  \"recommendation\": \"QUICK\", \"confidence\": 90, \"risk\": \"LOW\","
		+ "  \"reasons\": [\"Quick buy +3 gp\"], \"disclaimer\": \"Targets are estimates. Review manually.\""
		+ "},"
		+ "\"action\": {"
		+ "  \"actionType\": \"BUY_NEW\", \"actionLabel\": \"Buy new\","
		+ "  \"actionReason\": \"Review a new buy near 122 gp.\", \"reviewOnly\": true,"
		+ "  \"targetPrice\": 122, \"targetQuantity\": 100, \"targetSource\": \"Price Edge recommended buy\""
		+ "},"
		+ "\"targetComparison\": {"
		+ "  \"wikiBuyPrice\": 130, \"wikiSellPrice\": 119,"
		+ "  \"recommendedBuyPrice\": 122, \"recommendedSellPrice\": 127,"
		+ "  \"buyDelta\": 8, \"sellDelta\": 8,"
		+ "  \"extraEdgePerItem\": 16, \"potentialExtraProfit\": 1600,"
		+ "  \"buyMessage\": \"Try buying 8 gp cheaper than Wiki\","
		+ "  \"sellMessage\": \"Try selling 8 gp higher than Wiki\","
		+ "  \"guidance\": \"Use lower buy for margin, higher sell if you can wait. Review manually.\""
		+ "},"
		+ "\"expectedProfit\": 300, \"expectedDurationMinutes\": 18.0, \"roi\": 0.0245,"
		+ "\"cost\": 12200, \"suggestedQuantity\": 100,"
		+ "\"riskLevel\": \"LOW\", \"confidence\": 90,"
		+ "\"buySpeed\": \"FAST\", \"sellSpeed\": \"MODERATE\","
		+ "\"strategy\": {\"timeframeMinutes\": 480, \"riskLevel\": \"HIGH\", \"isDefault\": true, \"description\": \"8h timeframe · risk up to HIGH\"},"
		+ "\"disclaimer\": \"Wiki vs RuneFlip targets are informational. Decide and act manually.\""
		+ "}";

	@Test
	public void recommendedItemMapsEveryFieldAndComparison()
	{
		RuneFlipData.FastFlipItemContextResponse res =
			gson.fromJson(RECOMMENDED_JSON, RuneFlipData.FastFlipItemContextResponse.class);

		assertEquals(561, res.itemId);
		assertEquals("Nature rune", res.itemName);
		assertEquals(Boolean.TRUE, res.recommended);
		assertNull(res.notRecommendedReason);
		assertEquals(Long.valueOf(300L), res.expectedProfit);
		assertEquals(18.0, res.expectedDurationMinutes, 1e-9);
		assertEquals(Long.valueOf(12_200L), res.cost);
		assertEquals(Long.valueOf(100L), res.suggestedQuantity);

		RuneFlipData.TargetComparison tc = res.targetComparison;
		assertNotNull(tc);
		// Wiki buy = instant-buy (high leg); wiki sell = instant-sell (low leg).
		assertEquals(Long.valueOf(130L), tc.wikiBuyPrice);
		assertEquals(Long.valueOf(119L), tc.wikiSellPrice);
		assertEquals(Long.valueOf(8L), tc.buyDelta);
		assertEquals(Long.valueOf(8L), tc.sellDelta);
		assertEquals(Long.valueOf(16L), tc.extraEdgePerItem);
		assertEquals(Long.valueOf(1_600L), tc.potentialExtraProfit);
	}

	@Test
	public void panelRendersTheWikiVsRuneFlipComparisonVerbatim()
	{
		RuneFlipData.FastFlipItemContextResponse res =
			gson.fromJson(RECOMMENDED_JSON, RuneFlipData.FastFlipItemContextResponse.class);

		// Not-recommended line is absent for a recommended item.
		assertNull(RuneFlipPanel.noRuneFlipTargetLine(res));

		String buy = RuneFlipPanel.contextComparisonBuyLine(res.targetComparison);
		assertTrue(buy.contains("Try buying 8 gp cheaper than Wiki"));
		String sell = RuneFlipPanel.contextComparisonSellLine(res.targetComparison);
		assertTrue(sell.contains("Try selling 8 gp higher than Wiki"));

		String wikiVsTarget = RuneFlipPanel.wikiTargetPricesLine(res.targetComparison);
		assertTrue(wikiVsTarget.contains("130 gp")); // wiki instant-buy (high)
		assertTrue(wikiVsTarget.contains("119 gp")); // wiki instant-sell (low)
		assertTrue(wikiVsTarget.contains("target buy 122 gp"));
		assertTrue(wikiVsTarget.contains("sell 127 gp"));

		String extra = RuneFlipPanel.contextExtraProfitLine(res.targetComparison);
		assertTrue(extra.contains("+16 gp/item"));
		assertTrue(extra.contains("for qty"));

		String guidance = RuneFlipPanel.contextGuidanceLine(res.targetComparison);
		assertTrue(guidance.contains("Use lower buy for margin"));

		assertTrue(RuneFlipPanel.safeQuickLine(res.priceEdge).contains("Safe"));
		assertEquals("~18m", RuneFlipPanel.durationLabel(res.expectedDurationMinutes));
	}

	@Test
	public void notRecommendedItemDegradesToNoTargetYet()
	{
		String json = "{"
			+ "\"itemId\": 561, \"itemName\": \"Nature rune\","
			+ "\"recommended\": false,"
			+ "\"notRecommendedReason\": \"No RuneFlip target yet — this item has no recent price or feature data.\","
			+ "\"priceEdge\": {\"wikiLowPrice\": null, \"wikiHighPrice\": null, \"recommendation\": \"NOT_RECOMMENDED\"},"
			+ "\"targetComparison\": {"
			+ "  \"wikiBuyPrice\": null, \"wikiSellPrice\": null,"
			+ "  \"recommendedBuyPrice\": null, \"recommendedSellPrice\": null,"
			+ "  \"buyDelta\": null, \"sellDelta\": null,"
			+ "  \"buyMessage\": \"No RuneFlip buy target yet\","
			+ "  \"sellMessage\": \"No RuneFlip sell target yet\","
			+ "  \"guidance\": \"Use lower buy for margin, higher sell if you can wait. Review manually.\""
			+ "},"
			+ "\"riskLevel\": \"AVOID\", \"confidence\": 0,"
			+ "\"disclaimer\": \"Wiki vs RuneFlip targets are informational. Decide and act manually.\""
			+ "}";

		RuneFlipData.FastFlipItemContextResponse res =
			gson.fromJson(json, RuneFlipData.FastFlipItemContextResponse.class);

		String noTarget = RuneFlipPanel.noRuneFlipTargetLine(res);
		assertNotNull(noTarget);
		assertTrue(noTarget.contains("No RuneFlip target yet"));
		assertTrue(noTarget.contains("no recent price"));
		// No qty-scaled edge when there is no target.
		assertNull(RuneFlipPanel.contextExtraProfitLine(res.targetComparison));
		assertEquals("—", RuneFlipPanel.durationLabel(res.expectedDurationMinutes));
	}

	@Test
	public void preV084PayloadLeavesComparisonHelpersNull()
	{
		// A backend without the comparison block: every helper degrades to null.
		assertNull(RuneFlipPanel.wikiTargetPricesLine(null));
		assertNull(RuneFlipPanel.contextComparisonBuyLine(null));
		assertNull(RuneFlipPanel.contextComparisonSellLine(null));
		assertNull(RuneFlipPanel.contextExtraProfitLine(null));
		assertNull(RuneFlipPanel.contextGuidanceLine(null));
		assertNull(RuneFlipPanel.safeQuickLine(null));
		assertNull(RuneFlipPanel.noRuneFlipTargetLine(null));
	}
}
