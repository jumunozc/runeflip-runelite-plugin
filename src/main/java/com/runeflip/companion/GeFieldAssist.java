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
 *       the user actually has open: the selected-item context, or the #1
 *       primary suggestion when that is the open item. #2/#3 of the Top 3
 *       can never feed an assist (they are informational only).</li>
 * </ul>
 */
final class GeFieldAssist
{
	private GeFieldAssist()
	{
	}

	/** How a prepare call was initiated. Only USER_CLICK may ever write. */
	enum ActionSource
	{
		/** The user explicitly clicked a "RuneFlip: …" option. */
		USER_CLICK,
		/** Anything else — timers, events, background code. Always rejected. */
		AUTOMATIC
	}

	/** Which GE chatbox editor a prompt belongs to. */
	enum Field
	{
		QUANTITY,
		PRICE,
		NONE
	}

	/** Shown (chat + panel) right after a field was prepared. */
	static final String PREPARED_MESSAGE =
		"RuneFlip prepared the value. Review manually.";

	/**
	 * The single gate every prepare call passes: an explicit user click AND a
	 * valid, currently-open GE editor. A background/automatic source or a
	 * missing editor rejects the write — RuneFlip never acts without human
	 * input and never writes into the wrong context.
	 */
	static boolean canPrepare(ActionSource source, boolean editorValid)
	{
		return source == ActionSource.USER_CLICK && editorValid;
	}

	/**
	 * Classifies the GE chatbox prompt ("How many do you wish to buy?" /
	 * "Set a price for each item:") into the field being edited. Unknown or
	 * missing prompts yield {@link Field#NONE} — no assist is offered for an
	 * editor we cannot positively identify.
	 */
	static Field fieldForPrompt(String chatboxTitle)
	{
		if (chatboxTitle == null)
		{
			return Field.NONE;
		}
		String title = chatboxTitle.toLowerCase();
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
	 * Quantity to offer for the item the user has OPEN in the GE setup, or
	 * null when there is nothing RuneFlip stands behind. Sources, in order:
	 * the selected-item context (when it is for the open item), then the #1
	 * primary suggestion (when the open item IS the #1). Any other item —
	 * including #2/#3 of the Top 3 — gets no assist.
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
	 * the #1 primary suggestion when it is the open item; never #2/#3.
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
}
