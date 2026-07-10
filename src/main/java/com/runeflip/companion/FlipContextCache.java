package com.runeflip.companion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Active-flip context retention (v0.8.12). Keeps the last fetched RuneFlip
 * context per itemId for the length of a flip (default 30 minutes), so the
 * panel can keep helping CLOSE a flip: the user buys an item (buy context
 * shown), the offer fills, they open the SELL setup for the same item — and
 * the panel renders the retained targets/qty/profit/tax/ROI immediately
 * while the sell-focused refresh runs in the background, instead of dropping
 * to "No RuneFlip target yet".
 *
 * <p>Pure local bookkeeping over responses the backend already sent —
 * display data only, nothing here reads or touches the game.
 */
final class FlipContextCache
{
	/** One flip's worth of retention: long enough to buy, wait for the fill
	 *  and come back to sell; short enough that stale targets die out. */
	static final long DEFAULT_TTL_MS = 30 * 60_000;

	private final long ttlMs;
	private final Map<Integer, Entry> byItem = new ConcurrentHashMap<>();

	FlipContextCache(long ttlMs)
	{
		this.ttlMs = ttlMs;
	}

	/** Remembers the freshest context for one item (null values ignored). */
	void put(int itemId, RuneFlipData.FastFlipItemContextResponse context, long nowMs)
	{
		if (itemId <= 0 || context == null)
		{
			return;
		}
		byItem.put(itemId, new Entry(context, nowMs));
	}

	/** The retained context for one item, or null when absent/expired. */
	RuneFlipData.FastFlipItemContextResponse get(int itemId, long nowMs)
	{
		Entry entry = byItem.get(itemId);
		if (entry == null)
		{
			return null;
		}
		if (nowMs - entry.storedAtMs > ttlMs)
		{
			byItem.remove(itemId);
			return null;
		}
		return entry.context;
	}

	private static final class Entry
	{
		final RuneFlipData.FastFlipItemContextResponse context;
		final long storedAtMs;

		Entry(RuneFlipData.FastFlipItemContextResponse context, long storedAtMs)
		{
			this.context = context;
			this.storedAtMs = storedAtMs;
		}
	}
}
