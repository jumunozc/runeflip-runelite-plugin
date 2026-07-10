package com.runeflip.companion;

import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Visible GE chatbox hint (v0.8.13; layout polished in v0.8.16 to the
 * Flipping-Copilot look). Two layouts, both display-only:
 *
 * <ul>
 *   <li><b>Search row</b> — while the GE item search is open and still
 *       EMPTY, one row renders at the top of the search-results area (the
 *       slot the game's own "Last search:" row uses — below the "What would
 *       you like to buy/sell?" prompt and the typed-input line, above the
 *       "Start typing the name of an item…" helper): a small ITEM ICON on
 *       the left and "RuneFlip item: …" left-aligned next to it. When the
 *       native last-search row is showing, the hint sits one row height
 *       BELOW it. Once the user types, real results own the area and the
 *       hint is removed — it never overlaps them, the scrollbar or the
 *       "Show last searched" checkbox (the row ends at the same right edge
 *       as the native row content).</li>
 *   <li><b>Value line</b> — on the qty/price editors, one left-aligned line
 *       ("Press [key] to set RuneFlip price/quantity: …") on its OWN row
 *       under the prompt, never over it (the proven Flipping-Copilot
 *       geometry: x=10, y=40).</li>
 * </ul>
 *
 * <p>Rebuild-safe: attachment to the parent's real child slot is verified
 * every game tick (the GE search rebuilds the chatbox and silently wipes
 * dynamic children), and bounds are computed from the actual parent so the
 * hint always lands inside the visible area. Closing the editor removes the
 * hint.
 *
 * <p>COMPLIANCE: this adds purely presentational child widgets to a chatbox
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

	/** RuneFlip gold — readable on the dark search-results background. */
	static final int SEARCH_TEXT_COLOR = 0xE3_B75D;
	/** Dark gold — readable on the beige value-editor chatbox. */
	static final int VALUE_TEXT_COLOR = 0x99_5C00;
	/** Hover feedback on the clickable text (Copilot behavior). */
	private static final int HOVER_COLOR = 0xFF_FFFF;

	// ── search row geometry (mirrors the native "Last search:" row, the
	//    same slot Flipping Copilot uses) ─────────────────────────────────────
	/** Left edge of the row content — the native row's own indent. */
	static final int SEARCH_ICON_X = 114;
	static final int SEARCH_ICON_W = 36;
	/** Row height — exactly one native previous-search row. */
	static final int SEARCH_ROW_H = 32;
	/** Text starts right of the icon (4px gap). */
	static final int SEARCH_TEXT_X = SEARCH_ICON_X + SEARCH_ICON_W + 4;
	/** Text width capped so the row ends at the native row's right edge —
	 *  clear of the scrollbar and the "Show last searched" checkbox. */
	static final int SEARCH_TEXT_W = 216;

	// ── value-editor line geometry (proven Flipping-Copilot placement:
	//    an own line under the prompt, never over it) ────────────────────────
	static final int VALUE_LINE_X = 10;
	static final int VALUE_LINE_Y = 40;
	static final int VALUE_LINE_H = 16;
	/** The native prompt ("How many…"/"Set a price…") renders above this
	 *  band; the value line must start at or below it. */
	static final int NATIVE_PROMPT_BAND_H = 36;
	/** Fallback line width when the container reports no size yet. */
	static final int DEFAULT_VALUE_WIDTH = 475;

	private final Client client;
	private Widget icon;
	private Widget text;
	/** Idempotency key: layout kind + text + position + item — anything
	 *  else re-creates the widgets. */
	private String stateKey;

	/** What an update did — surfaced for the plugin's debug diagnostics. */
	enum Result
	{
		/** New hint widgets were created on a parent layer. */
		SHOWN,
		/** The existing widgets are still attached with the same state. */
		KEPT,
		/** No visible parent layer was available; nothing rendered. */
		NO_CONTAINER,
		/** The hint was removed (editor closed / typing / no text). */
		CLEARED
	}

	GeChatboxHint(Client client)
	{
		this.client = client;
	}

	/**
	 * Shows (or keeps) the search row: [item icon] "RuneFlip item: …",
	 * left-aligned in the native previous-search slot of the results area.
	 * {@code belowNativeRow} drops it one row height when the game's own
	 * "Last search:" row occupies the top slot. An {@code itemId <= 0}
	 * falls back to text-only at the same position. Idempotent per tick;
	 * re-creates after any chatbox rebuild.
	 */
	Result showSearchRow(
		String value, int itemId, boolean belowNativeRow, Runnable onUserClick)
	{
		if (value == null || value.isEmpty())
		{
			return clear();
		}
		Widget parent = visible(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
		if (parent == null)
		{
			parent = visible(ComponentID.CHATBOX_CONTAINER);
		}
		if (parent == null)
		{
			forget();
			return Result.NO_CONTAINER;
		}
		int y = searchRowY(belowNativeRow);
		String key = "search|" + value + '|' + itemId + '|' + y
			+ '|' + parent.getId();
		if (key.equals(stateKey) && attached(parent, text)
			&& (itemId <= 0 || attached(parent, icon)))
		{
			return Result.KEPT;
		}
		clear();
		if (itemId > 0)
		{
			Widget ic = parent.createChild(-1, WidgetType.GRAPHIC);
			ic.setItemId(itemId);
			ic.setItemQuantity(1);
			ic.setItemQuantityMode(0);
			ic.setBorderType(1);
			ic.setOriginalX(SEARCH_ICON_X);
			ic.setOriginalY(y);
			ic.setOriginalWidth(SEARCH_ICON_W);
			ic.setOriginalHeight(SEARCH_ROW_H);
			ic.revalidate();
			icon = ic;
		}
		Widget t = parent.createChild(-1, WidgetType.TEXT);
		t.setText(value);
		t.setTextColor(SEARCH_TEXT_COLOR);
		t.setFontId(FontID.VERDANA_11_BOLD);
		t.setTextShadowed(true);
		t.setOriginalX(itemId > 0 ? SEARCH_TEXT_X : SEARCH_ICON_X);
		t.setOriginalY(y);
		t.setOriginalWidth(SEARCH_TEXT_W);
		t.setOriginalHeight(SEARCH_ROW_H);
		t.setXTextAlignment(WidgetTextAlignment.LEFT);
		t.setYTextAlignment(WidgetTextAlignment.CENTER);
		makeClickable(t, onUserClick, SEARCH_TEXT_COLOR);
		t.revalidate();
		text = t;
		stateKey = key;
		logRendered("search row", parent, t);
		return Result.SHOWN;
	}

	/**
	 * Shows (or keeps) the value-editor line on its own left-aligned row
	 * under the qty/price prompt. Idempotent per tick; re-creates after any
	 * chatbox rebuild.
	 */
	Result showValueLine(String value, Runnable onUserClick)
	{
		if (value == null || value.isEmpty())
		{
			return clear();
		}
		Widget parent = visible(ComponentID.CHATBOX_CONTAINER);
		if (parent == null)
		{
			parent = visible(ComponentID.CHATBOX_PARENT);
		}
		if (parent == null)
		{
			forget();
			return Result.NO_CONTAINER;
		}
		int y = valueLineY(parent.getHeight());
		String key = "value|" + value + '|' + y + '|' + parent.getId();
		if (key.equals(stateKey) && attached(parent, text))
		{
			return Result.KEPT;
		}
		clear();
		Widget t = parent.createChild(-1, WidgetType.TEXT);
		t.setText(value);
		t.setTextColor(VALUE_TEXT_COLOR);
		t.setFontId(FontID.VERDANA_11_BOLD);
		t.setTextShadowed(false);
		t.setOriginalX(VALUE_LINE_X);
		t.setOriginalY(y);
		t.setOriginalWidth(valueLineWidth(parent.getWidth()));
		t.setOriginalHeight(VALUE_LINE_H);
		t.setXTextAlignment(WidgetTextAlignment.LEFT);
		t.setYTextAlignment(WidgetTextAlignment.CENTER);
		makeClickable(t, onUserClick, VALUE_TEXT_COLOR);
		t.revalidate();
		text = t;
		stateKey = key;
		logRendered("value line", parent, t);
		return Result.SHOWN;
	}

	/** Hides the current hint (safe on stale/recycled references). */
	Result clear()
	{
		if (icon != null)
		{
			icon.setHidden(true);
		}
		if (text != null)
		{
			text.setHidden(true);
		}
		forget();
		return Result.CLEARED;
	}

	/** Drops references without touching widgets (parent gone/rebuilt). */
	private void forget()
	{
		icon = null;
		text = null;
		stateKey = null;
	}

	private Widget visible(int componentId)
	{
		Widget candidate = client.getWidget(componentId);
		return candidate != null && !candidate.isHidden() ? candidate : null;
	}

	/**
	 * Whether OUR child is still the one sitting in the parent's dynamic
	 * child slot. After the game rebuilds the chatbox, the slot is empty or
	 * re-used by a different widget — the stale reference must not be
	 * trusted, even when it still carries the old text and is not hidden.
	 */
	private static boolean attached(Widget parent, Widget child)
	{
		return child != null && !child.isHidden()
			&& parent.getChild(child.getIndex()) == child;
	}

	/** User-click only: a widget-op callback on OUR OWN hint text, plus the
	 *  Copilot-style hover feedback that signals it is clickable. */
	private static void makeClickable(
		Widget widget, Runnable onUserClick, int restColor)
	{
		if (onUserClick == null)
		{
			return;
		}
		widget.setAction(0, "Use");
		widget.setHasListener(true);
		widget.setOnOpListener((JavaScriptCallback) ev -> onUserClick.run());
		widget.setOnMouseRepeatListener(
			(JavaScriptCallback) ev -> widget.setTextColor(HOVER_COLOR));
		widget.setOnMouseLeaveListener(
			(JavaScriptCallback) ev -> widget.setTextColor(restColor));
	}

	private static void logRendered(String kind, Widget parent, Widget t)
	{
		if (log.isDebugEnabled())
		{
			java.awt.Rectangle b = t.getBounds();
			log.debug("RuneFlip GE hint {} rendered on {} (index {}) bounds={}",
				kind, parent.getId(), t.getIndex(),
				b == null ? "?" : b.x + "," + b.y + " " + b.width + "x" + b.height);
		}
	}

	// ── pure geometry (unit-tested) ─────────────────────────────────────────

	/** Search row y inside the results area: the top slot, or one native
	 *  row height lower when the game's "Last search:" row is showing. */
	static int searchRowY(boolean belowNativeRow)
	{
		return belowNativeRow ? SEARCH_ROW_H : 0;
	}

	/** Value line y: the own-line slot under the prompt when it fits,
	 *  clamped onto the container's visible area when it would not. */
	static int valueLineY(int containerHeight)
	{
		if (containerHeight <= 0)
		{
			return VALUE_LINE_Y;
		}
		if (VALUE_LINE_Y + VALUE_LINE_H <= containerHeight)
		{
			return VALUE_LINE_Y;
		}
		return Math.max(0, containerHeight - VALUE_LINE_H);
	}

	/** Value line width: container minus symmetric margins, classic
	 *  chatbox width if the container has no size yet. */
	static int valueLineWidth(int containerWidth)
	{
		return containerWidth > 0
			? Math.max(0, containerWidth - 2 * VALUE_LINE_X)
			: DEFAULT_VALUE_WIDTH;
	}
}
