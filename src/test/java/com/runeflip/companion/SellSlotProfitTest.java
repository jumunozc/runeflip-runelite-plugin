package com.runeflip.companion;

import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * SELL-slot hover PROFIT line (v0.8.19). Pins the goal's cases: a tracked
 * gain shows "PROFIT: +… gp", a tracked loss "PROFIT: -… gp", a missing
 * cost basis "PROFIT: unknown" (never invented), BUY/empty/completed slots
 * get no line at all, and the tax mirrors RuneFlip's backend rule
 * (min(floor(price × 2%), 5M) per item).
 */
public class SellSlotProfitTest
{
	// ── which slots get the line ─────────────────────────────────────────────

	@Test
	public void onlyAnActiveSellSlotWithAnItemGetsTheLine()
	{
		assertTrue(SellSlotProfit.appliesTo(
			GrandExchangeOfferState.SELLING, 4151));

		// BUY slots never get the line — any buy state.
		assertFalse(SellSlotProfit.appliesTo(
			GrandExchangeOfferState.BUYING, 4151));
		assertFalse(SellSlotProfit.appliesTo(
			GrandExchangeOfferState.BOUGHT, 4151));
		// Empty slots never get the line.
		assertFalse(SellSlotProfit.appliesTo(
			GrandExchangeOfferState.EMPTY, 0));
		// Completed / cancelled sells are not an OPEN sell.
		assertFalse(SellSlotProfit.appliesTo(
			GrandExchangeOfferState.SOLD, 4151));
		assertFalse(SellSlotProfit.appliesTo(
			GrandExchangeOfferState.CANCELLED_SELL, 4151));
		// A sell state without a real item is never eligible.
		assertFalse(SellSlotProfit.appliesTo(
			GrandExchangeOfferState.SELLING, 0));
		assertFalse(SellSlotProfit.appliesTo(null, 4151));
	}

	// ── formula: netSell = sellPrice*qty − tax; cost = avgBuy*qty ───────────

	@Test
	public void trackedBuyWithGainShowsAPositiveProfitLine()
	{
		// Sell 100 @ 1,100 gp, bought @ 1,000 gp: tax 22/item →
		// net (1,100−22)×100 = 107,800; cost 100,000 → +7,800.
		Long profit = SellSlotProfit.profitOf(1_100, 100, 1_000L);
		assertEquals(Long.valueOf(7_800), profit);
		assertEquals("PROFIT: +7.8K gp", SellSlotProfit.label(profit));
		assertTrue(SellSlotProfit.tooltipLine(profit).contains("PROFIT: +7.8K gp"));
	}

	@Test
	public void trackedBuyWithLossShowsANegativeProfitLine()
	{
		// Sell 100 @ 980 gp, bought @ 1,000 gp: tax 19/item →
		// net (980−19)×100 = 96,100; cost 100,000 → −3,900.
		Long profit = SellSlotProfit.profitOf(980, 100, 1_000L);
		assertEquals(Long.valueOf(-3_900), profit);
		assertEquals("PROFIT: -3.9K gp", SellSlotProfit.label(profit));
		assertTrue(SellSlotProfit.tooltipLine(profit).contains("PROFIT: -3.9K gp"));
	}

	@Test
	public void noTrackedBuyShowsUnknownAndNeverInventsAPrice()
	{
		assertNull(SellSlotProfit.profitOf(1_100, 100, null));
		assertNull(SellSlotProfit.profitOf(1_100, 100, 0L));
		assertNull(SellSlotProfit.profitOf(0, 100, 1_000L));
		assertNull(SellSlotProfit.profitOf(1_100, 0, 1_000L));
		assertEquals("PROFIT: unknown", SellSlotProfit.label(null));
		assertTrue(SellSlotProfit.tooltipLine(null).contains("PROFIT: unknown"));
	}

	// ── tax: min(floor(price × 2%), 5M) per item, RuneFlip's backend rule ───

	@Test
	public void taxMirrorsTheBackendRule()
	{
		// 2% floored per item.
		assertEquals(22, SellSlotProfit.taxPerItem(1_100));
		assertEquals(19, SellSlotProfit.taxPerItem(980));
		// Sub-50 gp floors to zero (no tax).
		assertEquals(0, SellSlotProfit.taxPerItem(49));
		assertEquals(1, SellSlotProfit.taxPerItem(50));
		// Capped at 5M per item.
		assertEquals(5_000_000, SellSlotProfit.taxPerItem(1_000_000_000));
		assertEquals(0, SellSlotProfit.taxPerItem(0));
	}

	@Test
	public void taxIsAppliedPerItemAcrossTheWholeQuantity()
	{
		// 10 items @ 100 gp bought at 100: only the tax moves the result —
		// tax 2/item → net 980, cost 1,000 → −20.
		assertEquals(Long.valueOf(-20),
			SellSlotProfit.profitOf(100, 10, 100L));
	}

	// ── copy: short, signed, compact ─────────────────────────────────────────

	@Test
	public void labelsAreCompactAndSigned()
	{
		assertEquals("PROFIT: +50K gp", SellSlotProfit.label(50_000L));
		assertEquals("PROFIT: -2K gp", SellSlotProfit.label(-2_000L));
		assertEquals("PROFIT: +950 gp", SellSlotProfit.label(950L));
		assertEquals("PROFIT: +1.25M gp", SellSlotProfit.label(1_250_000L));
		assertEquals("PROFIT: +12.5K gp", SellSlotProfit.label(12_500L));
		// Zero renders as a (non-negative) zero, never as a loss.
		assertEquals("PROFIT: +0 gp", SellSlotProfit.label(0L));
	}

	@Test
	public void tooltipLineColorsGainGreenLossRedUnknownMuted()
	{
		assertTrue(SellSlotProfit.tooltipLine(50_000L).startsWith("<col=4cba86>"));
		assertTrue(SellSlotProfit.tooltipLine(-2_000L).startsWith("<col=e06767>"));
		assertTrue(SellSlotProfit.tooltipLine(null).startsWith("<col=8b91a0>"));
		assertTrue(SellSlotProfit.tooltipLine(50_000L).endsWith("</col>"));
	}
}
