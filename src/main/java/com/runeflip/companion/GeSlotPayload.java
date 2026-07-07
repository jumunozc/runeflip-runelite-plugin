package com.runeflip.companion;

/**
 * One slot entry of the snapshot payload, mirroring the backend contract
 * (docs/runelite-readonly-contract.md). Null fields are omitted by Gson,
 * which matches the backend's validation: EMPTY slots must not carry offer
 * fields, non-EMPTY slots require itemId and offerType.
 */
public class GeSlotPayload
{
	public final int slot;
	public final String status;
	public final Integer itemId;
	public final String itemName;
	public final String offerType;
	public final Integer price;
	public final Integer quantity;
	public final Integer quantityFilled;

	public GeSlotPayload(
		int slot,
		String status,
		Integer itemId,
		String itemName,
		String offerType,
		Integer price,
		Integer quantity,
		Integer quantityFilled)
	{
		this.slot = slot;
		this.status = status;
		this.itemId = itemId;
		this.itemName = itemName;
		this.offerType = offerType;
		this.price = price;
		this.quantity = quantity;
		this.quantityFilled = quantityFilled;
	}

	public static GeSlotPayload empty(int slot)
	{
		return new GeSlotPayload(slot, "EMPTY", null, null, null, null, null, null);
	}
}
