package com.runeflip.companion;

/**
 * Pure decision logic for the Explicit GE Field Assist (v0.8.11) — no
 * RuneLite types, fully unit-tested. It answers WHAT may be offered (which
 * field, which value, which label) given the current GE state; the actual
 * client write lives exclusively in {@link GeFieldAssistService}.
 *
 * <p>Official rule (v0.8.11): <b>RuneFlip can prepare GE fields after
 * explicit user action, but must never submit or execute the offer.</b>
 * Everything here enforces the "explicit" and "right context" halves:
 * <ul>
 *   <li>{@link #canPrepare} — only a {@link ActionSource#USER_CLICK} in a
 *       valid GE editor may proceed; anything automatic/background is
 *       rejected before a value is even resolved.</li>
 *   <li>{@link #qtyFor}/{@link #priceFor} — values exist ONLY for the item
 *       the user actually has open: the selected-item context, or the
 *       selected GE suggestion when that IS the open item. A panel
 *       selection for a DIFFERENT item than the open one never feeds a
 *       qty/price assist (v0.8.18) — no other suggestion row ever can.</li>
 * </ul>
 */
final class GeFieldAssist
{
	private GeFieldAssist()
	{
	}

	/** How a prepare call was initiated. Only the user's OWN explicit input
	 *  (a click on a RuneFlip option/hint, or the assist hotkey pressed by
	 *  the user) may ever write. */
	enum ActionSource
	{
		/** The user explicitly clicked a "RuneFlip: …" option or hint. */
		USER_CLICK,
		/** The user pressed the configured assist hotkey (v0.8.13). Listened
		 *  through RuneLite's KeyManager — the user's own physical key press,
		 *  never a synthesized one. */
		USER_HOTKEY,
		/** Anything else — timers, events, background code. Always rejected. */
		AUTOMATIC
	}

	/** Which GE editor is active. */
	enum Field
	{
		ITEM_SEARCH,
		QUANTITY,
		PRICE,
		NONE
	}

	/** Shown (chat + panel) right after a field was prepared. */
	static final String PREPARED_MESSAGE =
		"RuneFlip prepared the value. Review manually.";

	/**
	 * The single gate every prepare call passes: the user's OWN explicit
	 * input (click or hotkey) AND a valid, currently-open GE editor. A
	 * background/automatic source or a missing editor rejects the write —
	 * RuneFlip never acts without human input and never writes into the
	 * wrong context.
	 */
	static boolean canPrepare(ActionSource source, boolean editorValid)
	{
		return (source == ActionSource.USER_CLICK
			|| source == ActionSource.USER_HOTKEY) && editorValid;
	}

	/**
	 * Gate for the GE search select (v0.8.17, selectable rows v0.8.18) — the
	 * ONLY path that may select an item into the GE search, and the
	 * strictest gate in the plugin. Every condition must hold:
	 * <ul>
	 *   <li>{@link ActionSource#USER_CLICK} only — the user's own click on
	 *       the in-game "RuneFlip item:" row or on a VISIBLE side-panel
	 *       suggestion row. Unlike the field prepares, even the hotkey is
	 *       rejected here (the hotkey keeps its prepare-search-TEXT
	 *       semantics); AUTOMATIC/background is rejected as always.</li>
	 *   <li>the GE item search must be open right now;</li>
	 *   <li>the item id must be valid;</li>
	 *   <li>the item must BE the selected GE suggestion — the row the user
	 *       explicitly picked (or the default first row of the panel's
	 *       current page). Any other item — hidden pages and unloaded items
	 *       included — can never activate the search assist.</li>
	 * </ul>
	 * Selecting only loads the item into the offer setup — price/quantity
	 * are never set by this flow and the offer is never confirmed.
	 */
	static boolean canSelectSearchItem(
		ActionSource source, boolean searchOpen, int itemId, int selectedItemId)
	{
		return source == ActionSource.USER_CLICK
			&& searchOpen
			&& itemId > 0
			&& itemId == selectedItemId;
	}

