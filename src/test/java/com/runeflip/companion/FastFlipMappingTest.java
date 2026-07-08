package com.runeflip.companion;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * GET /fast-flip/overview JSON → DTO mapping (v0.7.0). The plugin renders
 * these figures verbatim, so the mapping must survive both a fully populated
 * payload and the contract's nullable fields without inventing values.
 */
public class FastFlipMappingTest
{
	private final Gson gson = new Gson();

	private static final String FULL_ITEM_JSON = "{"
		+ "\"itemId\": 4151,"
		+ "\"itemName\": \"Abyssal whip\","
		+ "\"iconUrl\": \"https://example.test/icon/4151.png\","
		+ "\"capturedAt\": \"2026-07-07T12:00:00.000Z\","
		+ "\"priceCapturedAt\": \"2026-07-07T11:59:00.000Z\","
		+ "\"suggestedBuyPrice\": 1712345,"
		+ "\"suggestedSellPrice\": 1758000,"
		+ "\"tax\": 17580,"
		+ "\"profitPerItem\": 28075,"
		+ "\"roi\": 0.0164,"
		+ "\"suggestedQuantity\": 4,"
		+ "\"requiredCapital\": 6849380,"
		+ "\"estimatedProfit\": 112300,"
		+ "\"fastBuyScore\": 84.5,"
		+ "\"fastSellScore\": 71.0,"
		+ "\"expectedBuyTimeMinutes\": 6.5,"
		+ "\"expectedSellTimeMinutes\": 14.0,"
		+ "\"buySpeed\": \"VERY_FAST\","
		+ "\"sellSpeed\": \"FAST\","
		+ "\"liquidityLevel\": \"HIGH\","
		+ "\"riskLevel\": \"MEDIUM\","
		+ "\"confidence\": 82,"
		+ "\"volatility\": 0.021,"
		+ "\"spreadStability\": 0.87,"
		+ "\"dumpRisk\": 0.12,"
		+ "\"competitionRisk\": 0.33,"
		+ "\"buyPressurePct\": 54.2,"
		+ "\"topFlipScore\": 77.3,"
		+ "\"priceEdge\": {"
		+ "  \"wikiLowPrice\": 1712345,"
		+ "  \"wikiHighPrice\": 1758000,"
		+ "  \"spread\": 45655,"
		+ "  \"safeBuyPrice\": 1712345,"
		+ "  \"safeSellPrice\": 1758000,"
		+ "  \"quickBuyPrice\": 1717345,"
		+ "  \"quickSellPrice\": 1753000,"
		+ "  \"buyEdgeGp\": 5000,"
		+ "  \"sellEdgeGp\": 5000,"
		+ "  \"recommendedBuyPrice\": 1717345,"
		+ "  \"recommendedSellPrice\": 1753000,"
		+ "  \"tax\": 35060,"
		+ "  \"profitPerItem\": 595,"
		+ "  \"roi\": 0.0003,"
		+ "  \"recommendation\": \"QUICK\","
		+ "  \"confidence\": 82,"
		+ "  \"risk\": \"MEDIUM\","
		+ "  \"reasons\": [\"Quick buy +5,000 gp\"],"
		+ "  \"disclaimer\": \"Targets are estimates. Review manually.\""
		+ "},"
		+ "\"reasons\": [\"High volume\", \"Stable spread\"],"
		+ "\"primaryReason\": \"High volume\","
		+ "\"disclaimer\": \"Fast flip estimates are informational. Review manually before trading.\""
		+ "}";

