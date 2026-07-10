package com.runeflip.companion;

/**
 * Selected-item stability (v0.8.14) — pure decisions that keep the selected
 * GE item card from flashing back to the Top 3.
 *
 * <p>Two real-play flickers motivated this: (1) the current-GE-item VarPlayer
 * briefly drops to -1 while the client transitions between the Buy and Sell
 * setup (or through the search), which used to clear the card instantly;
 * (2) a failed context fetch used to clear it too. The rule now: a VALID
 * item id always wins over the Top 3; a 0/UNKNOWN id starts a short GRACE
 * period during which the last card (or its loading state) stays — only a
 * selection still empty after the grace clears back to the Top 3. Display
 * bookkeeping only; nothing here touches the game.
 */
final class SelectedItemStability
{
	/** How long a 0/UNKNOWN selection may persist before the panel returns
	 *  to the Top 3 (covers the buy↔sell / search transition flicker). */
	static final long GRACE_MS = 800;

	private SelectedItemStability()
	{
	}

	/**
	 * Next value of the pending-clear deadline after one selection read.
	 * A valid item cancels any pending clear (0 = none); the FIRST empty
	 * read arms the deadline once — later empty reads keep the original
	 * deadline so repeated flickers cannot postpone the clear forever.
	 */
	static long clearDueAfter(int itemId, long nowMs, long currentDueMs)
	{
		if (itemId > 0)
		{
			return 0;
		}
		return currentDueMs != 0 ? currentDueMs : nowMs + GRACE_MS;
	}

	/** Whether the panel should NOW fall back to the Top 3: the selection is
	 *  still empty and the armed grace deadline has passed. */
	static boolean shouldClear(int itemId, long nowMs, long dueMs)
	{
		return itemId <= 0 && dueMs != 0 && nowMs >= dueMs;
	}

	/** Whether the selected view must be kept (valid item, or inside the
	 *  grace window of an empty read). */
	static boolean keepSelectedView(int itemId, long nowMs, long dueMs)
	{
		return itemId > 0 || (dueMs != 0 && nowMs < dueMs);
	}
}
