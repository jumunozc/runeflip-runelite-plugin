package com.runeflip.companion;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * GET /fast-flip/item/:itemId JSON → DTO mapping and the panel's selected-item
 * text helpers (v0.8.4, comparison fixed in v0.8.5). The plugin renders every
 * figure and comparison string verbatim, so the mapping must survive both a
 * recommended item and the honest "no target yet" degradation, and the buy/sell
 * lines must anchor to the wiki low / high legs correctly.
 */
public class ItemContextMappingTest
{
	private final Gson gson = new Gson();

	// Small prices on purpose: the panel formats gp via quantityToStackSize,
	// exact only below 10,000. Market low 119 (insta-sell) / high 130 (insta-buy);
	// quick pair buys at 122 (+3 above low) and sells at 127 (−3 below high).
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
		+ "  \"wikiLow\": 119, \"wikiHigh\": 130,"
		+ "  \"targetBuy\": 122, \"targetSell\": 127,"
		+ "  \"buyDeltaVsWikiLow\": 3, \"sellDeltaVsWikiHigh\": -3,"
		+ "  \"extraEdgePerItem\": 3, \"potentialExtraProfit\": 300,"
		+ "  \"buyMessage\": \"Target is 3 gp above Wiki low for faster fill\","
		+ "  \"sellMessage\": \"Target is 3 gp below Wiki high for faster fill\","
		+ "  \"guidance\": \"Use lower buy for margin, higher sell if you can wait. Review manually.\","
		+ "  \"wikiBuyPrice\": 130, \"wikiSellPrice\": 119,"
		+ "  \"recommendedBuyPrice\": 122, \"recommendedSellPrice\": 127,"
		+ "  \"buyDelta\": 3, \"sellDelta\": -3"
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
		// Buy anchored to the wiki LOW leg, sell to the wiki HIGH leg (v0.8.5).
		assertEquals(Long.valueOf(119L), tc.wikiLow);
		assertEquals(Long.valueOf(130L), tc.wikiHigh);
		assertEquals(Long.valueOf(122L), tc.targetBuy);
		assertEquals(Long.valueOf(127L), tc.targetSell);
		assertEquals(Long.valueOf(3L), tc.buyDeltaVsWikiLow);
		assertEquals(Long.valueOf(-3L), tc.sellDeltaVsWikiHigh);
		assertEquals(Long.valueOf(3L), tc.extraEdgePerItem);
		assertEquals(Long.valueOf(300L), tc.potentialExtraProfit);
	}

	@Test
	public void panelRendersTheWikiVsRuneFlipComparisonAnchoredCorrectly()
	{
		RuneFlipData.FastFlipItemContextResponse res =
			gson.fromJson(RECOMMENDED_JSON, RuneFlipData.FastFlipItemContextResponse.class);

		assertNull(RuneFlipPanel.noRuneFlipTargetLine(res));

		// Buy line references the wiki LOW leg, sell line the wiki HIGH leg.
		String buy = RuneFlipPanel.contextComparisonBuyLine(res.targetComparison);
		assertTrue(buy.contains("Target is 3 gp above Wiki low for faster fill"));
		String sell = RuneFlipPanel.contextComparisonSellLine(res.targetComparison);
		assertTrue(sell.contains("Target is 3 gp below Wiki high for faster fill"));

		// Compact Wiki legs + RuneFlip targets lines (v0.8.5).
		String wiki = RuneFlipPanel.wikiLegsLine(res.targetComparison);
		assertTrue(wiki.contains("L 119 gp"));
		assertTrue(wiki.contains("H 130 gp"));
		String targets = RuneFlipPanel.runeFlipTargetsLine(res.targetComparison);
		assertTrue(targets.contains("buy 122 gp"));
		assertTrue(targets.contains("sell 127 gp"));

		String extra = RuneFlipPanel.contextExtraProfitLine(res.targetComparison);
		assertTrue(extra.contains("+3 gp/item"));
		assertTrue(extra.contains("for qty"));

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
			+ "  \"wikiLow\": null, \"wikiHigh\": null,"
			+ "  \"targetBuy\": null, \"targetSell\": null,"
			+ "  \"buyDeltaVsWikiLow\": null, \"sellDeltaVsWikiHigh\": null,"
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
		// No qty-scaled edge and no target lines when there is no target.
		assertNull(RuneFlipPanel.contextExtraProfitLine(res.targetComparison));
		assertNull(RuneFlipPanel.runeFlipTargetsLine(res.targetComparison));
		assertEquals("—", RuneFlipPanel.durationLabel(res.expectedDurationMinutes));
	}

	@Test
	public void preV084PayloadLeavesComparisonHelpersNull()
	{
		assertNull(RuneFlipPanel.wikiLegsLine(null));
		assertNull(RuneFlipPanel.runeFlipTargetsLine(null));
		assertNull(RuneFlipPanel.contextComparisonBuyLine(null));
		assertNull(RuneFlipPanel.contextComparisonSellLine(null));
		assertNull(RuneFlipPanel.contextExtraProfitLine(null));
		assertNull(RuneFlipPanel.safeQuickLine(null));
		assertNull(RuneFlipPanel.noRuneFlipTargetLine(null));
	}
}
