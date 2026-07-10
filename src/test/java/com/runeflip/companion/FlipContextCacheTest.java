package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Active-flip context retention (v0.8.12). The cache exists for exactly one
 * real-play flow: buy an item (buy context fetched and shown), the offer
 * fills, open the SELL setup of the same item — the retained context must
 * still be there so the panel can render the sell targets immediately
 * instead of "No RuneFlip target yet".
 */
public class FlipContextCacheTest
{
	private static RuneFlipData.FastFlipItemContextResponse context(int itemId)
	{
		RuneFlipData.FastFlipItemContextResponse ctx =
			new RuneFlipData.FastFlipItemContextResponse();
		ctx.itemId = itemId;
		return ctx;
	}

	@Test
	public void retainsTheContextAcrossABuyToSellSwitch()
	{
		FlipContextCache cache = new FlipContextCache(FlipContextCache.DEFAULT_TTL_MS);
		RuneFlipData.FastFlipItemContextResponse buyContext = context(560);

		long boughtAt = 1_000_000;
		cache.put(560, buyContext, boughtAt);
		// Ten minutes later the buy filled and the user opens the SELL setup:
		// the same item's context is still there, verbatim.
		assertSame(buyContext, cache.get(560, boughtAt + 10 * 60_000));
		// A different item never sees it.
		assertNull(cache.get(4151, boughtAt + 10 * 60_000));
	}

	@Test
	public void entriesExpireAfterTheTtl()
	{
		FlipContextCache cache = new FlipContextCache(1_000);
		cache.put(560, context(560), 0);
		assertSame(cache.get(560, 1_000), cache.get(560, 1_000));
		assertNull(cache.get(560, 1_001));
		// Expiry is permanent — a later read does not resurrect it.
		assertNull(cache.get(560, 500));
	}

	@Test
	public void freshestContextWinsPerItem()
	{
		FlipContextCache cache = new FlipContextCache(FlipContextCache.DEFAULT_TTL_MS);
		RuneFlipData.FastFlipItemContextResponse first = context(560);
		RuneFlipData.FastFlipItemContextResponse second = context(560);
		cache.put(560, first, 0);
		cache.put(560, second, 1);
		assertSame(second, cache.get(560, 2));
	}

	@Test
	public void invalidEntriesAreIgnored()
	{
		FlipContextCache cache = new FlipContextCache(FlipContextCache.DEFAULT_TTL_MS);
		cache.put(-1, context(-1), 0);
		cache.put(560, null, 0);
		assertNull(cache.get(-1, 0));
		assertNull(cache.get(560, 0));
	}
}
