package com.runeflip.companion;

/**
 * Read-only capital observation for POST /player-capital/snapshot. Inventory
 * coins are read passively from the official item container; bank coins are
 * only what the client last saw when the USER opened the bank manually.
 * Null fields are omitted by Gson — nothing is ever guessed.
 */
public class PlayerCapitalPayload
{
	public final String source = "runelite";
	public final String captureId;
	public final String capturedAt;
	public final Integer inventoryCoins;
	public final Integer bankCoinsLastSeen;
	public final String bankLastSeenAt;

	public PlayerCapitalPayload(
		String captureId,
		String capturedAt,
		Integer inventoryCoins,
		Integer bankCoinsLastSeen,
		String bankLastSeenAt)
	{
		this.captureId = captureId;
		this.capturedAt = capturedAt;
		this.inventoryCoins = inventoryCoins;
		this.bankCoinsLastSeen = bankCoinsLastSeen;
		this.bankLastSeenAt = bankLastSeenAt;
	}
}
