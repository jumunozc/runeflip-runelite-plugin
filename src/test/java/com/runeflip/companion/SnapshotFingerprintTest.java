package com.runeflip.companion;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * The fingerprint must be identical for identical observations (so unchanged
 * snapshots are skipped) and differ on any observable change (so real
 * changes always reach the backend).
 */
public class SnapshotFingerprintTest
{
	private static GeSlotPayload active(int slot, int price, int filled)
	{
		return new GeSlotPayload(slot, "ACTIVE", 561, "Nature rune", "BUY", price, 100, filled);
	}

	@Test
	public void identicalSlotsProduceIdenticalFingerprints()
	{
		String a = SnapshotFingerprint.ofSlots(
			Arrays.asList(active(0, 100, 5), GeSlotPayload.empty(1)));
		String b = SnapshotFingerprint.ofSlots(
			Arrays.asList(active(0, 100, 5), GeSlotPayload.empty(1)));
		assertEquals(a, b);
	}

	@Test
	public void anyFieldChangeChangesTheFingerprint()
	{
		String base = SnapshotFingerprint.ofSlots(
			Collections.singletonList(active(0, 100, 5)));

		assertNotEquals(base, SnapshotFingerprint.ofSlots(
			Collections.singletonList(active(0, 101, 5)))); // price
		assertNotEquals(base, SnapshotFingerprint.ofSlots(
			Collections.singletonList(active(0, 100, 6)))); // fill progress
		assertNotEquals(base, SnapshotFingerprint.ofSlots(
			Collections.singletonList(active(1, 100, 5)))); // slot index
		assertNotEquals(base, SnapshotFingerprint.ofSlots(
			Collections.singletonList(GeSlotPayload.empty(0)))); // status
	}

	@Test
	public void capitalFingerprintTracksEachField()
	{
		String base = SnapshotFingerprint.ofCapital(1000, 5000, "t1");
		assertEquals(base, SnapshotFingerprint.ofCapital(1000, 5000, "t1"));
		assertNotEquals(base, SnapshotFingerprint.ofCapital(1001, 5000, "t1"));
		assertNotEquals(base, SnapshotFingerprint.ofCapital(1000, 5001, "t1"));
		assertNotEquals(base, SnapshotFingerprint.ofCapital(1000, null, null));
	}
}
