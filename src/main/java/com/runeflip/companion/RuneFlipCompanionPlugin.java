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
import net.runelite.api.MenuAction;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.VarbitChanged;
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
 * RuneFlip Companion — a strictly OBSERVATION-ONLY mirror of your Grand
 * Exchange slots (and, opt-in, passively observed capital), sent one-way to
 * a self-hosted RuneFlip dashboard. RuneFlip is manual-assisted, no botting:
 * it may assist input, but never execute intent — every GE offer is
 * reviewed and confirmed manually by the player.
 *
 * What this plugin never does (see docs/runelite-readonly-contract.md):
 * no buying, selling, cancelling, aborting or collecting — automatically or
 * otherwise; no clicks, keystrokes or menu actions; no flipping loops; no
 * opening of any interface (bank/GE state is only read when the USER opens
 * them); no reading of credentials or session data; no commands received
 * from the backend (the HTTP response is a bare acknowledgement that is
 * only logged — no command-and-control channel exists). It observes
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
	description = "Manual-assisted flipping copilot: mirrors your GE slots to a self-hosted RuneFlip dashboard. Never performs game actions — you confirm every offer.",
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
	/** SessionPanel accounting (v0.8.7): pure sums over passively observed
	 *  offer completions — display only, never an action. */
	private SessionTracker sessionTracker;

	// ── Responsiveness (v0.8.10) ────────────────────────────────────────────
	/** Short response caches: toggling pills / re-opening an item re-renders
	 *  instantly instead of waiting a network round-trip. */
	private static final long RESPONSE_CACHE_TTL_MS = 20_000;
	/** Saved-preferences query memo TTL — pill clicks skip the prefs GET. */
	private static final long PREFS_QUERY_TTL_MS = 30_000;
	private final ShortTtlCache<RuneFlipData.FastFlipOverviewResponse> overviewCache =
		new ShortTtlCache<>(RESPONSE_CACHE_TTL_MS);
	private final ShortTtlCache<RuneFlipData.FastFlipItemContextResponse> itemContextCache =
		new ShortTtlCache<>(RESPONSE_CACHE_TTL_MS);
	/** Stale-response guards: only the newest request of each kind renders. */
	private final RequestSequencer overviewSeq = new RequestSequencer();
	private final RequestSequencer itemContextSeq = new RequestSequencer();
	/** Memoized saved-preferences query ("" = defaults); null = not fetched. */
	private volatile String prefsQueryMemo;
	private volatile long prefsQueryAtMs;
	private volatile long lastPanelRefreshMs;
	private volatile long lastPanelOkMs;
	/** Item currently open in the GE Buy/Sell setup (v0.8.4), -1 when none.
	 *  Read only from the GE-current-item VarPlayer — never OCR/input. */
	private volatile int lastSelectedGeItem = -1;

	// ── Explicit GE Field Assist (v0.8.11) ──────────────────────────────────
	/** The ONLY writer of GE input fields; every write is click-gated and
	 *  editor-validated inside the service. */
	private GeFieldAssistService fieldAssist;
	/** #1 of the last rendered Fast Flip selection — the single primary GE
	 *  suggestion (v0.8.10). #2/#3 are never stored: they cannot assist. */
	private volatile RuneFlipData.FastFlipItem primaryFlip;
	/** Context of the item currently open in the GE setup (when fetched),
	 *  used to source qty/price for that exact item. */
	private volatile RuneFlipData.FastFlipItemContextResponse lastItemContext;

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
		sessionTracker = new SessionTracker(System.currentTimeMillis());
		fieldAssist = new GeFieldAssistService(client);
		if (config.panelEnabled())
		{
			panel = new RuneFlipPanel(
				itemManager, this::refreshPanel, pairingActions(),
				designActions(), isPaired());
			navButton = NavigationButton.builder()
				.tooltip("RuneFlip")
				.icon(buildNavIcon())
				.priority(7)
				.panel(panel)
				.build();
			clientToolbar.addNavigation(navButton);
			refreshPanel();
		}
		log.info("RuneFlip Companion started (manual-assisted, no botting; one-way snapshots only)");
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

	/**
	 * Passive observation only — marks the state dirty and feeds the session
	 * accounting (v0.8.7). The tracker only records ACTIVE→DONE transitions it
	 * saw itself, so the login replay never counts as session activity. Reading
	 * event fields is the only game touch-point; nothing is ever acted on.
	 */
	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		dirtyAtMs = System.currentTimeMillis();
		SessionTracker tracker = sessionTracker;
		GrandExchangeOffer offer = event.getOffer();
		if (tracker == null || offer == null)
		{
			return;
		}
		tracker.observe(
			event.getSlot(),
			sessionPhaseOf(offer.getState()),
			offer.getItemId(),
			offer.getQuantitySold(),
			offer.getSpent());
		pushSessionStats();
	}

	/**
	 * RuneLite offer state → session phase. Cancelled offers with partial fills
	 * count as done for the filled part (the coins really moved); unknown
	 * states map to EMPTY so nothing is ever guessed.
	 */
	static SessionTracker.SlotPhase sessionPhaseOf(
		net.runelite.api.GrandExchangeOfferState state)
	{
		if (state == null)
		{
			return SessionTracker.SlotPhase.EMPTY;
		}
		switch (state)
		{
			case BUYING:
				return SessionTracker.SlotPhase.ACTIVE_BUY;
			case SELLING:
				return SessionTracker.SlotPhase.ACTIVE_SELL;
			case BOUGHT:
			case CANCELLED_BUY:
				return SessionTracker.SlotPhase.DONE_BUY;
			case SOLD:
			case CANCELLED_SELL:
				return SessionTracker.SlotPhase.DONE_SELL;
			default:
				return SessionTracker.SlotPhase.EMPTY;
		}
	}

	/** Pushes the latest session KPIs to the panel (EDT). Display only. */
	private void pushSessionStats()
	{
		RuneFlipPanel target = panel;
		SessionTracker tracker = sessionTracker;
		if (target == null || tracker == null)
		{
			return;
		}
		SessionTracker.Stats stats =
			tracker.stats(System.currentTimeMillis());
		SwingUtilities.invokeLater(() -> target.updateSession(stats));
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

	/**
	 * Context-aware GE item detection (v0.8.4). Reads ONLY the read-only
	 * "current GE item" VarPlayer to learn which item the user just opened in
	 * the Buy/Sell setup — no OCR, no screen scraping, no synthetic input, no
	 * widget mutation. When the selection changes, the panel swaps its Top-3
	 * list for that item's RuneFlip context (fetched read-only); when the setup
	 * closes it falls back to the Top-3. VarbitChanged is dispatched on the
	 * client thread, so the var read here is safe. Display only.
	 */
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (panel == null || !config.contextualGePanel())
		{
			return;
		}
		int current = GeItemSelection.selectedItemId(
			client.getVarpValue(GeItemSelection.GE_CURRENT_ITEM_VARP));
		if (current == lastSelectedGeItem)
		{
			return;
		}
		lastSelectedGeItem = current;
		// The stored context belongs to the previous selection — drop it so
		// the field assist can never offer another item's qty/price.
		lastItemContext = null;
		RuneFlipPanel target = panel;
		if (current > 0)
		{
			fetchItemContext(current);
		}
		else if (target != null)
		{
			SwingUtilities.invokeLater(target::clearSelectedItem);
		}
	}

	/**
	 * Explicit GE Field Assist (v0.8.11) — the DISPLAY half. When the user
	 * right-clicks while a GE editor is open, this adds a "RuneFlip: …" menu
	 * OPTION for exactly one field of exactly the open item: select the #1
	 * primary suggestion in the item search, or set that item's qty/price in
	 * the chatbox editor. Adding an option is not an action — nothing happens
	 * unless the USER clicks it, and the click lands in
	 * {@link GeFieldAssistService}, which re-validates the editor and the
	 * USER_CLICK source before preparing the value. It never submits,
	 * confirms, cancels or collects; the official rule is: RuneFlip can
	 * prepare GE fields after explicit user action, but must never submit or
	 * execute the offer.
	 */
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		GeFieldAssistService assist = fieldAssist;
		if (assist == null || !config.enableGeFieldAssist())
		{
			return;
		}

		// GE item search: offer the #1 primary suggestion (never #2/#3).
		RuneFlipData.FastFlipItem primary = primaryFlip;
		if (assist.isItemSearchOpen())
		{
			if (primary != null && primary.itemName != null)
			{
				String name = primary.itemName;
				addAssistOption(GeFieldAssist.selectLabel(name), () ->
					assist.prepareItemSearch(
						name, GeFieldAssist.ActionSource.USER_CLICK));
			}
			return;
		}

		// Qty/price chatbox editor: only for the item the user has OPEN.
		if (!assist.isValueEditorOpen())
		{
			return;
		}
		GeFieldAssist.Field field =
			GeFieldAssist.fieldForPrompt(assist.chatboxPrompt());
		int openItem = lastSelectedGeItem;
		if (field == GeFieldAssist.Field.QUANTITY)
		{
			Long qty = GeFieldAssist.qtyFor(openItem, lastItemContext, primary);
			if (qty != null)
			{
				long value = qty;
				addAssistOption(GeFieldAssist.qtyLabel(value), () ->
					assist.prepareQuantity(
						value, GeFieldAssist.ActionSource.USER_CLICK));
			}
		}
		else if (field == GeFieldAssist.Field.PRICE)
		{
			Long price = GeFieldAssist.priceFor(
				openItem, assist.isSellOffer(), lastItemContext, primary);
			if (price != null)
			{
				long value = price;
				addAssistOption(GeFieldAssist.priceLabel(value), () ->
					assist.preparePrice(
						value, GeFieldAssist.ActionSource.USER_CLICK));
			}
		}
	}

	/** One "RuneFlip: …" menu option. The consumer runs only on the user's
	 *  own click on that option (client thread), never programmatically. */
	private void addAssistOption(String label, Runnable onUserClick)
	{
		client.getMenu().createMenuEntry(-1)
			.setOption(label)
			.setTarget("")
			.setType(MenuAction.RUNELITE)
			.onClick(entry -> onUserClick.run());
	}

	/**
	 * Fetches GET /fast-flip/item/:itemId for the selected GE item and shows its
	 * context, honoring this install's saved strategy preferences and forwarding
	 * the anonymous client id (so the action is offer-aware). Any failure just
	 * clears the context card — the Top-3 list stays. Display only.
	 */
	private void fetchItemContext(int itemId)
	{
		RuneFlipPanel target = panel;
		if (target == null)
		{
			return;
		}
		String url = config.backendUrl();
		String clientId = ensureClientId();

		// Fresh cache (v0.8.10): re-opening the same item re-renders instantly.
		String memoBase = prefsQueryMemo != null ? prefsQueryMemo : "";
		RuneFlipData.FastFlipItemContextResponse cached = itemContextCache.get(
			itemContextKey(itemId, overriddenQuery(memoBase)),
			System.currentTimeMillis());
		if (cached != null)
		{
			SwingUtilities.invokeLater(
				() -> respectSelection(itemId, target, cached));
			return;
		}

		// Immediate feedback (v0.8.10): the card swaps to "Loading item
		// context…" NOW; the fetch fills it in the background. A newer
		// selection or strategy change makes this ticket stale.
		SwingUtilities.invokeLater(target::showSelectedItemLoading);
		long ticket = itemContextSeq.begin();
		withBaseQuery(url, clientId, base ->
		{
			String query = overriddenQuery(base);
			apiClient.fetchFastFlipItem(url, itemId, query, clientId,
				res ->
				{
					if (!itemContextSeq.isCurrent(ticket))
					{
						return;
					}
					itemContextCache.put(itemContextKey(itemId, query), res,
						System.currentTimeMillis());
					SwingUtilities.invokeLater(
						() -> respectSelection(itemId, target, res));
				},
				() ->
				{
					if (!itemContextSeq.isCurrent(ticket))
					{
						return;
					}
					SwingUtilities.invokeLater(target::clearSelectedItem);
				});
		});
	}

	/** Cache key for one item context under one effective strategy. */
	private static String itemContextKey(int itemId, String strategyQuery)
	{
		return itemId + "|" + strategyQuery;
	}

	/**
	 * Runs an action with the saved-preferences base query (v0.8.10): served
	 * from a 30s memo when fresh — so pill clicks and item selections skip the
	 * prefs round-trip — otherwise fetched once and memoized. A failed fetch
	 * degrades to the default strategy ("") without memoizing the failure.
	 */
	private void withBaseQuery(
		String url, String clientId, java.util.function.Consumer<String> action)
	{
		String memo = prefsQueryMemo;
		if (memo != null
			&& System.currentTimeMillis() - prefsQueryAtMs < PREFS_QUERY_TTL_MS)
		{
			action.accept(memo);
			return;
		}
		apiClient.fetchStrategyPreferences(url, clientId,
			prefs ->
			{
				String query = RuneFlipApiClient.strategyQueryOf(prefs);
				prefsQueryMemo = query;
				prefsQueryAtMs = System.currentTimeMillis();
				action.accept(query);
			},
			() -> action.accept(""));
	}

	/**
	 * Applies a fetched item context only if it still matches the item the user
	 * has open (a slow response for a previous selection is dropped, not shown
	 * over the current one).
	 */
	private void respectSelection(
		int itemId,
		RuneFlipPanel target,
		RuneFlipData.FastFlipItemContextResponse res)
	{
		if (lastSelectedGeItem == itemId)
		{
			// Remembered for the field assist (v0.8.11): qty/price offers for
			// the OPEN item come from this context. Read-only bookkeeping.
			lastItemContext = res;
			target.updateSelectedItem(res);
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

	/**
	 * StrategyPill + Session callbacks (v0.8.7 design). All display-only:
	 * pills store a LOCAL fetch preference (clicking the active pill clears
	 * it) and re-fetch; reset clears the local session accounting. Nothing
	 * here writes to the backend's saved preferences or touches the game.
	 *
	 * <p>Responsiveness (v0.8.10): the clicked pill highlights OPTIMISTICALLY
	 * before any response ("Updating…" shows until it renders), and only the
	 * Fast Flip card re-fetches — served from the short cache when fresh.
	 */
	private RuneFlipPanel.DesignActions designActions()
	{
		return new RuneFlipPanel.DesignActions()
		{
			@Override
			public void onTimeframePill(int minutes)
			{
				int current = config.strategyTimeframeMinutes();
				configManager.setConfiguration(
					RuneFlipCompanionConfig.GROUP,
					"strategyTimeframeMinutes",
					current == minutes ? 0 : minutes);
				Integer optimistic =
					StrategyParams.optimisticTimeframe(current, minutes);
				RuneFlipPanel target = panel;
				if (target != null)
				{
					SwingUtilities.invokeLater(
						() -> target.showStrategyPendingTimeframe(optimistic));
				}
				refreshFastFlip();
			}

			@Override
			public void onRiskPill(String riskLevel)
			{
				String current = config.strategyRiskLevel();
				configManager.setConfiguration(
					RuneFlipCompanionConfig.GROUP, "strategyRiskLevel",
					riskLevel.equals(current) ? "" : riskLevel);
				String optimistic =
					StrategyParams.optimisticRisk(current, riskLevel);
				RuneFlipPanel target = panel;
				if (target != null)
				{
					SwingUtilities.invokeLater(
						() -> target.showStrategyPendingRisk(optimistic));
				}
				refreshFastFlip();
			}

			@Override
			public void onSessionReset()
			{
				SessionTracker tracker = sessionTracker;
				if (tracker != null)
				{
					tracker.reset(System.currentTimeMillis());
				}
				pushSessionStats();
			}
		};
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
				// New identity: force a fresh snapshot/capital send, and drop
				// the response caches — offer-aware actions and saved prefs
				// belong to the new client id (v0.8.10).
				lastSentFingerprint = null;
				lastCapitalFingerprint = null;
				overviewCache.clear();
				itemContextCache.clear();
				prefsQueryMemo = null;
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

		// Focus the panel (v0.8.5): contextual mode shows only the selected-item
		// card or the Top 3, hiding the legacy dashboard + GE completed. Applied
		// each refresh so a config toggle takes effect without a restart.
		final boolean contextual = config.contextualGePanel();
		SwingUtilities.invokeLater(() -> target.setContextualMode(contextual));

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

		// Fast Flip card (v0.7.0) honoring this install's saved strategy
		// preferences (v0.8.1): the private prefs fetch runs first; an old
		// backend or a failed fetch falls back to the default strategy. The
		// client id is forwarded (v0.8.2 opt-in) so recommended actions know
		// about this install's existing GE offers. Display only — the response
		// is rendered verbatim, never acted on.
		// SessionPanel KPIs (v0.8.7): refresh alongside the reads so the
		// session time stays current even without new offer events.
		pushSessionStats();

		// The StrategyPill's local override (v0.8.7) is applied on top of the
		// saved preferences — served from the 30s memo when fresh (v0.8.10),
		// and the default strategy applies when the prefs fetch fails.
		withBaseQuery(url, clientId, base ->
			loadFastFlip(url, overriddenQuery(base), clientId, target));

		apiClient.fetchCompletedAlerts(url, clientId,
			response -> SwingUtilities.invokeLater(() -> target.updateCompleted(response)),
			() -> SwingUtilities.invokeLater(() -> target.updateCompleted(null)));
	}

	/** Saved-preferences query + the StrategyPill's local override (v0.8.7). */
	private String overriddenQuery(String baseQuery)
	{
		return StrategyParams.override(
			baseQuery,
			config.strategyTimeframeMinutes(),
			config.strategyRiskLevel());
	}

	/**
	 * Fetches GET /fast-flip/overview with the given (already-validated) strategy
	 * query and renders it. Logs a safe one-line diagnostic (v0.8.5-c): the
	 * endpoint, the strategy params and the per-section counts — never the client
	 * id or any token — so a "Fast flip · 0" report can be traced to the strategy
	 * that filtered everything out. When a SAVED strategy empties every section
	 * (v0.8.6) the plugin re-fetches once with the DEFAULT strategy — the exact
	 * params a fresh web session uses (the backend applies 480 min / HIGH) — and
	 * the panel labels those rows as the default fallback. A failed fetch logs a
	 * warning with the normalized URL (user config, no ids) and renders the
	 * offline state, never "no matches". Display only.
	 */
	private void loadFastFlip(
		String url,
		String strategyQuery,
		String clientId,
		RuneFlipPanel target)
	{
		// Fresh cache (v0.8.10): pill toggles within the TTL re-render at once
		// — no fetch, no flicker. The regular refresh cycle repopulates it.
		RuneFlipData.FastFlipOverviewResponse cached =
			overviewCache.get(strategyQuery, System.currentTimeMillis());
		if (cached != null)
		{
			rememberPrimary(cached);
			SwingUtilities.invokeLater(
				() -> target.updateFastFlip(cached));
			return;
		}

		// Stale-response guard (v0.8.10): rapid Low→Med→High clicks issue new
		// tickets; only the newest request's answer ever renders.
		long ticket = overviewSeq.begin();
		apiClient.fetchFastFlipOverview(url, strategyQuery, clientId,
			response ->
			{
				if (!overviewSeq.isCurrent(ticket))
				{
					return;
				}
				logFastFlip(strategyQuery, response);
				overviewCache.put(strategyQuery, response,
					System.currentTimeMillis());
				boolean matchedNothing = FastFlipSelection.select(
					response, RuneFlipPanel.MAX_FAST_FLIP_ROWS).source
					== FastFlipSelection.Source.NONE;
				if (matchedNothing && strategyQuery != null && !strategyQuery.isEmpty())
				{
					// Saved strategy filtered everything: show the default-
					// strategy ideas (labelled as such) instead of an empty box.
					// Same ticket: a newer click also cancels this fallback.
					apiClient.fetchFastFlipOverview(url, "", clientId,
						fallback ->
						{
							if (!overviewSeq.isCurrent(ticket))
							{
								return;
							}
							logFastFlip("", fallback);
							overviewCache.put("", fallback,
								System.currentTimeMillis());
							rememberPrimary(fallback);
							SwingUtilities.invokeLater(() ->
								target.updateFastFlip(fallback, true));
						},
						() ->
						{
							if (!overviewSeq.isCurrent(ticket))
							{
								return;
							}
							rememberPrimary(response);
							SwingUtilities.invokeLater(() ->
								target.updateFastFlip(response, false));
						});
					return;
				}
				rememberPrimary(response);
				SwingUtilities.invokeLater(
					() -> target.updateFastFlip(response));
			},
			() ->
			{
				if (!overviewSeq.isCurrent(ticket))
				{
					return;
				}
				logFastFlip(strategyQuery, null);
				// Warn (not debug): this is the line that distinguishes a broken
				// backend URL / network from a strategy that matched nothing.
				// The URL is the user's own config; no client id, no token.
				log.warn("RuneFlip fast-flip overview fetch failed: {} (strategy={})",
					BackendUrl.normalize(url),
					strategyQuery == null || strategyQuery.isEmpty()
						? "default" : strategyQuery);
				rememberPrimary(null);
				SwingUtilities.invokeLater(
					() -> target.updateFastFlip(null));
			});
	}

	/**
	 * Remembers the #1 of the selection the panel is about to render — the
	 * single primary GE suggestion (v0.8.10) and the only Top-3 entry the
	 * field assist (v0.8.11) may ever offer. Empty/offline selections clear
	 * it, so a stale #1 can never be offered.
	 */
	private void rememberPrimary(RuneFlipData.FastFlipOverviewResponse response)
	{
		FastFlipSelection selection =
			FastFlipSelection.select(response, RuneFlipPanel.MAX_FAST_FLIP_ROWS);
		primaryFlip = selection.rows.isEmpty() ? null : selection.rows.get(0);
	}

	/**
	 * Fast Flip-only refresh (v0.8.10), used by the strategy pills: skips the
	 * recommendations/capital/alerts fetches a full panel refresh would also
	 * fire, so the click's answer is one (or zero, on cache hit) round-trip.
	 */
	private void refreshFastFlip()
	{
		RuneFlipPanel target = panel;
		if (target == null)
		{
			return;
		}
		String url = config.backendUrl();
		String clientId = ensureClientId();
		withBaseQuery(url, clientId, base ->
			loadFastFlip(url, overriddenQuery(base), clientId, target));
	}

	/**
	 * Safe debug line for the Fast Flip fetch (v0.8.5-c). The strategy query
	 * carries only timeframe/risk/minProfit/minRoi — never the client id or the
	 * ingest token — so it is safe to log. Off unless debug logging is enabled.
	 */
	private void logFastFlip(
		String strategyQuery, RuneFlipData.FastFlipOverviewResponse response)
	{
		if (!log.isDebugEnabled())
		{
			return;
		}
		int top = response == null || response.topFlips == null
			? 0 : response.topFlips.size();
		int fastBuy = response == null || response.fastBuy == null
			? 0 : response.fastBuy.size();
		int fastSell = response == null || response.fastSell == null
			? 0 : response.fastSell.size();
		String strategy = strategyQuery == null || strategyQuery.isEmpty()
			? "default" : strategyQuery;
		log.debug(
			"RuneFlip Fast Flip: GET /fast-flip/overview?limit=3 strategy={} "
				+ "top={} fastBuy={} fastSell={}",
			strategy, top, fastBuy, fastSell);
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
