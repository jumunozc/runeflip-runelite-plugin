package com.runeflip.companion;

import net.runelite.api.GrandExchangeOfferState;

/**
 * SELL-slot hover profit (v0.8.19) — pure math + copy for the single
 * "PROFIT: …" tooltip line shown while the mouse rests on an ACTIVE sell
 * slot. No I/O, no widget access; the plugin feeds observed offer fields
 * and a session-tracked cost basis and renders whatever comes back.
 *
 * <p>COMPLIANCE: display only. A tooltip line is information, not an
 * action — nothing here (or anywhere) can confirm, cancel, collect or
 * modify an offer. The cost basis comes exclusively from
 * {@link SessionTracker}'s passively observed session buys; when RuneFlip
 * never saw the buy, the line says {@code PROFIT: unknown} — a buy price
 * is never invented.
 *
 * <p>Formula (goal v0.8.19): {@code netSell = sellPrice*qty − tax},
 * {@code costBasis = avgBuyPrice*qty}, {@code profit = netSell − costBasis},
 * projected over the offer's full quantity at the offer's asking price.
 * The tax mirrors RuneFlip's own backend rule (packages/shared
 * {@code GE_TAX_RATE}/{@code GE_TAX_CAP}): 2% of the sell price per item,
 * floored, capped at 5,000,000 gp per item.
 */
final class SellSlotProfit
{
	/** Mirrors packages/shared GE_TAX_RATE (2% since the 2025 GE update). */
	static final double GE_TAX_RATE = 0.02;
	/** Mirrors packages/shared GE_TAX_CAP — per-item tax ceiling, gp. */
	static final long GE_TAX_CAP = 5_000_000L;
	/** Shown when RuneFlip has no tracked cost basis for the item. */
	static final String UNKNOWN_LABEL = "PROFIT: unknown";

	// Tooltip color tags — the panel palette (PROFIT green / RED / MUTED).
	private static final String COLOR_GAIN = "4cba86";
	private static final String COLOR_LOSS = "e06767";
	private static final String COLOR_UNKNOWN = "8b91a0";

	private SellSlotProfit()
	{
	}

	/**
	 * Whether the hovered slot gets the PROFIT line at all: only an ACTIVE
	 * sell offer with a real item. BUY slots (any state), empty slots,
	 * completed/cancelled slots and unknown states show nothing.
	 */
	static boolean appliesTo(GrandExchangeOfferState state, int itemId)
	{
		return state == GrandExchangeOfferState.SELLING && itemId > 0;
	}

	/** Per-item GE tax: {@code min(floor(price × 2%), 5M)} — the same rule
	 *  RuneFlip's backend applies to every fast-flip figure. */
	static long taxPerItem(long sellPrice)
	{
		if (sellPrice <= 0)
		{
			return 0;
		}
		return Math.min((long) Math.floor(sellPrice * GE_TAX_RATE), GE_TAX_CAP);
	}

	/**
	 * Projected profit of the open sell offer in gp, or null when there is
	 * no reliable cost basis (no session-tracked buys of the item remain) —
	 * the caller then shows {@link #UNKNOWN_LABEL} instead of inventing one.
	 */
	static Long profitOf(long sellPrice, long quantity, Long avgBuyPrice)
	{
		if (sellPrice <= 0 || quantity <= 0
			|| avgBuyPrice == null || avgBuyPrice <= 0)
		{
			return null;
		}
		long netSell = (sellPrice - taxPerItem(sellPrice)) * quantity;
		long costBasis = avgBuyPrice * quantity;
		return netSell - costBasis;
	}

	/** "PROFIT: +50K gp" / "PROFIT: -2K gp" / "PROFIT: unknown". */
	static String label(Long profit)
	{
		if (profit == null)
		{
			return UNKNOWN_LABEL;
		}
		String sign = profit < 0 ? "-" : "+";
		return "PROFIT: " + sign + compactGp(Math.abs(profit)) + " gp";
	}

	/** The one tooltip line, color-tagged for RuneLite's tooltip renderer:
	 *  gain green, loss red, unknown muted. */
	static String tooltipLine(Long profit)
	{
		String color = profit == null
			? COLOR_UNKNOWN : (profit < 0 ? COLOR_LOSS : COLOR_GAIN);
		return "<col=" + color + ">" + label(profit) + "</col>";
	}

	/**
	 * Compact gp amount: 950 → "950", 2,000 → "2K", 50,000 → "50K",
	 * 12,500 → "12.5K", 1,250,000 → "1.25M" (one/two decimals, trailing
	 * zeros trimmed). Display rounding only — the sign is handled by
	 * {@link #label}.
	 */
	static String compactGp(long amount)
	{
		if (amount >= 1_000_000L)
		{
			return trim(amount / 1_000_000d, 2) + "M";
		}
		if (amount >= 1_000L)
		{
			return trim(amount / 1_000d, 1) + "K";
		}
		return Long.toString(amount);
	}

	/** Rounds to {@code decimals} places and trims trailing zeros. Locale
	 *  pinned to ROOT so the decimal separator is always a dot. */
	private static String trim(double value, int decimals)
	{
		String out = String.format(
			java.util.Locale.ROOT, "%." + decimals + "f", value);
		while (out.contains(".") && (out.endsWith("0") || out.endsWith(".")))
		{
			out = out.substring(0, out.length() - 1);
		}
		return out;
	}
}
