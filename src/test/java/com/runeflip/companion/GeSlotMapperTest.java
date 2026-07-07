package com.runeflip.companion;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GeSlotMapperTest
{
	/** Minimal passive stub of the official offer interface. */
	private static GrandExchangeOffer offer(
		GrandExchangeOfferState state, int itemId, int price, int total, int sold)
	{
		return new GrandExchangeOffer()
		{
			@Override
			public int getQuantitySold()
			{
				return sold;
			}

			@Override
			public int getItemId()
			{
				return itemId;
			}

			@Override
			public int getTotalQuantity()
			{
				return total;
			}

			@Override
			public int getPrice()
			{
				return price;
			}

			@Override
			public int getSpent()
			{
				return price * sold;
			}

			@Override
			public GrandExchangeOfferState getState()
			{
				return state;
			}
		};
	}

	@Test
	public void emptyStateMapsToEmptySlotWithoutOfferFields()
	{
		GeSlotPayload slot =
			GeSlotMapper.map(3, offer(GrandExchangeOfferState.EMPTY, 0, 0, 0, 0), null);

		assertEquals(3, slot.slot);
		assertEquals("EMPTY", slot.status);
		assertNull(slot.itemId);
		assertNull(slot.itemName);
		assertNull(slot.offerType);
		assertNull(slot.price);
		assertNull(slot.quantity);
		assertNull(slot.quantityFilled);
	}

	@Test
	public void nullOfferMapsToEmpty()
	{
		assertEquals("EMPTY", GeSlotMapper.map(0, null, null).status);
	}

	@Test
	public void buyingMapsToActiveBuyWithAllFields()
	{
		GeSlotPayload slot = GeSlotMapper.map(
			0, offer(GrandExchangeOfferState.BUYING, 4151, 1_200_000, 10, 2), "Abyssal whip");

		assertEquals("ACTIVE", slot.status);
		assertEquals("BUY", slot.offerType);
		assertEquals(Integer.valueOf(4151), slot.itemId);
		assertEquals("Abyssal whip", slot.itemName);
		assertEquals(Integer.valueOf(1_200_000), slot.price);
		assertEquals(Integer.valueOf(10), slot.quantity);
		assertEquals(Integer.valueOf(2), slot.quantityFilled);
	}

	@Test
	public void sellingMapsToActiveSell()
	{
		GeSlotPayload slot = GeSlotMapper.map(
			1, offer(GrandExchangeOfferState.SELLING, 561, 92, 500, 100), null);

		assertEquals("ACTIVE", slot.status);
		assertEquals("SELL", slot.offerType);
		assertNull(slot.itemName);
	}

	@Test
	public void boughtAndSoldMapToCompleted()
	{
		assertEquals("COMPLETED", GeSlotMapper.map(
			0, offer(GrandExchangeOfferState.BOUGHT, 4151, 100, 10, 10), null).status);
		GeSlotPayload sold = GeSlotMapper.map(
			1, offer(GrandExchangeOfferState.SOLD, 561, 92, 500, 500), null);
		assertEquals("COMPLETED", sold.status);
		assertEquals("SELL", sold.offerType);
	}

	@Test
	public void cancelledStatesMapToCancelledWithDirection()
	{
		GeSlotPayload buy = GeSlotMapper.map(
			0, offer(GrandExchangeOfferState.CANCELLED_BUY, 4151, 100, 10, 3), null);
		assertEquals("CANCELLED", buy.status);
		assertEquals("BUY", buy.offerType);

		GeSlotPayload sell = GeSlotMapper.map(
			1, offer(GrandExchangeOfferState.CANCELLED_SELL, 561, 92, 500, 20), null);
		assertEquals("CANCELLED", sell.status);
		assertEquals("SELL", sell.offerType);
	}

	@Test
	public void filledIsClampedToQuantity()
	{
		GeSlotPayload slot = GeSlotMapper.map(
			0, offer(GrandExchangeOfferState.BUYING, 4151, 100, 5, 9), null);
		assertEquals(Integer.valueOf(5), slot.quantityFilled);
	}

	@Test
	public void nonEmptyStateWithoutRealItemFallsBackToEmpty()
	{
		GeSlotPayload slot = GeSlotMapper.map(
			0, offer(GrandExchangeOfferState.BUYING, 0, 100, 10, 0), null);
		assertEquals("EMPTY", slot.status);
		assertNull(slot.itemId);
	}

	@Test
	public void itemNameIsTrimmedAndTruncatedTo64Chars()
	{
		StringBuilder longName = new StringBuilder();
		for (int i = 0; i < 10; i++)
		{
			longName.append("0123456789");
		}
		GeSlotPayload slot = GeSlotMapper.map(
			0,
			offer(GrandExchangeOfferState.BUYING, 4151, 100, 10, 0),
			"  " + longName + "  ");
		assertEquals(64, slot.itemName.length());

		GeSlotPayload blank = GeSlotMapper.map(
			1, offer(GrandExchangeOfferState.BUYING, 4151, 100, 10, 0), "   ");
		assertNull(blank.itemName);
	}
}
