package com.runeflip.companion;

import java.util.List;

/**
 * Canonical fingerprints for observed state, so the plugin can skip POSTs
 * when nothing changed (a periodic keepalive still refreshes freshness).
 * Pure string building — no hashing, no collisions, trivially testable.
 */
public final class SnapshotFingerprint
{
	private SnapshotFingerprint()
	{
	}

	/** Canonical representation of a GE slots payload. */
	public static String ofSlots(List<GeSlotPayload> slots)
	{
		StringBuilder sb = new StringBuilder();
		for (GeSlotPayload s : slots)
		{
			sb.append(s.slot).append(':')
				.append(s.status).append(':')
				.append(s.itemId).append(':')
				.append(s.offerType).append(':')
				.append(s.price).append(':')
				.append(s.quantity).append(':')
				.append(s.quantityFilled).append('|');
		}
		return sb.toString();
	}

	/** Canonical representation of a capital observation. */
	public static String ofCapital(
		Integer inventoryCoins,
		Integer bankCoinsLastSeen,
		String bankLastSeenAt)
	{
		return inventoryCoins + ":" + bankCoinsLastSeen + ":" + bankLastSeenAt;
	}
}
