package com.runeflip.companion;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Plan & entitlements state of this install (v0.9.2). Pure state machine —
 * no game APIs, no Swing: the plugin injects a transport (the HTTP fetch)
 * and a listener (panel update) and calls {@link #refreshIfStale}.
 *
 * Rules, in order of importance:
 * <ul>
 *   <li><b>The server is the real gate.</b> A FREE response already excludes
 *       Members items and premium targets; this class only decides what the
 *       PANEL renders (Connected · Free/Pro, GE-assist lock copy).</li>
 *   <li><b>Fallback FREE, never up.</b> Before the first successful fetch,
 *       and after {@link #HARD_EXPIRY_MS} without one, the state is the FREE
 *       bundle. A fetch failure never grants anything.</li>
 *   <li><b>Never abrupt.</b> Within the hard expiry a transient failure keeps
 *       the last-known plan, so an open panel does not flicker or lose
 *       content because of one bad request.</li>
 * </ul>
 */
public class EntitlementsService
{
	/** Refetch cadence while the panel is alive. */
	static final long TTL_MS = 5 * 60_000L;
	/** Without a successful fetch for this long, degrade to FREE. */
	static final long HARD_EXPIRY_MS = 15 * 60_000L;

	static final String PLAN_FREE = "FREE";
	static final String PLAN_PRO = "PRO";

	/**
	 * Mirror of PLAN_FREE_ENTITLEMENTS in packages/shared (pinned by
	 * EntitlementsServiceTest against the bundled fixture, same pattern as
	 * ProdOverviewContractTest).
	 */
	static final Set<String> FREE_ENTITLEMENTS =
		Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"core.dashboard",
			"core.ge_slots",
			"items.f2p",
			"watchlist.basic",
			"history.recent",
			"stats.session",
			"fastflip.basic",
			"pairing",
			"devices.basic",
			"account.manage",
			"settings.basic",
			"billing.manage")));

	/** The HTTP fetch (RuneFlipApiClient.fetchEntitlements, bound by the plugin). */
	public interface Transport
	{
		void fetch(
			Consumer<RuneFlipData.EntitlementsResponse> onSuccess,
			Runnable onFailure);
	}

	/** Notified (on the calling thread) whenever the plan code changes. */
	public interface Listener
	{
		void onPlanChanged(String planCode);
	}

	private final Transport transport;
	private final Listener listener;
	private final LongSupplier clock;

	private volatile String planCode = PLAN_FREE;
	private volatile Set<String> entitlements = FREE_ENTITLEMENTS;
	private volatile long lastSuccessMs;
	private volatile boolean fetching;

	public EntitlementsService(Transport transport, Listener listener)
	{
		this(transport, listener, System::currentTimeMillis);
	}

	EntitlementsService(Transport transport, Listener listener, LongSupplier clock)
	{
		this.transport = transport;
		this.listener = listener;
		this.clock = clock;
	}

	/** "FREE" | "PRO" — display input for the Connected · Free/Pro label. */
	public String planCode()
	{
		return planCode;
	}

	/** The ONLY check callers make — never "is PRO?". */
	public boolean has(String entitlementKey)
	{
		return entitlements.contains(entitlementKey);
	}

	/** Kicks a refetch when the cached state is older than the TTL. Safe to
	 *  call every panel refresh; concurrent calls collapse into one fetch. */
	public void refreshIfStale()
	{
		long now = clock.getAsLong();
		if (fetching || now - lastSuccessMs < TTL_MS)
		{
			return;
		}
		fetching = true;
		transport.fetch(this::apply, () ->
		{
			fetching = false;
			// Transient failure: keep the last-known plan inside the hard
			// expiry (never abrupt), degrade to FREE beyond it (never stale
			// premium forever — a revoked credential must not keep PRO).
			if (clock.getAsLong() - lastSuccessMs >= HARD_EXPIRY_MS)
			{
				fallbackToFree();
			}
		});
	}

	/** Back to the FREE floor (used on disconnect and hard expiry). */
	public void reset()
	{
		fallbackToFree();
		lastSuccessMs = 0;
	}

	private void apply(RuneFlipData.EntitlementsResponse response)
	{
		fetching = false;
		lastSuccessMs = clock.getAsLong();
		String previous = planCode;
		// Only PRO is ever an upgrade; anything unknown stays FREE.
		planCode = PLAN_PRO.equals(response.planCode) ? PLAN_PRO : PLAN_FREE;
		entitlements = response.entitlements == null
			? FREE_ENTITLEMENTS
			: Collections.unmodifiableSet(new HashSet<>(response.entitlements));
		if (!previous.equals(planCode))
		{
			listener.onPlanChanged(planCode);
		}
	}

	private void fallbackToFree()
	{
		String previous = planCode;
		planCode = PLAN_FREE;
		entitlements = FREE_ENTITLEMENTS;
		if (!previous.equals(planCode))
		{
			listener.onPlanChanged(planCode);
		}
	}
}
