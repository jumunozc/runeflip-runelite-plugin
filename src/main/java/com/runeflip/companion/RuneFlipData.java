package com.runeflip.companion;

import java.util.List;

/**
 * Minimal Gson mirrors of the RuneFlip read endpoints the sidebar panel
 * displays. Every figure is computed by the backend — the plugin renders
 * these verbatim and never derives profit, risk or score itself.
 */
public final class RuneFlipData
{
	private RuneFlipData()
	{
	}

	public static class RecommendationsResponse
	{
		public List<Recommendation> items;
		public String capitalSource;
	}

	public static class Recommendation
	{
		public int itemId;
		public String name;
		public int buyPrice;
		public int sellPrice;
		public long estimatedProfit;
		public double roi;
		public int suggestedQuantity;
		public long capitalRequired;
		public String risk;
		public double score;
		public String reason;
	}

	public static class CapitalLatestResponse
	{
		public Capital capital;
	}

	public static class Capital
	{
		public Long inventoryCoins;
		public Long bankCoinsLastSeen;
		public String receivedAt;
	}

	public static class AlertsResponse
	{
		public List<Alert> alerts;
	}

	public static class Alert
	{
		public String id;
		public String itemName;
		public String message;
		public String createdAt;
	}

	/**
	 * Response of GET /fast-flip/overview (v0.7.0). Display data only: every
	 * score, speed, price and profit figure is computed by the backend and
	 * rendered verbatim — the plugin never derives or acts on any of it.
	 */
	public static class FastFlipOverviewResponse
	{
		public String generatedAt;
		public List<FastFlipItem> fastBuy;
		public List<FastFlipItem> fastSell;
		public List<FastFlipItem> topFlips;
		public FastFlipCoverage coverage;
		public String disclaimer;
	}

	public static class FastFlipCoverage
	{
		public Integer itemsScanned;
		public Integer itemsWithData;
		public Integer excludedAvoid;
		public Integer excludedInsufficientData;
	}

	/** One backend-computed fast-flip candidate. Boxed types: any may be null. */
	public static class FastFlipItem
	{
		public int itemId;
		public String itemName;
		public String iconUrl;
		public String capturedAt;
		public String priceCapturedAt;
		public Long suggestedBuyPrice;
		public Long suggestedSellPrice;
		public Long tax;
		public Long profitPerItem;
		public Double roi;
		public Long suggestedQuantity;
		public Long requiredCapital;
		public Long estimatedProfit;
		public Double fastBuyScore;
		public Double fastSellScore;
		public Double expectedBuyTimeMinutes;
		public Double expectedSellTimeMinutes;
		/** "VERY_FAST" | "FAST" | "MODERATE" | "SLOW" | "UNKNOWN". */
		public String buySpeed;
		public String sellSpeed;
		/** "HIGH" | "MEDIUM" | "LOW" | "UNKNOWN". */
		public String liquidityLevel;
		/** "LOW" | "MEDIUM" | "HIGH" | "AVOID". */
		public String riskLevel;
		public Integer confidence;
		public Double volatility;
		public Double spreadStability;
		public Double dumpRisk;
		public Double competitionRisk;
		public Double buyPressurePct;
		public Double topFlipScore;
		/** Price Edge targets (v0.7.1); null on pre-0.7.1 backends. */
		public PriceEdge priceEdge;
		public List<String> reasons;
		public String primaryReason;
		public String disclaimer;
	}

	/**
	 * Price Edge Model targets (v0.7.1). Backend-computed manual target
	 * prices: safe = at the wiki legs, quick = inside the spread for a
	 * faster fill, never past the backend's profit-after-tax floors. The
	 * plugin renders these verbatim and never acts on them.
	 */
	public static class PriceEdge
	{
		public Long wikiLowPrice;
		public Long wikiHighPrice;
		public Long spread;
		public Long safeBuyPrice;
		public Long safeSellPrice;
		public Long quickBuyPrice;
		public Long quickSellPrice;
		public Long buyEdgeGp;
		public Long sellEdgeGp;
		public Long recommendedBuyPrice;
		public Long recommendedSellPrice;
		public Long tax;
		public Long profitPerItem;
		public Double roi;
		/** "QUICK" | "SAFE" | "NOT_RECOMMENDED". */
		public String recommendation;
		public Integer confidence;
		/** "LOW" | "MEDIUM" | "HIGH" | "AVOID". */
		public String risk;
		public List<String> reasons;
		public String disclaimer;
	}

	/** Body of POST /pairing/complete — just the user-pasted code. */
	public static class PairingRequest
	{
		public final String code;

		public PairingRequest(String code)
		{
			this.code = code;
		}
	}

	/**
	 * Response of POST /pairing/complete (v0.6.3). The token appears ONLY
	 * here, exactly once — it is stored in the plugin config (secret) and
	 * must never be logged or displayed.
	 */
	public static class PairingResponse
	{
		public String clientId;
		public String token;
	}
}
