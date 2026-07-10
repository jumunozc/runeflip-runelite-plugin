package com.runeflip.companion;

import java.util.HashMap;
import java.util.Map;

/**
 * Session KPIs for the SessionPanel (v0.8.7 design) — answers "how did this
 * session go?" from passively observed GE offer completions. Pure accounting,
 * no RuneLite types, no I/O: the plugin feeds it slot phases translated from
 * {@code GrandExchangeOfferChanged} events and reads immutable stats back.
 *
 * <p>COMPLIANCE: observations in, numbers out. Nothing here (or anywhere) can
 * place, modify or collect an offer — this only summarizes what the player
 * already did manually. The client-side arithmetic is over the player's OWN
 * observed activity (never market advice, which stays backend-computed).
 *
 * <p>Login replay safety: RuneLite re-fires the current state of all 8 slots
 * at login. A trade is only recorded on a transition observed DURING the
 * session — the slot must have been seen ACTIVE first; a slot that first
 * appears already completed is baseline, not session activity.
 *
 * <p>Accounting: weighted-average cost per item. A completed sell is matched
 * against the session's buys of the same item; only the matched part has a
 * cost basis and contributes to realized profit + ROI. Unmatched proceeds
 * (item bought before the session) count as a flip but never invent a profit.
 * Values use the offer's observed coin amounts, so a sell's value is what the
 * player actually received.
 */
public class SessionTracker
{
	/** Slot phase as the plugin translates GE offer states (pure input). */
	public enum SlotPhase
	{
		EMPTY,
		ACTIVE_BUY,
		ACTIVE_SELL,
		DONE_BUY,
		DONE_SELL
	}

	/** Immutable KPI snapshot for the SessionPanel. */
	public static class Stats
	{
		/** Realized profit (matched sells − their cost basis), gp. */
		public final long profit;
		/** Completed sell offers observed this session. */
		public final int flips;
		/** Realized profit / matched cost basis; null until a matched sell. */
		public final Double roi;
		/** Coins currently sitting in session buys not yet sold, gp. */
		public final long openBuysCost;
		/** Milliseconds since session start / last reset. */
		public final long sessionMillis;
		/** Realized profit scaled to one hour; null before any activity. */
		public final Double profitPerHour;
		/** True once any completion was recorded (drives panel visibility). */
		public final boolean hasActivity;

		Stats(
			long profit,
			int flips,
			Double roi,
			long openBuysCost,
			long sessionMillis,
			Double profitPerHour,
			boolean hasActivity)
		{
			this.profit = profit;
			this.flips = flips;
			this.roi = roi;
			this.openBuysCost = openBuysCost;
			this.sessionMillis = sessionMillis;
			this.profitPerHour = profitPerHour;
			this.hasActivity = hasActivity;
		}
	}

	private static class Position
	{
		long quantity;
		long cost;
	}

	private final Map<Integer, SlotPhase> lastPhase = new HashMap<>();
	private final Map<Integer, Position> positions = new HashMap<>();
	private long startMs;
	private long realized;
	private long matchedCost;
	private long openBuysCost;
	private int flips;
	private boolean hasActivity;

	public SessionTracker(long nowMs)
	{
		this.startMs = nowMs;
	}

	/**
	 * Feeds one observed slot state. Records a trade only on an ACTIVE_* →
	 * DONE_* transition seen this session with a positive filled quantity —
	 * the first observation of a slot only sets its baseline.
	 */
	public synchronized void observe(
		int slot, SlotPhase phase, int itemId, long quantityFilled, long value)
	{
		SlotPhase previous = lastPhase.put(slot, phase);
		if (previous == null || quantityFilled <= 0 || itemId <= 0)
		{
			return;
		}
		if (phase == SlotPhase.DONE_BUY && previous == SlotPhase.ACTIVE_BUY)
		{
			recordBuy(itemId, quantityFilled, value);
		}
		else if (phase == SlotPhase.DONE_SELL && previous == SlotPhase.ACTIVE_SELL)
		{
			recordSell(itemId, quantityFilled, value);
		}
	}

	private void recordBuy(int itemId, long quantity, long cost)
	{
		Position position = positions.computeIfAbsent(itemId, id -> new Position());
		position.quantity += quantity;
		position.cost += Math.max(0, cost);
		openBuysCost += Math.max(0, cost);
		hasActivity = true;
	}

	private void recordSell(int itemId, long quantity, long proceeds)
	{
		flips++;
		hasActivity = true;
		Position position = positions.get(itemId);
		if (position == null || position.quantity <= 0)
		{
			// No session cost basis (bought before the session): a flip, but
			// never an invented profit.
			return;
		}
		long matched = Math.min(quantity, position.quantity);
		// Weighted-average cost of the matched units.
		long basis = Math.round(
			(double) position.cost * matched / position.quantity);
		long matchedProceeds = Math.round(
			(double) Math.max(0, proceeds) * matched / quantity);
		realized += matchedProceeds - basis;
		matchedCost += basis;
		openBuysCost -= basis;
		position.quantity -= matched;
		position.cost -= basis;
		if (position.quantity <= 0)
		{
			positions.remove(itemId);
		}
	}

	/** Clears all session state and restarts the clock. */
	public synchronized void reset(long nowMs)
	{
		lastPhase.clear();
		positions.clear();
		startMs = nowMs;
		realized = 0;
		matchedCost = 0;
		openBuysCost = 0;
		flips = 0;
		hasActivity = false;
	}

	/** Immutable KPI snapshot at {@code nowMs}. */
	public synchronized Stats stats(long nowMs)
	{
		long elapsed = Math.max(0, nowMs - startMs);
		Double roi = matchedCost > 0 ? (double) realized / matchedCost : null;
		Double perHour = hasActivity && elapsed > 0
			? realized * 3_600_000d / elapsed
			: null;
		return new Stats(
			realized, flips, roi, Math.max(0, openBuysCost), elapsed, perHour,
			hasActivity);
	}

	/** "02:19" style elapsed label (hours:minutes; formatting only). */
	static String sessionTimeLabel(long millis)
	{
		long minutes = Math.max(0, millis) / 60_000;
		return String.format("%02d:%02d", minutes / 60, minutes % 60);
	}
}
