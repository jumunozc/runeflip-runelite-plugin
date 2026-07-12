package com.runeflip.companion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Account pairing state machine (v0.9.1): backoff, slow_down, terminal
 * states, cancelation, expiry bounding and the single credential delivery.
 * The scheduler is synchronous (runs tasks inline when drained) so the test
 * drives every poll deterministically.
 */
public class AccountPairingServiceTest
{
	/** Deterministic scheduler: schedule() queues; drain() runs one task. */
	private static class ManualScheduler extends ScheduledThreadPoolExecutor
	{
		final List<Runnable> queued = new ArrayList<>();
		final List<Long> delaysSeconds = new ArrayList<>();

		ManualScheduler()
		{
			super(1);
		}

		@Override
		public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
		{
			queued.add(command);
			delaysSeconds.add(unit.toSeconds(delay));
			return new NoopFuture();
		}

		void drainOne()
		{
			if (!queued.isEmpty())
			{
				queued.remove(0).run();
			}
		}
	}

	private static class NoopFuture implements ScheduledFuture<Object>
	{
		@Override
		public long getDelay(TimeUnit unit)
		{
			return 0;
		}

		@Override
		public int compareTo(Delayed o)
		{
			return 0;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning)
		{
			return true;
		}

		@Override
		public boolean isCancelled()
		{
			return false;
		}

		@Override
		public boolean isDone()
		{
			return true;
		}

		@Override
		public Object get()
		{
			return null;
		}

		@Override
		public Object get(long timeout, TimeUnit unit)
		{
			return null;
		}
	}

	private static class FakeTransport implements AccountPairingService.Transport
	{
		final List<String> pollStatuses = new ArrayList<>();
		String credential = "rfd1_" + repeat("ab", 32);
		String polledDeviceCode;
		long expiresInMs = 10 * 60_000L;

		@Override
		public void start(
			Consumer<RuneFlipData.DevicePairingStartResponse> onSuccess,
			Consumer<String> onFailure)
		{
			RuneFlipData.DevicePairingStartResponse response =
				new RuneFlipData.DevicePairingStartResponse();
			response.deviceCode = "rfdc_" + repeat("cd", 32);
			response.userCode = "ABCD-2345";
			response.verificationUri = "http://localhost:3000/pair";
			response.verificationUriComplete = "http://localhost:3000/pair?code=ABCD2345";
			response.expiresAt = java.time.Instant.ofEpochMilli(
				System.currentTimeMillis() + expiresInMs).toString();
			response.pollIntervalSeconds = 5;
			onSuccess.accept(response);
		}

		@Override
		public void poll(
			String deviceCode,
			Consumer<RuneFlipData.DevicePollResponse> onSuccess,
			Runnable onFailure)
		{
			polledDeviceCode = deviceCode;
			RuneFlipData.DevicePollResponse response =
				new RuneFlipData.DevicePollResponse();
			response.status = pollStatuses.isEmpty()
				? "authorization_pending"
				: pollStatuses.remove(0);
			if ("approved".equals(response.status))
			{
				response.credential = credential;
				RuneFlipData.DevicePollDevice device =
					new RuneFlipData.DevicePollDevice();
				device.id = "dev_1";
				device.name = "RuneLite";
				device.platform = "runelite";
				device.clientId = "aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa";
				response.device = device;
			}
			onSuccess.accept(response);
		}
	}

	private static class RecordingListener implements AccountPairingService.Listener
	{
		final List<AccountPairingService.State> states = new ArrayList<>();
		final List<RuneFlipData.DevicePollResponse> approvals = new ArrayList<>();
		String lastUserCode;

		@Override
		public void onStateChanged(
			AccountPairingService.State state,
			String userCode,
			String pairingUrl,
			String error)
		{
			states.add(state);
			lastUserCode = userCode;
		}

		@Override
		public void onApproved(RuneFlipData.DevicePollResponse response)
		{
			approvals.add(response);
		}
	}

