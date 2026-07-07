package com.runeflip.companion;

import java.util.List;

/** Full snapshot body for POST /ge-slots/snapshot. One-way, read-only. */
public class GeSnapshotPayload
{
	public final String source = "runelite";
	public final String captureId;
	public final String capturedAt;
	public final List<GeSlotPayload> slots;

	public GeSnapshotPayload(String captureId, String capturedAt, List<GeSlotPayload> slots)
	{
		this.captureId = captureId;
		this.capturedAt = capturedAt;
		this.slots = slots;
	}
}
