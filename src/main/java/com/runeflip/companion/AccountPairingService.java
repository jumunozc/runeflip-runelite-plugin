package com.runeflip.companion;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Account pairing state machine (v0.9.1). Drives the OAuth-style device
 * authorization from the plugin side: start → show userCode → poll with
 * backoff until approved/denied/expired — cancelable at any point, and
 * hard-bounded by the request's expiry (no infinite polling).
 *
 * <p>Secrets discipline: the deviceCode lives only in this object's memory
 * for the duration of one pairing attempt; the credential passes straight
 * through {@link Listener#onApproved} into secret config. NEITHER is ever
 * logged — state transitions are reported by name only.
 *
 * <p>Compliance: this class talks HTTP and Swing-agnostic callbacks only.
 * Nothing here reads or touches the game client.
 */
public class AccountPairingService
{
	/** Sidebar states. NOT_CONNECTED also covers "legacy-paired only". */
	public enum State
	{
		NOT_CONNECTED,
		WAITING_APPROVAL,
		CONNECTED,
		EXPIRED,
		DENIED,
		REVOKED,
	}

	/** HTTP boundary — implemented with RuneFlipApiClient, faked in tests. */
	public interface Transport
	{
		void start(
			Consumer<RuneFlipData.DevicePairingStartResponse> onSuccess,
			Consumer<String> onFailure);

		void poll(
			String deviceCode,
			Consumer<RuneFlipData.DevicePollResponse> onSuccess,
			Runnable onFailure);
	}

	/** Plugin-side reactions. Called on the scheduler thread. */
	public interface Listener
	{
		void onStateChanged(State state, String userCode, String pairingUrl, String error);

		/** The single credential delivery — store it, never show it. */
		void onApproved(RuneFlipData.DevicePollResponse response);
	}

	/** Extra pacing added on every slow_down answer. */
	static final int SLOW_DOWN_EXTRA_SECONDS = 2;
	/** Poll cap even if the backend never answers a terminal status. */
	static final int MAX_POLLS = 200;

	private final ScheduledExecutorService scheduler;
	private final Transport transport;
	private final Listener listener;

	private volatile State state = State.NOT_CONNECTED;
	private String deviceCode;
	private String userCode;
	private String pairingUrl;
	private long expiresAtMs;
	private int intervalSeconds;
	private int polls;
	private ScheduledFuture<?> pollTask;

	public AccountPairingService(
		ScheduledExecutorService scheduler,
		Transport transport,
		Listener listener)
	{
		this.scheduler = scheduler;
		this.transport = transport;
		this.listener = listener;
	}

	public State state()
	{
		return state;
	}

	public String userCode()
	{
		return userCode;
	}

	public String pairingUrl()
	{
		return pairingUrl;
	}

	/** Millis until the current pairing attempt expires (0 when none). */
	public long remainingMs()
	{
		return state == State.WAITING_APPROVAL
			? Math.max(0, expiresAtMs - System.currentTimeMillis())
			: 0;
	}

	/** User clicked "Connect RuneFlip account". */
	public synchronized void connect()
	{
		cancelPollTask();
		transport.start(
			response -> onStarted(response),
			error -> moveTo(State.NOT_CONNECTED, error));
	}

	/** User clicked Cancel while waiting — stops polling immediately. */
	public synchronized void cancel()
	{
		cancelPollTask();
		clearAttempt();
		moveTo(State.NOT_CONNECTED, null);
	}

	/** External world changed under us (config cleared, disconnect, boot). */
	public synchronized void reset(State newState)
	{
		cancelPollTask();
		clearAttempt();
		moveTo(newState, null);
	}

	/** Ingest got a 401 with an account credential: access was revoked. */
	public synchronized void markRevoked()
	{
		if (state == State.CONNECTED)
		{
			moveTo(State.REVOKED, null);
		}
	}

	public synchronized void shutdown()
	{
		cancelPollTask();
	}

	private synchronized void onStarted(
		RuneFlipData.DevicePairingStartResponse response)
	{
		deviceCode = response.deviceCode;
		userCode = response.userCode;
		pairingUrl = response.verificationUriComplete != null
			? response.verificationUriComplete
			: response.verificationUri;
		intervalSeconds = response.pollIntervalSeconds != null
			&& response.pollIntervalSeconds > 0
			? response.pollIntervalSeconds
			: 5;
		expiresAtMs = parseExpiry(response.expiresAt);
		polls = 0;
		moveTo(State.WAITING_APPROVAL, null);
		schedulePoll(intervalSeconds);
	}

	private synchronized void schedulePoll(int delaySeconds)
	{
		if (state != State.WAITING_APPROVAL)
		{
			return;
		}
		pollTask = scheduler.schedule(this::pollOnce, delaySeconds, TimeUnit.SECONDS);
	}

	private synchronized void pollOnce()
	{
		if (state != State.WAITING_APPROVAL)
		{
			return;
		}
		if (System.currentTimeMillis() >= expiresAtMs || ++polls > MAX_POLLS)
		{
			clearAttempt();
			moveTo(State.EXPIRED, null);
			return;
		}
		transport.poll(deviceCode, this::onPollAnswer,
			// Network hiccup: keep waiting at the same cadence (still
			// bounded by the expiry check above).
			() -> schedulePoll(intervalSeconds));
	}

	private synchronized void onPollAnswer(RuneFlipData.DevicePollResponse response)
	{
		if (state != State.WAITING_APPROVAL)
		{
			return;
		}
		String status = response.status == null ? "" : response.status;
		switch (status)
		{
			case "authorization_pending":
				schedulePoll(intervalSeconds);
				return;
			case "slow_down":
				intervalSeconds += SLOW_DOWN_EXTRA_SECONDS;
				schedulePoll(intervalSeconds);
				return;
			case "access_denied":
				clearAttempt();
				moveTo(State.DENIED, null);
				return;
			case "expired_token":
				clearAttempt();
				moveTo(State.EXPIRED, null);
				return;
			case "approved":
				clearAttempt();
				moveTo(State.CONNECTED, null);
				listener.onApproved(response);
				return;
			default:
				// Unknown status from a newer backend: keep waiting.
				schedulePoll(intervalSeconds);
		}
	}

	private void cancelPollTask()
	{
		if (pollTask != null)
		{
			pollTask.cancel(false);
			pollTask = null;
		}
	}

	private void clearAttempt()
	{
		deviceCode = null;
	}

	private void moveTo(State next, String error)
	{
		state = next;
		listener.onStateChanged(next, userCode, pairingUrl, error);
	}

	private static long parseExpiry(String iso)
	{
		try
		{
			return java.time.Instant.parse(iso).toEpochMilli();
		}
		catch (RuntimeException e)
		{
			// Unparseable expiry: fall back to a 10-minute local window.
			return System.currentTimeMillis() + 10 * 60_000L;
		}
	}
}
