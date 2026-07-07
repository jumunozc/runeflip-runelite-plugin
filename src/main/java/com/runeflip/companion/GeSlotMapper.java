package com.runeflip.companion;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Maps RuneLite's official {@link GrandExchangeOffer} (a passive observation)
 * into the RuneFlip snapshot contract. Pure function of the offer — nothing
 * here can act on the game.
 *
 * State mapping (docs/runelite-readonly-contract.md §5):
 *   EMPTY                       -> EMPTY (no offer fields)
 *   BUYING / SELLING            -> ACTIVE  + BUY / SELL
 *   BOUGHT / SOLD               -> COMPLETED + BUY / SELL
 *   CANCELLED_BUY / _SELL       -> CANCELLED + BUY / SELL
 *   anything else               -> UNKNOWN when the direction can still be
 *                                  inferred from the state name; otherwise the
 *                                  slot is NOT reported (null) — the backend
 *                                  requires itemId+offerType on non-EMPTY
 *                                  slots and we never guess.
 */
public final class GeSlotMapper
{
	/** Backend caps itemName at 64 chars. */
	private static final int MAX_ITEM_NAME = 64;

	private GeSlotMapper()
	{
	}

	/**
	 * @return the slot payload, or null when the offer cannot be mapped safely
	 *         (the slot is then simply not reported in this snapshot).
	 */
	public static GeSlotPayload map(int slot, GrandExchangeOffer offer, String itemName)
	{
		if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
		{
			return GeSlotPayload.empty(slot);
		}

		// A non-empty state without a real item cannot satisfy the contract
		// (non-EMPTY requires a positive itemId); report the slot as empty.
		if (offer.getItemId() <= 0)
		{
			return GeSlotPayload.empty(slot);
		}

		String status = statusOf(offer.getState());
		String offerType = offerTypeOf(offer.getState());
		if (offerType == null)
		{
			// Unknown direction: never guess. Leave the slot unreported.
			return null;
		}

		int quantity = Math.max(0, offer.getTotalQuantity());
		// Clamp: the backend rejects quantityFilled > quantity.
		int filled = Math.min(Math.max(0, offer.getQuantitySold()), quantity);

		return new GeSlotPayload(
			slot,
			status,
			offer.getItemId(),
			truncate(itemName),
			offerType,
			Math.max(0, offer.getPrice()),
			quantity,
			filled
		);
	}

	private static String statusOf(GrandExchangeOfferState state)
	{
		switch (state)
		{
			case BUYING:
			case SELLING:
				return "ACTIVE";
			case BOUGHT:
			case SOLD:
				return "COMPLETED";
			case CANCELLED_BUY:
			case CANCELLED_SELL:
				return "CANCELLED";
			default:
				return "UNKNOWN";
		}
	}

	private static String offerTypeOf(GrandExchangeOfferState state)
	{
		switch (state)
		{
			case BUYING:
			case BOUGHT:
			case CANCELLED_BUY:
				return "BUY";
			case SELLING:
			case SOLD:
			case CANCELLED_SELL:
				return "SELL";
			default:
				// Future/unknown states: try the enum name, else give up.
				String name = state.name();
				if (name.contains("BUY"))
				{
					return "BUY";
				}
				if (name.contains("SELL"))
				{
					return "SELL";
				}
				return null;
		}
	}

	private static String truncate(String name)
	{
		if (name == null || name.trim().isEmpty())
		{
			return null;
		}
		String trimmed = name.trim();
		return trimmed.length() <= MAX_ITEM_NAME
			? trimmed
			: trimmed.substring(0, MAX_ITEM_NAME);
	}
}
