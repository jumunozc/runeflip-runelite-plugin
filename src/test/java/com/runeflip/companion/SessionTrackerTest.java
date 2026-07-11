package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * SessionPanel accounting (v0.8.7 design) — pure sums over observed offer
 * completions. Pins the three invariants that keep the KPIs honest: only
 * ACTIVE→DONE transitions seen this session count (login replay is baseline),
 * profit only exists where a session cost basis exists (weighted-average
 * matching), and reset really clears everything.
 */
public class SessionTrackerTest
{
	private static final int SLOT = 2;
	private static final int ITEM = 4151;

	@Test
	public void loginReplayOfACompletedOfferIsBaselineNotActivity()
	{
		SessionTracker tracker = new SessionTracker(0);
		// First observation of the slot arrives already DONE (login replay of
		// an offer completed before the session): never counted.
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_SELL, ITEM, 100, 50_000);

		SessionTracker.Stats stats = tracker.stats(60_000);
		assertFalse(stats.hasActivity);
		assertEquals(0, stats.flips);
		assertEquals(0, stats.profit);
	}

	@Test
	public void buyThenSellRealizesTheMatchedProfit()
	{
		SessionTracker tracker = new SessionTracker(0);
		// Buy 100 @ 1,000 gp total 100,000 — seen active first, then done.
		tracker.observe(SLOT, SessionTracker.SlotPhase.ACTIVE_BUY, ITEM, 0, 0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_BUY, ITEM, 100, 100_000);
		// Sell the same 100 for 110,000 received.
		tracker.observe(SLOT, SessionTracker.SlotPhase.ACTIVE_SELL, ITEM, 0, 0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_SELL, ITEM, 100, 110_000);

		SessionTracker.Stats stats = tracker.stats(3_600_000);
		assertTrue(stats.hasActivity);
		assertEquals(10_000, stats.profit);
		assertEquals(1, stats.flips);
		assertEquals(0.10, stats.roi, 1e-9);
		assertEquals(0, stats.openBuysCost);
		// One hour elapsed → profit/hour equals the realized profit.
		assertEquals(10_000d, stats.profitPerHour, 1e-6);
	}

	@Test
	public void sellWithoutSessionBuyIsAFlipButNeverInventsProfit()
	{
		SessionTracker tracker = new SessionTracker(0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.ACTIVE_SELL, ITEM, 0, 0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_SELL, ITEM, 50, 75_000);

		SessionTracker.Stats stats = tracker.stats(1_000);
		assertTrue(stats.hasActivity);
		assertEquals(1, stats.flips);
		// No session cost basis → no invented profit, no ROI.
		assertEquals(0, stats.profit);
		assertNull(stats.roi);
	}

	@Test
	public void partialSellMatchesAtWeightedAverageCost()
	{
		SessionTracker tracker = new SessionTracker(0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.ACTIVE_BUY, ITEM, 0, 0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_BUY, ITEM, 100, 100_000);
		// Sell only 40 of them for 48,000: basis 40 × 1,000 = 40,000.
		tracker.observe(SLOT, SessionTracker.SlotPhase.ACTIVE_SELL, ITEM, 0, 0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_SELL, ITEM, 40, 48_000);

		SessionTracker.Stats stats = tracker.stats(1_000);
		assertEquals(8_000, stats.profit);
		// The other 60 units stay as open buys at their average cost.
		assertEquals(60_000, stats.openBuysCost);
	}

	@Test
	public void lossesGoNegativeInsteadOfBeingHidden()
	{
		SessionTracker tracker = new SessionTracker(0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.ACTIVE_BUY, ITEM, 0, 0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_BUY, ITEM, 10, 10_000);
		tracker.observe(SLOT, SessionTracker.SlotPhase.ACTIVE_SELL, ITEM, 0, 0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_SELL, ITEM, 10, 9_000);

		SessionTracker.Stats stats = tracker.stats(1_000);
		assertEquals(-1_000, stats.profit);
		assertTrue(stats.roi < 0);
	}

	@Test
	public void resetClearsEverythingAndRestartsTheClock()
	{
		SessionTracker tracker = new SessionTracker(0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.ACTIVE_BUY, ITEM, 0, 0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_BUY, ITEM, 10, 10_000);

		tracker.reset(500_000);
		SessionTracker.Stats stats = tracker.stats(560_000);
		assertFalse(stats.hasActivity);
		assertEquals(0, stats.profit);
		assertEquals(0, stats.openBuysCost);
		assertEquals(60_000, stats.sessionMillis);
	}

	@Test
	public void zeroQuantityAndUnknownItemsAreIgnored()
	{
		SessionTracker tracker = new SessionTracker(0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.ACTIVE_BUY, ITEM, 0, 0);
		// Zero filled (aborted before any fill) and itemId 0: no activity.
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_BUY, ITEM, 0, 0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.ACTIVE_BUY, 0, 0, 0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_BUY, 0, 10, 1_000);

		assertFalse(tracker.stats(1_000).hasActivity);
	}

	@Test
	public void sessionTimeLabelFormatsHoursAndMinutes()
	{
		assertEquals("00:00", SessionTracker.sessionTimeLabel(59_000));
		assertEquals("00:05", SessionTracker.sessionTimeLabel(5 * 60_000));
		assertEquals("02:19", SessionTracker.sessionTimeLabel(139 * 60_000));
		assertEquals("00:00", SessionTracker.sessionTimeLabel(-5));
	}

	@Test
	public void offerStateMapsToSessionPhases()
	{
		assertEquals(SessionTracker.SlotPhase.ACTIVE_BUY,
			RuneFlipCompanionPlugin.sessionPhaseOf(
				net.runelite.api.GrandExchangeOfferState.BUYING));
		assertEquals(SessionTracker.SlotPhase.DONE_BUY,
			RuneFlipCompanionPlugin.sessionPhaseOf(
				net.runelite.api.GrandExchangeOfferState.BOUGHT));
		assertEquals(SessionTracker.SlotPhase.DONE_BUY,
			RuneFlipCompanionPlugin.sessionPhaseOf(
				net.runelite.api.GrandExchangeOfferState.CANCELLED_BUY));
		assertEquals(SessionTracker.SlotPhase.ACTIVE_SELL,
			RuneFlipCompanionPlugin.sessionPhaseOf(
				net.runelite.api.GrandExchangeOfferState.SELLING));
		assertEquals(SessionTracker.SlotPhase.DONE_SELL,
			RuneFlipCompanionPlugin.sessionPhaseOf(
				net.runelite.api.GrandExchangeOfferState.SOLD));
		assertEquals(SessionTracker.SlotPhase.EMPTY,
			RuneFlipCompanionPlugin.sessionPhaseOf(
				net.runelite.api.GrandExchangeOfferState.EMPTY));
		assertEquals(SessionTracker.SlotPhase.EMPTY,
			RuneFlipCompanionPlugin.sessionPhaseOf(null));
	}

	@Test
	public void profitLabelIsSignedAndPortfolioSumsOnlyObservedCoins()
	{
		assertEquals("+10K gp", RuneFlipPanel.profitLabel(10_000));
		assertTrue(RuneFlipPanel.profitLabel(-1_050).startsWith("−"));

		RuneFlipData.Capital capital = new RuneFlipData.Capital();
		assertNull(RuneFlipPanel.portfolioCoinsOf(null));
		assertNull(RuneFlipPanel.portfolioCoinsOf(capital));
		capital.inventoryCoins = 250_000L;
		assertEquals(Long.valueOf(250_000L),
			RuneFlipPanel.portfolioCoinsOf(capital));
		capital.bankCoinsLastSeen = 7_000_000L;
		assertEquals(Long.valueOf(7_250_000L),
			RuneFlipPanel.portfolioCoinsOf(capital));
	}

	// ── avgBuyPriceOf (v0.8.19) — the SELL-hover cost basis ─────────────────

	@Test
	public void avgBuyPriceReflectsSessionBuysOnly()
	{
		SessionTracker tracker = new SessionTracker(0);
		// Nothing tracked yet → no cost basis, never invented.
		assertNull(tracker.avgBuyPriceOf(ITEM));

		// Buy 100 @ 1,000 gp each (100,000 total), observed active → done.
		tracker.observe(SLOT, SessionTracker.SlotPhase.ACTIVE_BUY, ITEM, 0, 0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_BUY, ITEM, 100, 100_000);
		assertEquals(Long.valueOf(1_000), tracker.avgBuyPriceOf(ITEM));

		// A second buy at a higher price moves the weighted average.
		tracker.observe(SLOT, SessionTracker.SlotPhase.ACTIVE_BUY, ITEM, 0, 0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_BUY, ITEM, 100, 140_000);
		assertEquals(Long.valueOf(1_200), tracker.avgBuyPriceOf(ITEM));

		// Other items stay unknown.
		assertNull(tracker.avgBuyPriceOf(ITEM + 1));
	}

	@Test
	public void avgBuyPriceIsConsumedByCompletedSellsAndClearedByReset()
	{
		SessionTracker tracker = new SessionTracker(0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.ACTIVE_BUY, ITEM, 0, 0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_BUY, ITEM, 100, 100_000);

		// Selling the whole position consumes the basis → unknown again.
		tracker.observe(SLOT, SessionTracker.SlotPhase.ACTIVE_SELL, ITEM, 0, 0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_SELL, ITEM, 100, 110_000);
		assertNull(tracker.avgBuyPriceOf(ITEM));

		// And reset clears any remaining position.
		tracker.observe(SLOT, SessionTracker.SlotPhase.ACTIVE_BUY, ITEM, 0, 0);
		tracker.observe(SLOT, SessionTracker.SlotPhase.DONE_BUY, ITEM, 10, 10_000);
		tracker.reset(0);
		assertNull(tracker.avgBuyPriceOf(ITEM));
	}
}