	private static String repeat(String s, int times)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < times; i++)
		{
			sb.append(s);
		}
		return sb.toString();
	}

	private ManualScheduler scheduler;
	private FakeTransport transport;
	private RecordingListener listener;
	private AccountPairingService service;

	private void build()
	{
		scheduler = new ManualScheduler();
		transport = new FakeTransport();
		listener = new RecordingListener();
		service = new AccountPairingService(scheduler, transport, listener);
	}

	@Test
	public void connectShowsTheUserCodeAndWaits()
	{
		build();
		service.connect();
		assertEquals(AccountPairingService.State.WAITING_APPROVAL, service.state());
		assertEquals("ABCD-2345", listener.lastUserCode);
		assertEquals("http://localhost:3000/pair?code=ABCD2345", service.pairingUrl());
		assertTrue(service.remainingMs() > 0);
	}

	@Test
	public void approvedDeliversTheCredentialExactlyOnceAndConnects()
	{
		build();
		transport.pollStatuses.add("authorization_pending");
		transport.pollStatuses.add("approved");
		service.connect();
		scheduler.drainOne(); // pending
		scheduler.drainOne(); // approved
		assertEquals(AccountPairingService.State.CONNECTED, service.state());
		assertEquals(1, listener.approvals.size());
		assertEquals(transport.credential, listener.approvals.get(0).credential);
		// No further polls are scheduled after a terminal state.
		assertTrue(scheduler.queued.isEmpty());
	}

	@Test
	public void slowDownWidensThePollInterval()
	{
		build();
		transport.pollStatuses.add("slow_down");
		transport.pollStatuses.add("authorization_pending");
		service.connect();
		long first = scheduler.delaysSeconds.get(0);
		scheduler.drainOne(); // slow_down → widened reschedule
		long second = scheduler.delaysSeconds.get(1);
		assertEquals(first + AccountPairingService.SLOW_DOWN_EXTRA_SECONDS, second);
		scheduler.drainOne(); // pending keeps the widened cadence
		long third = scheduler.delaysSeconds.get(2);
		assertEquals(second, third);
	}

	@Test
	public void deniedAndExpiredAreTerminal()
	{
		build();
		transport.pollStatuses.add("access_denied");
		service.connect();
		scheduler.drainOne();
		assertEquals(AccountPairingService.State.DENIED, service.state());
		assertTrue(scheduler.queued.isEmpty());

		build();
		transport.pollStatuses.add("expired_token");
		service.connect();
		scheduler.drainOne();
		assertEquals(AccountPairingService.State.EXPIRED, service.state());
		assertTrue(scheduler.queued.isEmpty());
	}

	@Test
	public void localExpiryStopsPollingWithoutServerHelp()
	{
		build();
		transport.expiresInMs = -1_000; // already expired when polling starts
		service.connect();
		scheduler.drainOne();
		assertEquals(AccountPairingService.State.EXPIRED, service.state());
		assertNull(transport.polledDeviceCode); // never even hit the network
	}

	@Test
	public void cancelStopsPollingImmediately()
	{
		build();
		service.connect();
		service.cancel();
		assertEquals(AccountPairingService.State.NOT_CONNECTED, service.state());
		// A queued poll that fires after cancel is a no-op.
		scheduler.drainOne();
		assertNull(transport.polledDeviceCode);
	}

	@Test
	public void revokedOnlyAppliesWhileConnected()
	{
		build();
		service.markRevoked();
		assertEquals(AccountPairingService.State.NOT_CONNECTED, service.state());
		service.reset(AccountPairingService.State.CONNECTED);
		service.markRevoked();
		assertEquals(AccountPairingService.State.REVOKED, service.state());
	}

	@Test
	public void networkFailuresKeepWaitingBoundedByExpiry()
	{
		build();
		AccountPairingService.Transport flaky = new AccountPairingService.Transport()
		{
			@Override
			public void start(
				Consumer<RuneFlipData.DevicePairingStartResponse> onSuccess,
				Consumer<String> onFailure)
			{
				transport.start(onSuccess, onFailure);
			}

			@Override
			public void poll(
				String deviceCode,
				Consumer<RuneFlipData.DevicePollResponse> onSuccess,
				Runnable onFailure)
			{
				onFailure.run(); // network hiccup every time
			}
		};
		service = new AccountPairingService(scheduler, flaky, listener);
		service.connect();
		scheduler.drainOne();
		assertEquals(AccountPairingService.State.WAITING_APPROVAL, service.state());
		assertTrue("a retry poll must be scheduled", !scheduler.queued.isEmpty());
	}
}
