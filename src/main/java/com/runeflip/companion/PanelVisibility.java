package com.runeflip.companion;

/**
 * Contextual panel visibility rules (v0.8.5) — pure, side-effect-free logic for
 * which sidebar sections show, given the {@code contextualGePanel} config and
 * whether an item is currently open in the GE Buy/Sell setup. Kept separate from
 * the Swing panel so the focus rules can be unit-tested without a live client.
 *
 * <p>Contextual mode (config ON) is FOCUSED: it shows exactly one of the
 * selected-item context card (an item is open) or the Top 3 Fast Flips (none
 * open), plus the always-present header / capital / pairing / footer. It hides
 * the legacy dashboard (top recommendation + recommendation list) and the GE
 * completed summary. Legacy mode (config OFF) keeps the full pre-v0.8.4 panel.
 */
public final class PanelVisibility
{
	private PanelVisibility()
	{
	}

	/** Legacy dashboard (top recommendation card + recommendation list): shown
	 *  only in legacy mode. */
	public static boolean showLegacyDashboard(boolean contextual)
	{
		return !contextual;
	}

	/** GE completed summary: shown only in legacy mode. Contextual mode hides
	 *  it (the backend and its events are untouched — display only). */
	public static boolean showGeCompleted(boolean contextual)
	{
		return !contextual;
	}

	/** Selected-item context card: only in contextual mode with an item open. */
	public static boolean showSelectedItem(boolean contextual, boolean hasSelection)
	{
		return contextual && hasSelection;
	}

	/**
	 * Top 3 Fast Flips: shown whenever the selected-item card is NOT — i.e. in
	 * legacy mode (always) and in contextual mode when no item is open. Hidden
	 * only in contextual mode while an item is open (the context card takes its
	 * place).
	 */
	public static boolean showTopThree(boolean contextual, boolean hasSelection)
	{
		return !showSelectedItem(contextual, hasSelection);
	}

	/**
	 * StrategyPill (v0.8.7 design, state 1a): the timeframe/risk pills show only
	 * in contextual mode with no item open — the selected-item card (1b) is
	 * about ONE item, and legacy mode keeps its pre-design panel.
	 */
	public static boolean showStrategyPill(boolean contextual, boolean hasSelection)
	{
		return contextual && !hasSelection;
	}

	/**
	 * SessionPanel, full KPI block (v0.8.7 design, states 1a/1c): contextual
	 * mode with no item open. The design shows it under the Top 3 and under the
	 * empty state alike ("Session si hay data" — the has-data gate is the
	 * caller's, this is pure placement).
	 */
	public static boolean showSessionFull(boolean contextual, boolean hasSelection)
	{
		return contextual && !hasSelection;
	}

	/**
	 * SessionPanel, collapsed one-liner (v0.8.7 design, state 1b): contextual
	 * mode while an item is open — the selected-item card keeps the focus, the
	 * session stays one glanceable row.
	 */
	public static boolean showSessionCompact(boolean contextual, boolean hasSelection)
	{
		return contextual && hasSelection;
	}
}
