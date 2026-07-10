package com.runeflip.companion;

/**
 * Context-aware GE item detection (v0.8.4) — pure, side-effect-free helpers over
 * the ONE read-only signal the panel uses to know which item the user is setting
 * up in the Grand Exchange: VarPlayer {@value #GE_CURRENT_ITEM_VARP} (the item
 * currently open in the Buy/Sell offer screen).
 *
 * <p>COMPLIANCE: reading a VarPlayer value with {@code Client.getVarpValue(int)}
 * is a pure observation of game state the RuneLite API already exposes — no OCR,
 * no screen scraping, no synthetic input, no widget mutation. This class holds
 * only the numeric var id and the mapping logic so it can be unit-tested without
 * a live client; the single read happens in the plugin, on the client thread.
 * The var id is used directly (not via an API enum constant) so the detection
 * keeps compiling across RuneLite releases.
 */
public final class GeItemSelection
{
	/**
	 * VarPlayer id of the item currently set up in the GE offer screen. It holds
	 * the itemId while the Buy/Sell setup interface is open and a non-positive
	 * value (typically -1) otherwise.
	 */
	public static final int GE_CURRENT_ITEM_VARP = 1151;

	private GeItemSelection()
	{
	}

	/**
	 * Maps the raw VarPlayer value to a selected itemId, or {@code -1} when no
	 * item is being set up. Any non-positive raw value means "no selection".
	 */
	public static int selectedItemId(int varpValue)
	{
		return varpValue > 0 ? varpValue : -1;
	}

	/** Whether an item is currently open in the GE Buy/Sell setup. */
	public static boolean hasSelection(int varpValue)
	{
		return selectedItemId(varpValue) > 0;
	}

	/**
	 * Whether the selection changed between two raw var reads (both normalized
	 * through {@link #selectedItemId}, so -1 ↔ 0 is not a spurious change). Lets
	 * the plugin skip work on the frequent var updates that don't move the GE
	 * item.
	 */
	public static boolean changed(int previousVarpValue, int currentVarpValue)
	{
		return selectedItemId(previousVarpValue) != selectedItemId(currentVarpValue);
	}

	/**
	 * Maps the read-only GE_OFFER_CREATION_TYPE varbit (0 = buy setup,
	 * 1 = sell setup) to the API's side param (v0.8.12). Pure observation —
	 * the same safe signal family as the item varp; anything unexpected maps
	 * to BUY, the pre-v0.8.12 default.
	 */
	public static String sideOf(int geOfferCreationType)
	{
		return geOfferCreationType == 1 ? "SELL" : "BUY";
	}
}
