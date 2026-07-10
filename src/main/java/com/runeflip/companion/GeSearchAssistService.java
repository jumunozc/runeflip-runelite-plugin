package com.runeflip.companion;

import net.runelite.api.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * COMPLIANCE — Clickable GE search row (v0.8.17). This class is the ONLY
 * place in the plugin allowed to run the GE search SELECT script, and the
 * ComplianceScanTest enforces exactly that: any other {@code runScript}
 * here, or a select anywhere else, fails the build.
 *
 * <p>Official rule: <b>RuneFlip can prepare GE fields after explicit user
 * action, but must never submit or execute the offer.</b> Selecting the
 * suggested item from the open GE search is a field PREPARE: it loads the
 * item into the offer setup — exactly what clicking a native search result
 * or the game's own "Last search:" row does — and nothing more. Price and
 * quantity are never set by this flow, and no offer is ever confirmed,
 * cancelled, aborted or collected (those APIs are forbidden project-wide).
 *
 * <p>The one script this service may run is the game's own previous-search
 * SELECT handler — the same script id + argument pair the native
 * "Last search:" row dispatches on the user's click, verified against the
 * Flipping Copilot reference implementation (Plugin Hub, years in
 * production: {@code setOnOpListener(754, itemId, 84)}). Running it with an
 * item id while the search is open selects that item into the offer setup;
 * it has no confirm/cancel/collect path.
 *
 * <p>Guardrails, all enforced by {@link GeFieldAssist#canSelectSearchItem}
 * AT CLICK TIME (stronger than static listener args — the state is
 * re-validated the moment the user clicks):
 * <ul>
 *   <li><b>USER_CLICK only.</b> The user's own click on the RuneFlip row.
 *       Even {@code USER_HOTKEY} is rejected — the hotkey keeps its
 *       prepare-search-text semantics; {@code AUTOMATIC} (timers, events,
 *       background code) is rejected as everywhere else.</li>
 *   <li><b>GE search open, verified.</b> Wrong state = no-op.</li>
 *   <li><b>#1 primary suggestion only.</b> The clicked item id must equal
 *       the #1 primary's id; #2/#3 of the Top 3 can never activate the
 *       search assist.</li>
 * </ul>
 *
 * <p>Must be called on the client thread (widget-op {@code onClick}
 * callbacks already are).
 */
class GeSearchAssistService
{
	private static final Logger log =
		LoggerFactory.getLogger(GeSearchAssistService.class);

	/**
	 * The game's GE previous-search SELECT script — what the native
	 * "Last search:" row runs on the user's click (Flipping Copilot ships
	 * the identical id). The only script this service may ever run; the
	 * ComplianceScanTest pins it.
	 */
	static final int GE_SEARCH_SELECT_SCRIPT = 754;

	/** Second script argument the native row dispatches with (its source
	 *  widget marker; copied verbatim from the verified reference). */
	static final int GE_SEARCH_SELECT_ARG = 84;

	private final Client client;
	private final GeFieldAssistService fieldAssist;

	GeSearchAssistService(Client client, GeFieldAssistService fieldAssist)
	{
		this.client = client;
		this.fieldAssist = fieldAssist;
	}

	/**
	 * Selects the #1 primary suggestion into the open GE search — the
	 * user's own click on the "RuneFlip item: …" row, re-validated at
	 * click time. Anything short of the full gate (USER_CLICK + search
	 * open + valid id + id == #1 primary) is a no-op. Never sets
	 * price/quantity, never confirms.
	 */
	boolean selectPrimaryItem(
		RuneFlipData.FastFlipItem primary,
		int itemId,
		GeFieldAssist.ActionSource source)
	{
		if (primary == null || !GeFieldAssist.canSelectSearchItem(
			source, fieldAssist.isItemSearchOpen(), itemId, primary.itemId))
		{
			return false;
		}
		// Safe diagnostic: editor + item id only — never a token/client id.
		log.debug("RuneFlip GE search select: item={} (user click)", itemId);
		client.runScript(GE_SEARCH_SELECT_SCRIPT, itemId, GE_SEARCH_SELECT_ARG);
		return true;
	}
}
