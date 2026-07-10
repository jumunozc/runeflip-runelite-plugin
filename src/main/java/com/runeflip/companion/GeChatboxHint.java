package com.runeflip.companion;

import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/**
 * Visible GE chatbox hint (v0.8.13, Copilot-style). Renders ONE display-only
 * TEXT line inside the chatbox while a GE editor is open — "RuneFlip item:
 * …" on the search, "Press [key] to set RuneFlip price/quantity: …" on the
 * value editors — so the assist is discoverable without right-clicking.
 *
 * <p>COMPLIANCE: this adds a purely presentational child widget to the
 * chatbox container (the standard overlay technique of Plugin Hub flipping
 * tools). It reads and mutates nothing of the game's own widgets, fires no
 * script, and performs no input. The hint may carry a click listener — a
 * RuneLite widget-op callback that only runs when the USER clicks the hint
 * itself, routing to {@link GeFieldAssistService} where the write stays
 * gated (explicit source + verified editor, prepare-only, never submit).
 * Must be called on the client thread.
 */
class GeChatboxHint
{
	/** Dark gold — readable on the beige chatbox background. */
	private static final int HINT_COLOR = 0x99_5C00;

	private final Client client;
	private Widget hint;

	GeChatboxHint(Client client)
	{
		this.client = client;
	}

	/**
	 * Shows (or refreshes) the hint line. Idempotent per text: when the
	 * current child still exists with the same text nothing is touched; a
	 * chatbox rebuild discards the old child and a new one is created.
	 */
	void show(String text, Runnable onUserClick)
	{
		if (text == null || text.isEmpty())
		{
			clear();
			return;
		}
		Widget container = client.getWidget(ComponentID.CHATBOX_CONTAINER);
		if (container == null || container.isHidden())
		{
			hint = null;
			return;
		}
		if (hint != null && text.equals(hint.getText()) && !hint.isHidden())
		{
			return;
		}
		clear();
		Widget child = container.createChild(-1, WidgetType.TEXT);
		child.setText(text);
		child.setTextColor(HINT_COLOR);
		child.setFontId(FontID.VERDANA_11_BOLD);
		child.setTextShadowed(false);
		child.setOriginalX(0);
		child.setOriginalY(55);
		child.setOriginalWidth(495);
		child.setOriginalHeight(16);
		child.setXTextAlignment(1); // centered, like the native prompts
		if (onUserClick != null)
		{
			// User-click only: a widget-op callback on OUR OWN hint line.
			child.setAction(0, "Use");
			child.setHasListener(true);
			child.setOnOpListener((JavaScriptCallback) ev -> onUserClick.run());
		}
		child.revalidate();
		hint = child;
	}

	/** Hides the current hint (safe on stale/recycled references). */
	void clear()
	{
		if (hint != null)
		{
			hint.setHidden(true);
			hint = null;
		}
	}
}
