package com.runeflip.companion;

import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Read-only hover detection over the eight GE offer-slot widgets (v0.8.19).
 * Answers "which slot box is the mouse resting on right now?" by comparing
 * the client-reported mouse canvas position against the slot widgets'
 * bounds — official RuneLite reads only: no OCR, no pixel access, no input
 * hooks, and certainly no synthesis. Feeds the display-only SELL-slot
 * PROFIT tooltip; detecting a hover performs no action.
 */
class GeSlotHover
{
	/** The GE main screen has exactly eight offer slots. */
	static final int SLOT_COUNT = 8;

	private final Client client;

	GeSlotHover(Client client)
	{
		this.client = client;
	}

	/**
	 * The 0-based GE offer slot under the mouse, or -1 when none (GE closed,
	 * slot hidden, or the mouse elsewhere). Must be called on the client
	 * thread (widget reads).
	 */
	int hoveredSlot()
	{
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return -1;
		}
		for (int slot = 0; slot < SLOT_COUNT; slot++)
		{
			// INDEX_0..INDEX_7 are contiguous component ids (gameval).
			Widget box = client.getWidget(InterfaceID.GeOffers.INDEX_0 + slot);
			if (box == null || box.isHidden())
			{
				continue;
			}
			Rectangle bounds = box.getBounds();
			if (bounds != null && bounds.contains(mouse.getX(), mouse.getY()))
			{
				return slot;
			}
		}
		return -1;
	}
}