	/**
	 * Classifies the GE chatbox prompt into the editor being shown. The real
	 * prompts (v0.8.13, +search helper in v0.8.14): "What would you like to
	 * buy?" / "What would you like to sell?" and the helper line "Start
	 * typing the name of an item to search for it." (item search), "Set a
	 * price for each item:" (price), "How many do you wish to buy?" / "How
	 * many do you wish to sell?" (quantity). Unknown or missing prompts yield
	 * {@link Field#NONE} — no assist is offered for an editor we cannot
	 * positively identify.
	 */
	static Field fieldForPrompt(String chatboxTitle)
	{
		if (chatboxTitle == null)
		{
			return Field.NONE;
		}
		String title = chatboxTitle.toLowerCase();
		if (title.contains("what would you like")
			|| title.contains("start typing the name of an item"))
		{
			return Field.ITEM_SEARCH;
		}
		if (title.contains("how many"))
		{
			return Field.QUANTITY;
		}
		if (title.contains("price"))
		{
			return Field.PRICE;
		}
		return Field.NONE;
	}

	/**
	 * Whether the GE ITEM SEARCH editor is open (v0.8.14 hotfix). The old
	 * detection relied solely on the client reporting the SEARCH meslayer
	 * mode — which the live GE search does not always do, leaving the real
	 * "What would you like to buy?" screen unrecognized (no hint, no menu
	 * option, no hotkey). Detection is now structural; the search is open
	 * when the GE offer setup is showing AND any ONE positive signal holds:
	 * <ul>
	 *   <li>the client reports the SEARCH input mode (the original signal);</li>
	 *   <li>the chatbox search-results layer is showing — the layer that
	 *       carries "Start typing the name of an item to search for it." even
	 *       when it is the only prompt visible;</li>
	 *   <li>the chatbox prompt is one of the EXACT search prompts.</li>
	 * </ul>
	 * No signal → not a search → no assist (unknown editors stay unassisted).
	 * This only ever gates DISPLAY plus the click/hotkey-gated prepare of the
	 * pending search text; nothing is selected, submitted or executed.
	 */
	static boolean searchEditorOpen(
		boolean offerSetupOpen,
		boolean searchModeReported,
		boolean searchResultsShowing,
		String chatboxPrompt)
	{
		return offerSetupOpen
			&& (searchModeReported || searchResultsShowing
				|| fieldForPrompt(chatboxPrompt) == Field.ITEM_SEARCH);
	}

	/**
	 * Resolves WHICH editor is active from the observed chatbox state
	 * (v0.8.14). A value editor with a qty/price prompt is authoritative —
	 * the prompt names the field being edited even if a stale search signal
	 * lingers. Otherwise a detected search wins. Anything else — including a
	 * value editor whose prompt we cannot classify — is {@link Field#NONE}:
	 * no assist for an editor we cannot positively identify.
	 */
	static Field classifyEditor(
		boolean valueEditorOpen, boolean searchOpen, String chatboxPrompt)
	{
		Field byPrompt = fieldForPrompt(chatboxPrompt);
		if (valueEditorOpen
			&& (byPrompt == Field.QUANTITY || byPrompt == Field.PRICE))
		{
			return byPrompt;
		}
		return searchOpen ? Field.ITEM_SEARCH : Field.NONE;
	}

	/**
	 * Quantity to offer for the item the user has OPEN in the GE setup, or
	 * null when there is nothing RuneFlip stands behind. Sources, in order:
	 * the selected-item context (when it is for the open item), then the
	 * selected GE suggestion (when the open item IS that suggestion). Any
	 * other item — a panel selection different from the open item included —
	 * gets no assist.
	 */
	static Long qtyFor(
		int openItemId,
		RuneFlipData.FastFlipItemContextResponse context,
		RuneFlipData.FastFlipItem primary)
	{
		if (openItemId <= 0)
		{
			return null;
		}
		if (context != null && context.itemId == openItemId)
		{
			return positiveOrNull(context.suggestedQuantity);
		}
		if (primary != null && primary.itemId == openItemId)
		{
			Long qty = positiveOrNull(primary.suggestedQuantity);
			if (qty != null)
			{
				return qty;
			}
			return primary.action == null
				? null : positiveOrNull(primary.action.targetQuantity);
		}
		return null;
	}

