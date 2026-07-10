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
 * Visible GE chatbox hint (v0.8.13; Copilot-style layout since v0.8.16;
 * clickable search row + top-left value line since v0.8.17). Two layouts:
 *
 * <ul>
 *   <li><b>Search row</b> — while the GE item search is open and still
 *       EMPTY, one functional row renders in the previous-search zone of
 *       the results area: a full-row click surface (hover-highlighted,
 *       menu action "Select"), a small ITEM ICON and "RuneFlip item: …"
 *       left-aligned next to it — the same anatomy as the game's own
 *       "Last search:" row. When the native last-search row is showing,
 *       the RuneFlip row sits one row height BELOW it on its own line.
 *       Once the user types, real results own the area and the row is
 *       removed — it never overlaps them, the scrollbar or the "Show last
 *       searched" checkbox (the row ends at the native content's right
 *       edge).</li>
 *   <li><b>Value line</b> — on the qty/price editors, one left-aligned
 *       line ("Press [key] to set RuneFlip price/quantity: …") at the
 *       TOP-LEFT of the chatbox, in the same left column as the flipping
 *       tracker lines ("no buy tracked" / "set to wiki insta buy: …") and
 *       directly below their two slots — never centered, never over the
 *       "Set a price…"/"How many…" prompt, the typed input or those
 *       existing red lines.</li>
 * </ul>
 *
 * <p>Rebuild-safe: attachment to the parent's real child slot is verified
 * every game tick (the GE search rebuilds the chatbox and silently wipes
 * dynamic children), and bounds are computed from the actual parent so the
 * hint always lands inside the visible area. Closing the editor removes the
 * hint.
 *
 * <p>COMPLIANCE: this adds presentational child widgets of its own to a
 * chatbox layer (the standard overlay technique of Plugin Hub flipping
 * tools). It reads and mutates nothing of the game's own widgets and
 * performs no input. Click listeners are RuneLite widget-op callbacks that
 * only run when the USER clicks the hint itself, and both routes stay
 * gated at click time: the search row routes to
 * {@link GeSearchAssistService} (select the #1 primary into the open
 * search — never price/qty, never confirm), the value line to
 * {@link GeFieldAssistService} (prepare the pending input — never submit).
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
	/** Full-row click surface width — the native row content span
	 *  (x=114..370), identical to the reference implementation. */
	static final int SEARCH_ROW_W = 256;

	// ── value-editor line geometry (v0.8.17: TOP-LEFT column, the slot
	//    directly below the flipping tracker lines) ─────────────────────────
	static final int VALUE_LINE_X = 10;
	static final int VALUE_LINE_Y = 34;
	static final int VALUE_LINE_H = 13;
	/** The two tracker lines ("no buy tracked" / "set to wiki insta
	 *  buy: …") occupy the left column's y=5 and y=20 slots; their text
	 *  band ends here — the value line starts at or below it. */
	static final int TRACKER_LINES_BOTTOM = 34;
	/** The native centered prompt ("How many…"/"Set a price…") starts
	 *  around this y — the value line must end above it. */
	static final int NATIVE_PROMPT_TOP = 47;
	/** Fallback line width when the container reports no size yet. */
	static final int DEFAULT_VALUE_WIDTH = 475;

	private final Client client;
	private Widget rowSurface;
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
	 * Shows (or keeps) the functional search row: a full-row click surface
	 * (menu action "Select", hover highlight — the native previous-search
	 * anatomy) with the [item icon] and "RuneFlip item: …" left-aligned on
	 * it. {@code belowNativeRow} drops it one row height when the game's
	 * own "Last search:" row occupies the top slot, so the two never
	 * overlap. An {@code itemId <= 0} falls back to text-only at the same
	 * position. {@code onUserClick} runs ONLY on the user's own click on
	 * the row — the plugin routes it to the click-time-gated
	 * {@link GeSearchAssistService}. Idempotent per tick; re-creates after
	 * any chatbox rebuild.
	 */
	Result showSearchRow(
		String value, String menuTarget, int itemId, boolean belowNativeRow,
		Runnable onUserClick)
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
			&& attached(parent, rowSurface)
			&& (itemId <= 0 || attached(parent, icon)))
		{
			return Result.KEPT;
		}
		clear();

		// Full-row click surface first (lowest z), exactly the native row
		// span: invisible at rest, faint highlight on hover, one "Select"
		// menu op that runs only on the user's own click.
		Widget row = parent.createChild(-1, WidgetType.RECTANGLE);
		row.setFilled(true);
		row.setTextColor(0xFF_FFFF);
		row.setOpacity(255);
		row.setOriginalX(SEARCH_ICON_X);
		row.setOriginalY(y);
		row.setOriginalWidth(SEARCH_ROW_W);
		row.setOriginalHeight(SEARCH_ROW_H);
		if (onUserClick != null)
		{
			row.setName(menuTarget == null || menuTarget.isEmpty()
				? "" : "<col=ff9040>" + menuTarget + "</col>");
			row.setAction(0, "Select");
			row.setHasListener(true);
			row.setOnOpListener((JavaScriptCallback) ev -> onUserClick.run());
			row.setOnMouseRepeatListener(
				(JavaScriptCallback) ev -> row.setOpacity(200));
			row.setOnMouseLeaveListener(
				(JavaScriptCallback) ev -> row.setOpacity(255));
		}
		row.revalidate();
		rowSurface = row;

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
		t.revalidate();
		text = t;
		stateKey = key;
		logRendered("search row", parent, t);
		return Result.SHOWN;
	}

	/**
	 * Shows (or keeps) the value-editor line at the TOP-LEFT of the
	 * chatbox — the left-column slot directly below the flipping tracker
	 * lines ("no buy tracked" / "set to wiki insta buy: …"), left-aligned
	 * and never centered, clear of the native prompt, the typed input and
	 * those existing lines. Idempotent per tick; re-creates after any
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
		t.setYTextAlignment(WidgetTextAlignment.TOP);
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
		if (rowSurface != null)
		{
			rowSurface.setHidden(true);
		}
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
		rowSurface = null;
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

	/** Value line y: the top-left column slot directly below the tracker
	 *  lines and above the native prompt, clamped onto the container's
	 *  visible area when it would not fit. */
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