	@Test
	public void fullOverviewPayloadMapsEveryField()
	{
		String json = "{"
			+ "\"generatedAt\": \"2026-07-07T12:00:05.000Z\","
			+ "\"fastBuy\": [" + FULL_ITEM_JSON + "],"
			+ "\"fastSell\": [],"
			+ "\"topFlips\": [" + FULL_ITEM_JSON + "],"
			+ "\"coverage\": {"
			+ "  \"itemsScanned\": 3800,"
			+ "  \"itemsWithData\": 2100,"
			+ "  \"excludedAvoid\": 140,"
			+ "  \"excludedInsufficientData\": 1560"
			+ "},"
			+ "\"disclaimer\": \"Fast flip estimates are informational. Review manually before trading.\""
			+ "}";

		RuneFlipData.FastFlipOverviewResponse overview =
			gson.fromJson(json, RuneFlipData.FastFlipOverviewResponse.class);

		assertEquals("2026-07-07T12:00:05.000Z", overview.generatedAt);
		assertEquals(
			"Fast flip estimates are informational. Review manually before trading.",
			overview.disclaimer);
		assertEquals(1, overview.fastBuy.size());
		assertTrue(overview.fastSell.isEmpty());
		assertEquals(1, overview.topFlips.size());

		assertNotNull(overview.coverage);
		assertEquals(Integer.valueOf(3800), overview.coverage.itemsScanned);
		assertEquals(Integer.valueOf(2100), overview.coverage.itemsWithData);
		assertEquals(Integer.valueOf(140), overview.coverage.excludedAvoid);
		assertEquals(Integer.valueOf(1560), overview.coverage.excludedInsufficientData);

		RuneFlipData.FastFlipItem flip = overview.topFlips.get(0);
		assertEquals(4151, flip.itemId);
		assertEquals("Abyssal whip", flip.itemName);
		assertEquals("https://example.test/icon/4151.png", flip.iconUrl);
		assertEquals("2026-07-07T12:00:00.000Z", flip.capturedAt);
		assertEquals("2026-07-07T11:59:00.000Z", flip.priceCapturedAt);
		assertEquals(Long.valueOf(1_712_345L), flip.suggestedBuyPrice);
		assertEquals(Long.valueOf(1_758_000L), flip.suggestedSellPrice);
		assertEquals(Long.valueOf(17_580L), flip.tax);
		assertEquals(Long.valueOf(28_075L), flip.profitPerItem);
		assertEquals(0.0164, flip.roi, 1e-9);
		assertEquals(Long.valueOf(4L), flip.suggestedQuantity);
		assertEquals(Long.valueOf(6_849_380L), flip.requiredCapital);
		assertEquals(Long.valueOf(112_300L), flip.estimatedProfit);
		assertEquals(84.5, flip.fastBuyScore, 1e-9);
		assertEquals(71.0, flip.fastSellScore, 1e-9);
		assertEquals(6.5, flip.expectedBuyTimeMinutes, 1e-9);
		assertEquals(14.0, flip.expectedSellTimeMinutes, 1e-9);
		assertEquals("VERY_FAST", flip.buySpeed);
		assertEquals("FAST", flip.sellSpeed);
		assertEquals("HIGH", flip.liquidityLevel);
		assertEquals("MEDIUM", flip.riskLevel);
		assertEquals(Integer.valueOf(82), flip.confidence);
		assertEquals(0.021, flip.volatility, 1e-9);
		assertEquals(0.87, flip.spreadStability, 1e-9);
		assertEquals(0.12, flip.dumpRisk, 1e-9);
		assertEquals(0.33, flip.competitionRisk, 1e-9);
		assertEquals(54.2, flip.buyPressurePct, 1e-9);
		assertEquals(77.3, flip.topFlipScore, 1e-9);
		assertEquals(2, flip.reasons.size());
		assertEquals("High volume", flip.reasons.get(0));
		assertEquals("High volume", flip.primaryReason);

		RuneFlipData.PriceEdge edge = flip.priceEdge;
		assertNotNull(edge);
		assertEquals(Long.valueOf(1_712_345L), edge.wikiLowPrice);
		assertEquals(Long.valueOf(1_758_000L), edge.wikiHighPrice);
		assertEquals(Long.valueOf(45_655L), edge.spread);
		assertEquals(Long.valueOf(1_717_345L), edge.quickBuyPrice);
		assertEquals(Long.valueOf(1_753_000L), edge.quickSellPrice);
		assertEquals(Long.valueOf(5_000L), edge.buyEdgeGp);
		assertEquals(Long.valueOf(5_000L), edge.sellEdgeGp);
		assertEquals(Long.valueOf(1_717_345L), edge.recommendedBuyPrice);
		assertEquals(Long.valueOf(1_753_000L), edge.recommendedSellPrice);
		assertEquals(Long.valueOf(35_060L), edge.tax);
		assertEquals(Long.valueOf(595L), edge.profitPerItem);
		assertEquals("QUICK", edge.recommendation);
		assertEquals(Integer.valueOf(82), edge.confidence);
		assertEquals("MEDIUM", edge.risk);
		assertEquals("Targets are estimates. Review manually.", edge.disclaimer);
		assertEquals(
			"Fast flip estimates are informational. Review manually before trading.",
			flip.disclaimer);
	}

	@Test
	public void nullableFieldsStayNullInsteadOfDefaultingToZero()
	{
		String json = "{"
			+ "\"itemId\": 561,"
			+ "\"itemName\": null,"
			+ "\"iconUrl\": null,"
			+ "\"suggestedBuyPrice\": null,"
			+ "\"suggestedSellPrice\": null,"
			+ "\"profitPerItem\": null,"
			+ "\"fastBuyScore\": null,"
			+ "\"fastSellScore\": null,"
			+ "\"topFlipScore\": null,"
			+ "\"buySpeed\": \"UNKNOWN\","
			+ "\"sellSpeed\": \"UNKNOWN\","
			+ "\"riskLevel\": \"LOW\","
			+ "\"confidence\": 10,"
			+ "\"reasons\": [],"
			+ "\"primaryReason\": \"Insufficient history\","
			+ "\"disclaimer\": \"Fast flip estimates are informational. Review manually before trading.\""
			+ "}";

		RuneFlipData.FastFlipItem flip =
			gson.fromJson(json, RuneFlipData.FastFlipItem.class);

		assertEquals(561, flip.itemId);
		assertNull(flip.itemName);
		assertNull(flip.iconUrl);
		assertNull(flip.suggestedBuyPrice);
		assertNull(flip.suggestedSellPrice);
		assertNull(flip.profitPerItem);
		assertNull(flip.fastBuyScore);
		assertNull(flip.fastSellScore);
		assertNull(flip.topFlipScore);
		assertEquals("UNKNOWN", flip.buySpeed);
		assertEquals("LOW", flip.riskLevel);
		assertEquals(Integer.valueOf(10), flip.confidence);
		assertTrue(flip.reasons.isEmpty());
		assertEquals("Insufficient history", flip.primaryReason);
		// Pre-0.7.1 payloads simply omit priceEdge — it must stay null.
		assertNull(flip.priceEdge);
	}

	@Test
	public void emptyTopFlipsAndMissingCoverageParseCleanly()
	{
		String json = "{"
			+ "\"generatedAt\": \"2026-07-07T12:00:05.000Z\","
			+ "\"fastBuy\": [],"
			+ "\"fastSell\": [],"
			+ "\"topFlips\": [],"
			+ "\"disclaimer\": \"Fast flip estimates are informational. Review manually before trading.\""
			+ "}";

		RuneFlipData.FastFlipOverviewResponse overview =
			gson.fromJson(json, RuneFlipData.FastFlipOverviewResponse.class);

		assertTrue(overview.topFlips.isEmpty());
		assertNull(overview.coverage);
	}
}
