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
