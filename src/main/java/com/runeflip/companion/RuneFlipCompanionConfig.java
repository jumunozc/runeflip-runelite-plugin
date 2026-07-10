package com.runeflip.companion;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/**
 * Companion settings. The token is a LOCAL RuneFlip secret (the value of
 * OSRS_GE_INGEST_TOKEN in the backend's apps/api/.env) — never a Jagex or
 * RuneLite credential. See the dashboard's /settings page for the values.
 */
@ConfigGroup(RuneFlipCompanionConfig.GROUP)
public interface RuneFlipCompanionConfig extends Config
{
	String GROUP = "runeflipcompanion";

	@ConfigItem(
		keyName = "backendUrl",
		name = "Backend URL",
		description = "RuneFlip API base URL. Defaults to the public RuneFlip service; point it at your own self-hosted backend (e.g. http://localhost:3005/api) if you run one.",
		position = 1
	)
	default String backendUrl()
	{
		return "https://runeflip-api.onrender.com/api";
	}

	@ConfigItem(
		keyName = "ingestToken",
		name = "Ingest token",
		description = "Filled automatically when you pair from the sidebar panel (v0.6.3). Self-hosted backends can still paste OSRS_GE_INGEST_TOKEN manually. NOT a Jagex credential.",
		secret = true,
		position = 2
	)
	default String ingestToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "syncEnabled",
		name = "Sync enabled",
		description = "Send one-way GE slot snapshots to the RuneFlip backend (observation only)",
		position = 3
	)
	default boolean syncEnabled()
	{
		return true;
	}

	@Range(min = 30)
	@ConfigItem(
		keyName = "heartbeatSeconds",
		name = "Heartbeat (seconds)",
		description = "How often to check the GE slots even without activity (minimum 30s). Unchanged snapshots are skipped.",
		position = 4
	)
	default int heartbeatSeconds()
	{
		return 60;
	}

	@Range(min = 1)
	@ConfigItem(
		keyName = "keepaliveMinutes",
		name = "Keepalive (minutes)",
		description = "Re-send an unchanged snapshot after this many minutes so the dashboard's freshness stays accurate (minimum 1)",
		position = 5
	)
	default int keepaliveMinutes()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "capitalSyncEnabled",
		name = "Capital sync (observation)",
		description = "Also send inventory coins, and bank coins as last seen when YOU open the bank. Observation only — nothing is ever acted on.",
		position = 6
	)
	default boolean capitalSyncEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "panelEnabled",
		name = "Sidebar panel",
		description = "Show the informational RuneFlip panel in the sidebar (recommendations, capital, completed offers — display only)",
		position = 7
	)
	default boolean panelEnabled()
	{
		return true;
	}

	/**
	 * LEGACY (v0.8.3 → retired in v0.8.10). The clipboard-only Copy price/qty
	 * buttons this flag used to gate were removed: the game client accepts no
	 * paste, so in real play the buttons assisted nothing. The key is kept
	 * (hidden) ONLY so existing installs' saved config does not break — the
	 * value is read by nothing and turning it on revives no button. The
	 * panel's assistance is now the display-only "GE suggestion" chip on the
	 * #1 Fast Flip row. See docs/anti-bot-compliance.md.
	 */
	@ConfigItem(
		keyName = "enableAssistedOfferSetup",
		name = "Assisted offer setup (retired)",
		description = "Retired in v0.8.10: the clipboard Copy price/qty buttons were removed (the game accepts no paste). This setting no longer does anything and is kept only for config compatibility.",
		hidden = true,
		position = 9
	)
	default boolean enableAssistedOfferSetup()
	{
		return false;
	}

	/**
	 * Context-aware GE panel (v0.8.4), ON by default. When enabled and an item
	 * is open in the GE Buy/Sell setup, the sidebar swaps its Top-3 list for that
	 * item's RuneFlip context (wiki vs target comparison, action, ROI/profit/qty).
	 * It reads ONLY the read-only "current GE item" VarPlayer to know which item
	 * is selected — no OCR, no screen scraping, no input. Display-only.
	 */
	@ConfigItem(
		keyName = "contextualGePanel",
		name = "Context-aware GE panel",
		description = "When you open an item in the GE Buy/Sell setup, show that item's RuneFlip context (wiki vs target, action, ROI) instead of the Top 3 list. Reads only the selected item id — never OCR, screen scraping or input. Display-only.",
		position = 12
	)
	default boolean contextualGePanel()
	{
		return true;
	}

	/**
	 * Explicit GE Field Assist (v0.8.11), ON by default. Adds "RuneFlip: …"
	 * right-click menu OPTIONS while a GE editor is open: select the #1
	 * suggestion in the item search, or set the open item's suggested
	 * quantity/price in the chatbox editor. Rule: RuneFlip can prepare GE
	 * fields after explicit user action, but must never submit or execute
	 * the offer — every option only acts on the user's own click, the write
	 * is a pending-input prepare ({@link GeFieldAssistService}), and nothing
	 * ever confirms, cancels or collects.
	 */
	@ConfigItem(
		keyName = "enableGeFieldAssist",
		name = "GE field assist",
		description = "Add right-click 'RuneFlip: select/set qty/set price' options while a GE editor is open. Prepares the typed value only, after your explicit click — you still press Enter and confirm every offer yourself. Never submits, cancels or collects.",
		position = 15
	)
	default boolean enableGeFieldAssist()
	{
		return true;
	}

	@Range(min = 30)
	@ConfigItem(
		keyName = "panelRefreshSeconds",
		name = "Panel refresh (seconds)",
		description = "How often the sidebar panel re-fetches from the backend while open (minimum 30s)",
		position = 8
	)
	default int panelRefreshSeconds()
	{
		return 60;
	}

	/**
	 * StrategyPill timeframe override (v0.8.7 design), set by clicking the
	 * 5m/30m/2h/8h pills in the sidebar. 0 = no override (saved preferences or
	 * the backend default apply). Display preference only: it changes which
	 * read-only fetch the panel makes — never an action, never written to the
	 * backend's saved preferences.
	 */
	@ConfigItem(
		keyName = "strategyTimeframeMinutes",
		name = "Strategy timeframe (pill)",
		description = "Timeframe selected in the sidebar pills (0 = saved preferences / backend default). Display preference only.",
		hidden = true,
		position = 13
	)
	default int strategyTimeframeMinutes()
	{
		return 0;
	}

	/**
	 * StrategyPill risk override (v0.8.7 design), set by clicking the
	 * Low/Med/High pills. Empty = no override. Same display-only contract as
	 * the timeframe override.
	 */
	@ConfigItem(
		keyName = "strategyRiskLevel",
		name = "Strategy risk (pill)",
		description = "Risk grade selected in the sidebar pills (empty = saved preferences / backend default). Display preference only.",
		hidden = true,
		position = 14
	)
	default String strategyRiskLevel()
	{
		return "";
	}

	/**
	 * Anonymous client id (v0.6.1): a stable UUID generated once per install
	 * and sent as X-RuneFlip-Client-Id so the backend keeps this install's
	 * data separate from other users. Isolation, not authentication — it is
	 * not a secret and not a Jagex/RuneLite credential.
	 */
	@ConfigItem(
		keyName = "clientId",
		name = "Client id",
		description = "Anonymous id that keeps your RuneFlip data separate from other users (generated automatically)",
		hidden = true,
		position = 10
	)
	default String clientId()
	{
		return "";
	}

	/**
	 * When pairing last completed (v0.6.3), ISO-8601. Empty = not paired.
	 * Drives the Paired / Not paired label; the token itself is never
	 * displayed anywhere.
	 */
	@ConfigItem(
		keyName = "pairedAt",
		name = "Paired at",
		description = "Set automatically when pairing completes (informational)",
		hidden = true,
		position = 11
	)
	default String pairedAt()
	{
		return "";
	}
}
