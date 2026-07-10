package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Panel responsiveness (v0.8.10): the pure pieces behind the instant UI —
 * optimistic pill highlight (local state before any response), the
 * stale-response sequencer (only the newest request renders) and the short
 * TTL caches (fresh hits render immediately, no fetch). The Swing wiring
 * renders whatever these decide; the decisions are pinned here.
 */
public class ResponsivenessTest
{
	// ── Optimistic pills: local state BEFORE the fetch completes ────────────

	@Test
	public void timeframeClickHighlightsTheClickedPillImmediately()
	{
		// No override active → clicking 30m highlights 30m at once.
		assertEquals(Integer.valueOf(30),
			StrategyParams.optimisticTimeframe(0, 30));
		// Different override active → the new click wins at once.
		assertEquals(Integer.valueOf(480),
			StrategyParams.optimisticTimeframe(30, 480));
	}

	@Test
	public void clickingTheActivePillTogglesOffAndWaitsForTheEcho()
	{
		// Toggle-off: the effective strategy (saved prefs / default) is only
		// known from the response echo — no local guess is highlighted.
		assertNull(StrategyParams.optimisticTimeframe(30, 30));
		assertNull(StrategyParams.optimisticRisk("MEDIUM", "MEDIUM"));
	}

	@Test
	public void riskClickHighlightsTheClickedPillImmediately()
	{
		assertEquals("MEDIUM", StrategyParams.optimisticRisk("", "MEDIUM"));
		assertEquals("HIGH", StrategyParams.optimisticRisk("LOW", "HIGH"));
	}

	// ── Stale responses: only the newest request renders ────────────────────

	@Test
	public void olderResponseIsIgnoredWhenANewerRequestExists()
	{
		RequestSequencer seq = new RequestSequencer();
		long low = seq.begin();    // Low clicked…
		long medium = seq.begin(); // …then Med…
		long high = seq.begin();   // …then High, quickly.

		// The Low/Med answers arrive late: both are stale, only High renders.
		assertFalse(seq.isCurrent(low));
		assertFalse(seq.isCurrent(medium));
		assertTrue(seq.isCurrent(high));
	}

	@Test
	public void aTicketStaysCurrentUntilTheNextRequestBegins()
	{
		RequestSequencer seq = new RequestSequencer();
		long ticket = seq.begin();
		assertTrue(seq.isCurrent(ticket));
		seq.begin();
		assertFalse(seq.isCurrent(ticket));
	}

	// ── Short TTL cache: fresh hits render immediately ──────────────────────

	@Test
	public void freshCacheHitReturnsTheExactCachedResponse()
	{
		ShortTtlCache<RuneFlipData.FastFlipOverviewResponse> cache =
			new ShortTtlCache<>(20_000);
		RuneFlipData.FastFlipOverviewResponse response =
			new RuneFlipData.FastFlipOverviewResponse();
		cache.put("&riskLevel=MEDIUM", response, 1_000);

		// Within the TTL the exact response renders — zero network wait.
		assertSame(response, cache.get("&riskLevel=MEDIUM", 15_000));
	}

	@Test
	public void expiredEntriesAndUnknownKeysMiss()
	{
		ShortTtlCache<String> cache = new ShortTtlCache<>(20_000);
		cache.put("key", "value", 0);

		assertNull("expired entry must miss", cache.get("key", 20_001));
		assertNull("unknown key must miss", cache.get("other", 1_000));
	}

	@Test
	public void cacheKeysAreIsolatedPerStrategy()
	{
		ShortTtlCache<String> cache = new ShortTtlCache<>(20_000);
		cache.put("&riskLevel=LOW", "low", 0);
		cache.put("&riskLevel=HIGH", "high", 0);

		assertEquals("low", cache.get("&riskLevel=LOW", 1_000));
		assertEquals("high", cache.get("&riskLevel=HIGH", 1_000));
	}

	@Test
	public void clearDropsEverythingAndNullsAreNeverCached()
	{
		ShortTtlCache<String> cache = new ShortTtlCache<>(20_000);
		cache.put("a", "1", 0);
		cache.clear();
		assertNull(cache.get("a", 1));

		cache.put("b", null, 0);
		assertNull(cache.get("b", 1));
		cache.put(null, "x", 0);
		assertNull(cache.get(null, 1));
	}

	// ── Loading vs empty: the loading line is its own honest state ──────────

	@Test
	public void loadingLinesAreDistinctFromTheEmptyAndOfflineStates()
	{
		// The item card and the Top-3 card each have an explicit in-flight
		// state, so "No matches" can never be what an in-flight request shows.
		assertTrue(RuneFlipPanel.ITEM_LOADING_LINE.contains("Loading"));
		assertTrue(RuneFlipPanel.UPDATING_LINE.contains("Updating"));
		assertFalse(RuneFlipPanel.ITEM_LOADING_LINE.contains("No matches"));
		assertFalse(RuneFlipPanel.UPDATING_LINE.contains("No matches"));
	}

	// ── Selected item keeps priority over the Top 3 (unchanged invariant) ───

	@Test
	public void selectedItemResponseHasPriorityOverTheOverview()
	{
		// While an item is open (including its loading state, which sets
		// hasSelection), the context card shows and the Top 3 stays hidden —
		// an overview response can never displace the selected item.
		assertTrue(PanelVisibility.showSelectedItem(true, true));
		assertFalse(PanelVisibility.showTopThree(true, true));
	}
}