	/**
	 * Price to offer for the OPEN item, or null. A buy offer uses the
	 * recommended/target BUY leg, a sell offer the SELL leg — never crossed.
	 * Same source rule as {@link #qtyFor}: selected-item context first, then
	 * the selected GE suggestion when it IS the open item; never any other.
	 */
	static Long priceFor(
		int openItemId,
		boolean sellOffer,
		RuneFlipData.FastFlipItemContextResponse context,
		RuneFlipData.FastFlipItem primary)
	{
		if (openItemId <= 0)
		{
			return null;
		}
		if (context != null && context.itemId == openItemId)
		{
			RuneFlipData.TargetComparison tc = context.targetComparison;
			if (tc != null)
			{
				Long target = positiveOrNull(sellOffer ? tc.targetSell : tc.targetBuy);
				if (target != null)
				{
					return target;
				}
			}
			return priceEdgeLeg(context.priceEdge, sellOffer);
		}
		if (primary != null && primary.itemId == openItemId)
		{
			Long recommended = priceEdgeLeg(primary.priceEdge, sellOffer);
			if (recommended != null)
			{
				return recommended;
			}
			return positiveOrNull(sellOffer
				? primary.suggestedSellPrice : primary.suggestedBuyPrice);
		}
		return null;
	}

	/** The recommended buy/sell leg of a Price Edge block, or null. */
	private static Long priceEdgeLeg(RuneFlipData.PriceEdge edge, boolean sellOffer)
	{
		if (edge == null)
		{
			return null;
		}
		return positiveOrNull(sellOffer
			? edge.recommendedSellPrice : edge.recommendedBuyPrice);
	}

	private static Long positiveOrNull(Long value)
	{
		return value != null && value > 0 ? value : null;
	}

	// ── Menu labels (compact, per the v0.8.11 UX spec) ───────────────────────

	static String selectLabel(String itemName)
	{
		return "RuneFlip: select " + RuneFlipPanel.sanitizeName(itemName);
	}

	static String qtyLabel(long qty)
	{
		return "RuneFlip: set qty " + String.format("%,d", qty);
	}

	static String priceLabel(long price)
	{
		return "RuneFlip: set price " + String.format("%,d", price) + " gp";
	}

	// ── Chatbox hints (v0.8.13, Copilot-style visible assist) ────────────────

	/**
	 * The full visible-hint model (v0.8.14): which text — if any — the
	 * chatbox hint shows for the active editor. Search shows the selected
	 * GE suggestion by name (the caller only ever holds that one
	 * suggestion); the value editors show the press-hotkey copy for their
	 * value. A missing value or an unidentified/closed editor returns null,
	 * which REMOVES the hint. Display only — showing a hint performs no
	 * action.
	 */
	static String hintFor(
		Field field, String primaryName, Long qty, Long price, String keyLabel)
	{
		switch (field)
		{
			case ITEM_SEARCH:
				return primaryName == null || primaryName.trim().isEmpty()
					? null : searchHint(primaryName);
			case QUANTITY:
				return qty == null ? null : qtyHint(qty, keyLabel);
			case PRICE:
				return price == null ? null : priceHint(price, keyLabel);
			default:
				return null;
		}
	}

	/** GE search hint: "RuneFlip item: Death rune" — always the selected GE
	 *  suggestion (the user's pick, or the default first row of the panel's
	 *  current page). */
	static String searchHint(String itemName)
	{
		return "RuneFlip item: " + RuneFlipPanel.sanitizeName(itemName);
	}

	/** Price editor hint: "Press [Right Brace] to set RuneFlip price: 1,049
	 *  gp" — the key label comes from the user's configured hotkey. */
	static String priceHint(long price, String keyLabel)
	{
		return "Press [" + keyLabel + "] to set RuneFlip price: "
			+ String.format("%,d", price) + " gp";
	}

	/** Quantity editor hint: "Press [Right Brace] to set RuneFlip quantity:
	 *  250". */
	static String qtyHint(long qty, String keyLabel)
	{
		return "Press [" + keyLabel + "] to set RuneFlip quantity: "
			+ String.format("%,d", qty);
	}
}
