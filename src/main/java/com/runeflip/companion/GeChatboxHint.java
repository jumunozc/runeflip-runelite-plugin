package com.runeflip.companion;

import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Visible GE chatbox hint (v0.8.13, Copilot-style; hardened in the v0.8.14
 * hotfix). Renders ONE display-only TEXT line inside the chatbox while a GE
 * editor is open — "RuneFlip item: …" on the search, "Press [key] to set
 * RuneFlip price/quantity: …" on the value editors — so the assist is
 * discoverable without right-clicking.
 *
 * <p>v0.8.14 hardening — why the hint could stay invisible before:
 * <ul>
 *   <li><b>Stale child.</b> The GE search rebuilds the chatbox mes-layer,
 *       wiping dynamic children; the old reference still LOOKED alive (same
 *       text, not hidden), so the idempotency check skipped re-creating it.
 *       Attachment is now verified against the container's real child slot
 *       every update (each game tick), re-creating after any rebuild.</li>
 *   <li><b>One fixed container.</b> If the mes-layer container is missing or
 *       hidden, safe fallbacks are tried in order: the chatbox search-results
 *       layer, then the chatbox parent. All are standard overlay parents.</li>
 *   <li><b>Fixed bounds.</b> Position/size are now computed from the actual
 *       container so the line always lands inside its visible area.</li>
 * </ul>
 *
 * <p>COMPLIANCE: this adds a purely presentational child widget to a chatbox
 * layer (the standard overlay technique of Plugin Hub flipping tools). It
 * reads and mutates nothing of the game's own widgets, fires no script, and
 * performs no input. The hint may carry a click listener — a RuneLite
 * widget-op callback that only runs when the USER clicks the hint itself,
 * routing to {@link GeFieldAssistService} where the write stays gated
 * (explicit source + verified editor, prepare-only, never submit).
 * Must be called on the client thread.
 */
class GeChatboxHint
{
	private static final Logger log = LoggerFactory.getLogger(GeChatboxHint.class);

	/** Dark gold — readable on the beige chatbox background. */
	private static final int HINT_COLOR = 0x99_5C00;
	/** Fallback line geometry when the container reports no size yet. */
	static final int DEFAULT_WIDTH = 495;
	static final int DEFAULT_Y = 55;
	static final int LINE_HEIGHT = 16;

	/** What an update did — surfaced for the plugin's debug diagnostics. */
	enum Result
	{
		/** A new hint child was created on a container. */
		SHOWN,
		/** The existing child is still attached with the same text. */
		KEPT,
		/** No visible container was available; nothing rendered. */
		NO_CONTAINER,
		/** The hint was removed (editor closed / no text). */
		CLEARED
	}

	/** Overlay parents, tried in order: the chatbox mes-layer container,
	 *  the GE search-results layer, the chatbox parent. */
	private static final int[] CONTAINER_CANDIDATES = {
		ComponentID.CHATBOX_CONTAINER,
		ComponentID.CHATBOX_GE_SEARCH_RESULTS,
		ComponentID.CHATBOX_PARENT,
	};

	private final Client client;
	private Widget hint;

	GeChatboxHint(Client client)
	{
		this.client = client;
	}

	/**
	 * Shows (or refreshes) the hint line. Idempotent per game tick: when the
	 * current child is still ATTACHED to its container with the same text,
	 * nothing is touched; a chatbox rebuild (which discards dynamic
	 * children) is detected via the container's child slot and the hint is
	 * re-created.
	 */
	Result show(String text, Runnable onUserClick)
	{
		if (text == null || text.isEmpty())
		{
			return clear();
		}
		Widget container = findContainer();
		if (container == null)
		{
			hint = null;
			return Result.NO_CONTAINER;
		}
		if (isAttached(container, hint) && text.equals(hint.getText()))
		{
			return Result.KEPT;
		}
		clear();
		Widget child = container.createChild(-1, WidgetType.TEXT);
		child.setText(text);
		child.setTextColor(HINT_COLOR);
		child.setFontId(FontID.VERDANA_11_BOLD);
		child.setTextShadowed(false);
		child.setOriginalX(0);
		child.setOriginalY(hintY(container.getHeight()));
		child.setOriginalWidth(hintWidth(container.getWidth()));
		child.setOriginalHeight(LINE_HEIGHT);
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
		if (log.isDebugEnabled())
		{
			java.awt.Rectangle b = child.getBounds();
			log.debug("RuneFlip GE hint rendered on {} (index {}) bounds={}",
				container.getId(), child.getIndex(),
				b == null ? "?" : b.x + "," + b.y + " " + b.width + "x" + b.height);
		}
		return Result.SHOWN;
	}

	/** Hides the current hint (safe on stale/recycled references). */
	Result clear()
	{
		if (hint != null)
		{
			hint.setHidden(true);
			hint = null;
			return Result.CLEARED;
		}
		return Result.CLEARED;
	}

	/** First candidate container that exists and is not hidden, or null. */
	private Widget findContainer()
	{
		for (int componentId : CONTAINER_CANDIDATES)
		{
			Widget candidate = client.getWidget(componentId);
			if (candidate != null && !candidate.isHidden())
			{
				return candidate;
			}
		}
		return null;
	}

	/**
	 * Whether OUR child is still the one sitting in the container's dynamic
	 * child slot. After the game rebuilds the chatbox, the slot is empty or
	 * re-used by a different widget — the stale reference must not be
	 * trusted, even when it still carries the old text and is not hidden.
	 */
	private static boolean isAttached(Widget container, Widget child)
	{
		return child != null && !child.isHidden()
			&& container.getChild(child.getIndex()) == child;
	}

	// ── pure geometry (unit-tested) ─────────────────────────────────────────

	/** Line y inside the container: the classic under-the-prompt line when it
	 *  fits, clamped onto the container's visible area when it would not. */
	static int hintY(int containerHeight)
	{
		if (containerHeight <= 0)
		{
			return DEFAULT_Y;
		}
		if (DEFAULT_Y + LINE_HEIGHT <= containerHeight)
		{
			return DEFAULT_Y;
		}
		return Math.max(0, containerHeight - LINE_HEIGHT);
	}

	/** Full container width when known, the classic chatbox width if not. */
	static int hintWidth(int containerWidth)
	{
		return containerWidth > 0 ? containerWidth : DEFAULT_WIDTH;
	}
}
