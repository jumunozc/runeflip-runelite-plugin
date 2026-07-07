package com.runeflip.companion;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RuneFlip Companion — a strictly READ-ONLY mirror of your Grand Exchange
 * slots (and, opt-in, passively observed capital), sent one-way to a
 * self-hosted RuneFlip dashboard.
 *
 * What this plugin never does (see docs/runelite-readonly-contract.md):
 * no buying, selling, cancelling or collecting; no clicks, keystrokes or
 * menu actions; no opening of any interface (bank/GE state is only read
 * when the USER opens them); no reading of credentials or session data; no
 * commands received from the backend (the HTTP response is a bare
 * acknowledgement that is only logged). It observes
 * {@link GrandExchangeOffer} and official item containers through the
 * RuneLite API and POSTs JSON snapshots — that is all.
 *
 * Continuous tracking: {@link Client#getGrandExchangeOffers()} reflects the
 * account's GE state while logged in, whether or not the GE interface is
 * open, so the heartbeat mirrors offers even away from the exchange.
 * Snapshots are fingerprinted — unchanged state is not re-POSTed until the
 * keepalive elapses (which keeps the dashboard's freshness meaningful).
 */
@PluginDescriptor(
	name = "RuneFlip Companion",
	description = "Read-only mirror of your GE slots to a self-hosted RuneFlip dashboard. Never performs game actions.",
	tags = {"grand", "exchange", "ge", "flipping", "runeflip"}
)
public class RuneFlipCompanionPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(RuneFlipCompanionPlugin.class);

	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	/** Floor for the heartbeat, matching the config UI minimum. */
	private static final int MIN_HEARTBEAT_SECONDS = 30;
	/** Floor for the keepalive, matching the config UI minimum. */
	private static final int MIN_KEEPALIVE_MINUTES = 1;
	/** Debounce for observation-event bursts (login fires 8 at once). */
	private static final long EVENT_DEBOUNCE_MS = 2_000;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private RuneFlipCompanionConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	private RuneFlipPanel panel;
	private NavigationButton navButton;
	private RuneFlipApiClient apiClient;
	private volatile long lastPanelRefreshMs;
	private volatile long lastPanelOkMs;

	private final AtomicBoolean inFlight = new AtomicBoolean(false);
	private final AtomicBoolean capitalInFlight = new AtomicBoolean(false);
	private volatile long lastAttemptMs;
	private volatile long dirtyAtMs;
	private volatile String lastSyncStatus = "never";
	private volatile String lastSyncAt = "-";
	/** Fingerprint of the last snapshot the backend ACCEPTED. */
	private volatile String lastSentFingerprint;
	private volatile long lastSentOkMs;
	private volatile String lastCapitalFingerprint;
	private volatile long lastCapitalOkMs;
	/** Bank coins as last seen when the USER opened the bank. Never inferred. */
	private volatile Integer bankCoinsLastSeen;
	private volatile String bankLastSeenAt;

	@Provides
	RuneFlipCompanionConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneFlipCompanionConfig.class);
	}

	/**
	 * Stable anonymous client id (v0.6.1), generated ONCE per install and
	 * stored in the plugin config. Sent as X-RuneFlip-Client-Id so the
	 * backend isolates this install's data from other users. Isolation, not
	 * authentication — it is not a secret and never a Jagex credential.
	 */
	private String ensureClientId()
	{
		String id = config.clientId();
		if (id == null || id.trim().isEmpty())
		{
			id = UUID.randomUUID().toString();
			configManager.setConfiguration(
				RuneFlipCompanionConfig.GROUP, "clientId", id);
			log.info("RuneFlip Companion generated a new anonymous client id");
		}
		return id.trim();
	}

	@Override
	protected void startUp()
	{
		ensureClientId();
		apiClient = new RuneFlipApiClient(okHttpClient, gson);
		if (config.panelEnabled())
		{
			panel = new RuneFlipPanel(
				itemManager, this::refreshPanel, pairingActions(), isPaired());
			navButton = NavigationButton.builder()
				.tooltip("RuneFlip")
				.icon(buildNavIcon())
				.priority(7)
				.panel(panel)
				.build();
			clientToolbar.addNavigation(navButton);
			refreshPanel();
		}
		log.info("RuneFlip Companion started (read-only; one-way snapshots only)");
	}

	@Override
	protected void shutDown()
	{
		dirtyAtMs = 0;
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
			panel = null;
		}
		log.info("RuneFlip Companion stopped (last sync: {} at {})", lastSyncStatus, lastSyncAt);
	}

	/** Passive observation only — marks the state dirty, nothing else. */
	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		dirtyAtMs = System.currentTimeMillis();
	}

	/**
	 * Passive observation of official item containers. Bank contents are only
	 * visible after the USER opens the bank — this never opens anything.
	 */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (!config.capitalSyncEnabled())
		{
			return;
		}
		if (event.getContainerId() == InventoryID.BANK.getId())
		{
			ItemContainer bank = event.getItemContainer();
			if (bank != null)
			{
				bankCoinsLastSeen = bank.count(ItemID.COINS_995);
				bankLastSeenAt = Instant.now().toString();
				dirtyAtMs = System.currentTimeMillis();
			}
		}
		else if (event.getContainerId() == InventoryID.INVENTORY.getId())
		{
			dirtyAtMs = System.currentTimeMillis();
		}
	}

	@Schedule(period = 5, unit = ChronoUnit.SECONDS)
	public void tick()
	{
		if (!SyncGate.canSync(config.syncEnabled(), config.backendUrl(), config.ingestToken()))
		{
			return;
		}
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		long now = System.currentTimeMillis();
		long heartbeatMs =
			Math.max(MIN_HEARTBEAT_SECONDS, config.heartbeatSeconds()) * 1_000L;
		boolean heartbeatDue = lastAttemptMs == 0 || now - lastAttemptMs >= heartbeatMs;
		boolean eventDue = dirtyAtMs > 0 && now - dirtyAtMs >= EVENT_DEBOUNCE_MS;
		if (!heartbeatDue && !eventDue)
		{
			return;
		}
		if (!inFlight.compareAndSet(false, true))
		{
			return;
		}

		lastAttemptMs = now;
		dirtyAtMs = 0;
		long keepaliveMs =
			Math.max(MIN_KEEPALIVE_MINUTES, config.keepaliveMinutes()) * 60_000L;

		// Read state on the client thread; ship the POSTs off it.
		clientThread.invoke(() ->
		{
			try
			{
				GeSnapshotPayload payload = buildSnapshot();
				String fingerprint = SnapshotFingerprint.ofSlots(payload.slots);
				boolean changed = !fingerprint.equals(lastSentFingerprint);
				boolean keepaliveDue = now - lastSentOkMs >= keepaliveMs;
				if (changed || keepaliveDue)
				{
					executor.execute(() -> send(payload, fingerprint));
				}
				else
				{
					// Unchanged snapshot inside the keepalive window: skip.
					inFlight.set(false);
				}

				if (config.capitalSyncEnabled())
				{
					maybeSendCapital(now, keepaliveMs);
				}
			}
			catch (RuntimeException e)
			{
				inFlight.set(false);
				log.warn("RuneFlip snapshot build failed: {}", e.getMessage());
			}
		});
	}

	/** Paired = a pairing-issued token is stored (locally) in the config. */
	private boolean isPaired()
	{
		return !config.pairedAt().trim().isEmpty()
			&& !config.ingestToken().trim().isEmpty();
	}

	private RuneFlipPanel.PairingActions pairingActions()
	{
		return new RuneFlipPanel.PairingActions()
		{
			@Override
			public void pair(String code, java.util.function.Consumer<String> onResult)
			{
				completePairing(code, onResult);
			}

			@Override
			public void unpair(java.util.function.Consumer<String> onResult)
			{
				revokePairing(onResult);
			}
		};
	}

	/**
	 * Exchanges the user-pasted code for the paired clientId + scoped token
	 * (v0.6.3) and adopts both in the plugin config. Everything stays local:
	 * config writes + one HTTP call. The token is stored as a secret config
	 * value and NEVER logged or displayed.
	 */
	private void completePairing(String code, java.util.function.Consumer<String> onResult)
	{
		apiClient.completePairing(config.backendUrl(), code,
			response ->
			{
				configManager.setConfiguration(
					RuneFlipCompanionConfig.GROUP, "clientId",
					response.clientId.trim().toLowerCase());
				configManager.setConfiguration(
					RuneFlipCompanionConfig.GROUP, "ingestToken",
					response.token.trim());
				configManager.setConfiguration(
					RuneFlipCompanionConfig.GROUP, "pairedAt",
					Instant.now().toString());
				// New identity: force a fresh snapshot/capital send.
				lastSentFingerprint = null;
				lastCapitalFingerprint = null;
				log.info("RuneFlip Companion paired — adopted the dashboard's client id");
				SwingUtilities.invokeLater(() ->
				{
					RuneFlipPanel target = panel;
					if (target != null)
					{
						target.setPaired(true);
					}
					onResult.accept("Paired. GE slot sync is now connected.");
				});
				refreshPanel();
			},
			message -> SwingUtilities.invokeLater(() -> onResult.accept(message)));
	}

	/**
	 * Unpair: best-effort server-side revoke, then the token is removed from
	 * the local config regardless. The clientId is kept so the informational
	 * panel keeps showing this install's data.
	 */
	private void revokePairing(java.util.function.Consumer<String> onResult)
	{
		String token = config.ingestToken().trim();
		Runnable clearLocal = () ->
		{
			configManager.setConfiguration(
				RuneFlipCompanionConfig.GROUP, "ingestToken", "");
			configManager.setConfiguration(
				RuneFlipCompanionConfig.GROUP, "pairedAt", "");
			log.info("RuneFlip Companion unpaired — token revoked and removed");
			SwingUtilities.invokeLater(() ->
			{
				RuneFlipPanel target = panel;
				if (target != null)
				{
					target.setPaired(false);
				}
				onResult.accept("Unpaired. Sync stays off until you pair again.");
			});
		};
		if (token.isEmpty())
		{
			clearLocal.run();
			return;
		}
		apiClient.revokeToken(config.backendUrl(), token, clearLocal);
	}

	/** Soft panel refresh while it exists — display only, min 30s cadence. */
	@Schedule(period = 10, unit = ChronoUnit.SECONDS)
	public void panelTick()
	{
		if (panel == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		long intervalMs = Math.max(30, config.panelRefreshSeconds()) * 1_000L;
		if (now - lastPanelRefreshMs < intervalMs)
		{
			return;
		}
		refreshPanel();
	}

	/**
	 * Fetches the read endpoints and updates the sidebar. Pure display: a
	 * failing backend only flips the status to Offline — RuneLite is never
	 * affected, and no request carries the ingest token.
	 */
	private void refreshPanel()
	{
		RuneFlipPanel target = panel;
		if (target == null)
		{
			return;
		}
		lastPanelRefreshMs = System.currentTimeMillis();
		String url = config.backendUrl();
		String clientId = ensureClientId();

		apiClient.fetchRecommendations(url, 10,
			response -> SwingUtilities.invokeLater(() ->
			{
				lastPanelOkMs = System.currentTimeMillis();
				target.showStatus(true);
				target.updateRecommendations(response);
				apiClient.fetchCapital(url, clientId,
					capital -> SwingUtilities.invokeLater(() ->
						target.updateCapital(capital, response.capitalSource)),
					() -> SwingUtilities.invokeLater(() ->
						target.updateCapital(null, null)));
			}),
			() -> SwingUtilities.invokeLater(() ->
			{
				// Stale when we had data recently; Offline otherwise.
				if (lastPanelOkMs > 0
					&& System.currentTimeMillis() - lastPanelOkMs < 10 * 60_000)
				{
					target.showStale();
				}
				else
				{
					target.showStatus(false);
				}
			}));

		apiClient.fetchCompletedAlerts(url, clientId,
			response -> SwingUtilities.invokeLater(() -> target.updateCompleted(response)),
			() -> SwingUtilities.invokeLater(() -> target.updateCompleted(null)));
	}

	/** Tiny gold-diamond mark for the sidebar button (drawn, no assets). */
	private static BufferedImage buildNavIcon()
	{
		BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = image.createGraphics();
		g.setRenderingHint(
			java.awt.RenderingHints.KEY_ANTIALIASING,
			java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		int[] xs = {12, 21, 12, 3};
		int[] ys = {3, 12, 21, 12};
		g.setColor(new java.awt.Color(0xe3, 0xb7, 0x5d));
		g.fillPolygon(xs, ys, 4);
		int[] xi = {12, 17, 12, 7};
		int[] yi = {7, 12, 17, 12};
		g.setColor(new java.awt.Color(0x0b, 0x0d, 0x12));
		g.fillPolygon(xi, yi, 4);
		g.dispose();
		return image;
	}

	/** Runs on the client thread. Observation only. */
	private GeSnapshotPayload buildSnapshot()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		List<GeSlotPayload> slots = new ArrayList<>(8);
		for (int i = 0; i < Math.min(8, offers.length); i++)
		{
			GeSlotPayload slot = GeSlotMapper.map(i, offers[i], itemNameOf(offers[i]));
			if (slot != null)
			{
				slots.add(slot);
			}
		}
		return new GeSnapshotPayload(
			UUID.randomUUID().toString(),
			Instant.now().toString(),
			slots
		);
	}

	/**
	 * Runs on the client thread. Reads inventory coins passively and reuses
	 * the last USER-seen bank amount; sends only when changed or keepalive.
	 */
	private void maybeSendCapital(long now, long keepaliveMs)
	{
		Integer inventoryCoins = null;
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory != null)
		{
			inventoryCoins = inventory.count(ItemID.COINS_995);
		}
		Integer bankCoins = bankCoinsLastSeen;
		String bankAt = bankCoins != null ? bankLastSeenAt : null;

		String fingerprint =
			SnapshotFingerprint.ofCapital(inventoryCoins, bankCoins, bankAt);
		boolean changed = !fingerprint.equals(lastCapitalFingerprint);
		boolean keepaliveDue = now - lastCapitalOkMs >= keepaliveMs;
		if (!changed && !keepaliveDue)
		{
			return;
		}
		if (!capitalInFlight.compareAndSet(false, true))
		{
			return;
		}

		PlayerCapitalPayload payload = new PlayerCapitalPayload(
			UUID.randomUUID().toString(),
			Instant.now().toString(),
			inventoryCoins,
			bankCoins,
			bankAt
		);
		executor.execute(() -> sendCapital(payload, fingerprint));
	}

	/** Best-effort item name; the backend treats itemId as authoritative. */
	private String itemNameOf(GrandExchangeOffer offer)
	{
		if (offer == null || offer.getItemId() <= 0)
		{
			return null;
		}
		try
		{
			return itemManager.getItemComposition(offer.getItemId()).getName();
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/** Runs off the client thread. The response is logged, never acted on. */
	private void send(GeSnapshotPayload payload, String fingerprint)
	{
		String endpoint = BackendUrl.snapshotEndpoint(config.backendUrl());
		String token = config.ingestToken().trim();
		if (endpoint == null || token.isEmpty())
		{
			inFlight.set(false);
			return;
		}

		Request request = new Request.Builder()
			.url(endpoint)
			.header("X-RuneFlip-Token", token)
			.header(RuneFlipApiClient.CLIENT_ID_HEADER, ensureClientId())
			.post(RequestBody.create(JSON, gson.toJson(payload)))
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				inFlight.set(false);
				recordSync("network error");
				log.warn("RuneFlip sync failed (network): {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					inFlight.set(false);
					boolean wasOk = "ok".equals(lastSyncStatus);
					recordSync(describeResponse(r.code()));
					if (r.isSuccessful())
					{
						lastSentFingerprint = fingerprint;
						lastSentOkMs = System.currentTimeMillis();
						// INFO once on first success / recovery; repeats at debug.
						if (wasOk)
						{
							log.debug("RuneFlip sync ok ({} slots)", payload.slots.size());
						}
						else
						{
							log.info("RuneFlip sync ok ({} slots)", payload.slots.size());
						}
					}
					else
					{
						log.warn("RuneFlip sync rejected: {}", describeResponse(r.code()));
					}
				}
			}
		});
	}

	/** Same one-way POST for the opt-in capital observation. */
	private void sendCapital(PlayerCapitalPayload payload, String fingerprint)
	{
		String endpoint = BackendUrl.capitalEndpoint(config.backendUrl());
		String token = config.ingestToken().trim();
		if (endpoint == null || token.isEmpty())
		{
			capitalInFlight.set(false);
			return;
		}

		Request request = new Request.Builder()
			.url(endpoint)
			.header("X-RuneFlip-Token", token)
			.header(RuneFlipApiClient.CLIENT_ID_HEADER, ensureClientId())
			.post(RequestBody.create(JSON, gson.toJson(payload)))
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				capitalInFlight.set(false);
				log.warn("RuneFlip capital sync failed (network): {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					capitalInFlight.set(false);
					if (r.isSuccessful())
					{
						lastCapitalFingerprint = fingerprint;
						lastCapitalOkMs = System.currentTimeMillis();
						log.debug("RuneFlip capital sync ok");
					}
					else
					{
						log.warn("RuneFlip capital sync rejected: {}", describeResponse(r.code()));
					}
				}
			}
		});
	}

	private void recordSync(String status)
	{
		lastSyncStatus = status;
		lastSyncAt = Instant.now().toString();
	}

	/**
	 * Human-readable status per contract response code. Never includes the
	 * token or any header — safe to log.
	 */
	static String describeResponse(int code)
	{
		switch (code)
		{
			case 200:
				return "ok";
			case 400:
				return "400 rejected payload (plugin/backend version mismatch?)";
			case 401:
				return "401 unauthorized (check the ingest token in the plugin settings)";
			case 429:
				return "429 rate limited (backend will accept again shortly)";
			case 503:
				return "503 ingest disabled (set OSRS_GE_INGEST_TOKEN in the backend .env)";
			default:
				return "HTTP " + code;
		}
	}
}
