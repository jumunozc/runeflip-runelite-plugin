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
		position = 9
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
		position = 10
	)
	default String pairedAt()
	{
		return "";
	}
}
