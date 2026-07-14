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
		/** Insights for this client's existing offers (v0.8.2); null on
		 *  pre-0.8.2 backends, empty without a paired client or active offers. */
		public List<GeSlotActionInsight> slotInsights;
		/** Applied strategy echo (v0.8.0); null on pre-0.8.0 backends. */
		public FastFlipStrategy strategy;
		/** Honest plan-truncation metadata (v0.9.2); null on older backends
		 *  and on unshaped responses. */
		public PlanAccessMeta access;
		public String disclaimer;
	}

	/**
	 * Plan & entitlements of this install (v0.9.2) — GET /entitlements. The
	 * plugin renders/gates on it; the SERVER remains the real gate (a FREE
	 * response already excluded Members items and premium targets). All boxed
	 * types: any field may be null on older backends.
	 */
	public static class EntitlementsResponse
	{
		/** "FREE" | "PRO". */
		public String planCode;
		/** "ACTIVE" | "EXPIRED" | "REVOKED". */
		public String planStatus;
		public List<String> entitlements;
		/** ISO end of the current grant; null = open-ended / FREE. */
		public String expiresAt;
		/** Server clock — expiry is compared against THIS, never local time. */
		public String serverTime;
		/** Mobile-only hint; the plugin never shows ads (Plugin Hub rule). */
		public Boolean adsEnabled;
		/** "F2P_ONLY" | "ALL". */
		public String membershipItemAccess;
	}

	/**
	 * Honest-truncation block plan-limited responses carry (v0.9.2). Display
	 * copy input only.
	 */
	public static class PlanAccessMeta
	{
		/** "FREE" | "PRO". */
		public String plan;
		/** "F2P_ONLY" | "ALL". */
		public String membershipItemAccess;
		public Boolean isLimited;
		public String limitReason;
		public String upgradeEntitlement;
		public Long lockedCount;
		public Boolean strategyLocked;
	}

	public static class FastFlipCoverage
	{
		public Integer itemsScanned;
		public Integer itemsWithData;
		public Integer excludedAvoid;
		public Integer excludedInsufficientData;
		/** Rankable items excluded by the strategy (v0.8.0); may be null. */
		public Integer excludedByStrategy;
	}

	/**
	 * Strategy Engine echo (v0.8.0). Pure display metadata about how the
	 * backend ranked/filtered this response — the plugin renders the
	 * backend-built description verbatim and never derives or acts on it.
	 */
	public static class FastFlipStrategy
	{
		public Integer timeframeMinutes;
		/** "LOW" | "MEDIUM" | "HIGH" (maximum accepted risk). */
		public String riskLevel;
		public Long minPredictedProfit;
		public Double minRoi;
		public Boolean isDefault;
		/** Backend-built summary, e.g. "8h timeframe · risk up to HIGH". */
		public String description;
	}

	/**
	 * GET /strategy/preferences response (v0.8.1): the strategy this install
	 * saved from web/mobile, scoped to the anonymous clientId. The plugin only
	 * forwards it as query params on the overview fetch so the Fast Flip card
	 * reflects the saved strategy — display preferences, never an action.
	 */
	public static class StrategyPreferencesResponse
	{
		public StrategyPreferences preferences;
		/** False => nothing saved yet; the plugin keeps the default strategy. */
		public Boolean saved;
		public String updatedAt;
	}

	public static class StrategyPreferences
	{
		public Integer timeframeMinutes;
		/** "LOW" | "MEDIUM" | "HIGH" (maximum accepted risk). */
		public String riskLevel;
		public Long minPredictedProfit;
		public Double minRoi;
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
		/** Graded auxiliary risks — "LOW" | "MEDIUM" | "HIGH" | "UNKNOWN".
		 *  The backend has ALWAYS sent these as grade strings
		 *  (FastFlipAuxRisk); typing them Double (v0.7.0) made Gson throw
		 *  NumberFormatException on every real overview payload, which the
		 *  plugin then displayed as "Fast flip · 0" (hotfix v0.8.6). */
		public String dumpRisk;
		public String competitionRisk;
		public Double buyPressurePct;
		public Double topFlipScore;
		/** Members flag (v0.9.2); null on older backends / unknown items. */
		public Boolean members;
		/** Price Edge targets (v0.7.1); null on pre-0.7.1 backends. v0.9.2:
		 *  also ABSENT (null) on a FREE plan — targets are premium. */
		public PriceEdge priceEdge;
		/** Recommended action (v0.8.2); null on pre-0.8.2 backends. */
		public RecommendedAction action;
		public List<String> reasons;
		public String primaryReason;
		public String disclaimer;
	}

	/**
	 * Recommendation Actions v0.8.2 — a backend-computed suggestion of WHAT to
	 * review for one item. Compliance rule: RuneFlip may assist input, but
	 * never execute intent. The plugin renders the label/reason verbatim and
	 * never turns it into a game action (reviewOnly is always true).
	 */
	public static class RecommendedAction
	{
		/** BUY_NEW | SELL_EXISTING | HOLD | MODIFY_BUY | MODIFY_SELL |
		 *  ABORT_BUY | ABORT_SELL | AVOID. */
		public String actionType;
		public String actionLabel;
		public String actionReason;
		public Boolean reviewOnly;
		/**
		 * Assisted Offer Setup metadata (v0.8.3), null on pre-0.8.3 backends
		 * and for actions with nothing to set up (HOLD / ABORT_* / AVOID).
		 * These are values the user MAY copy to the clipboard after an
		 * explicit click — never filled, submitted or acted on.
		 */
		public Long targetPrice;
		public Long targetQuantity;
		public String targetSource;
	}

	/**
	 * Read-only insight for one EXISTING offer of the paired client (v0.8.2).
	 * Observations in, suggestions out — never a command. Only present when
	 * the plugin is paired and the backend has fast-flip data for the offer.
	 */
	public static class GeSlotActionInsight
	{
		public int slot;
		public int itemId;
		public String itemName;
		public String iconUrl;
		/** "BUY" | "SELL". */
		public String offerType;
		public Long offerPrice;
		public Long quantity;
		public Long quantityFilled;
		public RecommendedAction action;
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
		/** v0.9.2: TRUE when the plan reduced this block to the wiki legs —
		 *  every target is null and `recommendation` is NOT meaningful; the
		 *  panel must render the locked state instead. Null on full blocks. */
		public Boolean locked;
	}

	/**
	 * Response of GET /fast-flip/item/:itemId (v0.8.4) — the full RuneFlip
	 * context for ONE item, used by the contextual side panel when the user has
	 * an item open in the GE Buy/Sell setup. Display data only: every figure and
	 * every comparison string is computed by the backend and rendered verbatim —
	 * the plugin never derives or acts on any of it. `recommended` is false (with
	 * a clear `notRecommendedReason`) for AVOID / not-recommended / no-data items;
	 * the panel then shows "No RuneFlip target yet".
	 */
	public static class FastFlipItemContextResponse
	{
		public String generatedAt;
		public int itemId;
		public String itemName;
		public String iconUrl;
		public String priceCapturedAt;
		/** Echo of the requested offer side ("BUY"/"SELL", v0.8.12); null on
		 *  pre-v0.8.12 backends or when the request sent no side. */
		public String side;
		/** v0.9.2: TRUE = identity-only locked card (FREE + Members item);
		 *  itemName travels, every analytic field is null. Null otherwise. */
		public Boolean locked;
		/** Members flag (v0.9.2); null on older backends / unknown items. */
		public Boolean members;
		public Boolean recommended;
		public String notRecommendedReason;
		public PriceEdge priceEdge;
		public RecommendedAction action;
		public TargetComparison targetComparison;
		public Long expectedProfit;
		public Double expectedDurationMinutes;
		public Double roi;
		public Long cost;
		public Long suggestedQuantity;
		/** "LOW" | "MEDIUM" | "HIGH" | "AVOID". */
		public String riskLevel;
		public Integer confidence;
		public String buySpeed;
		public String sellSpeed;
		/** Applied strategy echo (v0.8.0); null on pre-0.8.0 backends. */
		public FastFlipStrategy strategy;
		/** Honest plan-truncation metadata (v0.9.2); null when unshaped. */
		public PlanAccessMeta access;
		public String disclaimer;
	}

	/**
	 * Wiki-vs-RuneFlip target comparison (v0.8.4). Backend arithmetic over the
	 * Price Edge block: "Wiki buy" is the instant-buy leg (buying now), "Wiki
	 * sell" is the instant-sell leg (selling now); a positive buyDelta means the
	 * RuneFlip buy target is that many gp cheaper, a positive sellDelta means the
	 * sell target is that many gp higher. The plugin renders the ready-made
	 * buyMessage / sellMessage / guidance strings verbatim — it never computes a
	 * comparison itself and never acts on it.
	 */
	public static class TargetComparison
	{
		// Correctly-anchored fields (v0.8.5): a flip buys near the wiki LOW leg
		// (recent insta-sell) and sells near the wiki HIGH leg (recent insta-buy).
		public Long wikiLow;
		public Long wikiHigh;
		public Long targetBuy;
		public Long targetSell;
		public Long buyDeltaVsWikiLow;
		public Long sellDeltaVsWikiHigh;
		public Long extraEdgePerItem;
		public Long potentialExtraProfit;
		public String buyMessage;
		public String sellMessage;
		public String guidance;

		// Deprecated v0.8.4 aliases (still sent by the backend for pre-v0.8.5
		// clients; not used by the panel anymore).
		public Long wikiBuyPrice;
		public Long wikiSellPrice;
		public Long recommendedBuyPrice;
		public Long recommendedSellPrice;
		public Long buyDelta;
		public Long sellDelta;
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

	// ── Account device pairing (v0.9.1) ─────────────────────────────────────

	/** Body of POST /pairing/start. */
	public static class DevicePairingStartRequest
	{
		public final String deviceName;

		public DevicePairingStartRequest(String deviceName)
		{
			this.deviceName = deviceName;
		}
	}

	/**
	 * Response of POST /pairing/start. deviceCode is the polling secret —
	 * kept in memory only for the pairing attempt, never logged or shown.
	 */
	public static class DevicePairingStartResponse
	{
		public String deviceCode;
		public String userCode;
		public String verificationUri;
		public String verificationUriComplete;
		public String expiresAt;
		public Integer pollIntervalSeconds;
	}

	/** Body of POST /pairing/token (the poll). */
	public static class DevicePollRequest
	{
		public final String deviceCode;

		public DevicePollRequest(String deviceCode)
		{
			this.deviceCode = deviceCode;
		}
	}

	/**
	 * Response of POST /pairing/token. `credential` appears ONLY on the
	 * single successful 'approved' poll — stored as secret config, never
	 * displayed, never logged.
	 */
	public static class DevicePollResponse
	{
		public String status;
		public String credential;
		public DevicePollDevice device;
	}

	public static class DevicePollDevice
	{
		public String id;
		public String name;
		public String platform;
		public String clientId;
	}
}
