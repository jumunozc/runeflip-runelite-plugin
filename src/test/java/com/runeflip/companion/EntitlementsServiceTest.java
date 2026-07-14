package com.runeflip.companion;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Plan/entitlement cache of the plugin (v0.9.2): FREE fallback, TTL
 * refetching, non-abrupt failure handling and the mirror contract with
 * packages/shared. No game APIs anywhere near this — pure state.
 */
public class EntitlementsServiceTest
{
	/** Controllable transport: the test decides when/how fetches resolve. */
	private static final class FakeTransport implements EntitlementsService.Transport
	{
		int fetches;
		Consumer<RuneFlipData.EntitlementsResponse> pendingSuccess;
		Runnable pendingFailure;

		@Override
		public void fetch(
			Consumer<RuneFlipData.EntitlementsResponse> onSuccess,
			Runnable onFailure)
		{
			fetches++;
			pendingSuccess = onSuccess;
			pendingFailure = onFailure;
		}

		void succeedWith(String planCode, List<String> entitlements)
		{
			RuneFlipData.EntitlementsResponse response =
				new RuneFlipData.EntitlementsResponse();
			response.planCode = planCode;
			response.entitlements = entitlements;
			pendingSuccess.accept(response);
		}
	}

	private static final class Clock
	{
		long nowMs = 1_000_000L;
	}

	private static List<String> proBundle()
	{
		List<String> keys =
			new ArrayList<>(EntitlementsService.FREE_ENTITLEMENTS);
		keys.addAll(Arrays.asList(
			"items.members", "fastflip.full", "priceedge.full",
			"history.full", "settings.strategy", "ge_assist",
			"ads.disabled"));
		return keys;
	}

	@Test
	public void startsAsFreeAndNeverEscalatesOnFailure()
	{
		FakeTransport transport = new FakeTransport();
		List<String> changes = new ArrayList<>();
		EntitlementsService service = new EntitlementsService(
			transport, changes::add, () -> 1_000_000L);

		assertEquals("FREE", service.planCode());
		assertTrue(service.has("core.ge_slots"));
		assertFalse(service.has("ge_assist"));
		assertFalse(service.has("items.members"));

		service.refreshIfStale();
		transport.pendingFailure.run();
		assertEquals("FREE", service.planCode());
		assertTrue(changes.isEmpty());
	}

	@Test
	public void proFetchUnlocksTheBundleAndNotifiesOnce()
	{
		FakeTransport transport = new FakeTransport();
		List<String> changes = new ArrayList<>();
		Clock clock = new Clock();
		EntitlementsService service =
			new EntitlementsService(transport, changes::add, () -> clock.nowMs);

		service.refreshIfStale();
		transport.succeedWith("PRO", proBundle());

		assertEquals("PRO", service.planCode());
		assertTrue(service.has("ge_assist"));
		assertTrue(service.has("items.members"));
		assertEquals(Arrays.asList("PRO"), changes);
	}

	@Test
	public void refreshesOnlyAfterTheTtl()
	{
		FakeTransport transport = new FakeTransport();
		Clock clock = new Clock();
		EntitlementsService service = new EntitlementsService(
			transport, plan -> { }, () -> clock.nowMs);

		service.refreshIfStale();
		transport.succeedWith("PRO", proBundle());
		assertEquals(1, transport.fetches);

		// Fresh: repeated calls collapse into nothing.
		service.refreshIfStale();
		service.refreshIfStale();
		assertEquals(1, transport.fetches);

		clock.nowMs += EntitlementsService.TTL_MS + 1;
		service.refreshIfStale();
		assertEquals(2, transport.fetches);
	}

	@Test
	public void transientFailureKeepsTheLastKnownPlanUntilTheHardExpiry()
	{
		FakeTransport transport = new FakeTransport();
		List<String> changes = new ArrayList<>();
		Clock clock = new Clock();
		EntitlementsService service =
			new EntitlementsService(transport, changes::add, () -> clock.nowMs);

		service.refreshIfStale();
		transport.succeedWith("PRO", proBundle());

		// One failed refetch inside the hard expiry: never abrupt — the
		// open panel keeps its PRO state.
		clock.nowMs += EntitlementsService.TTL_MS + 1;
		service.refreshIfStale();
		transport.pendingFailure.run();
		assertEquals("PRO", service.planCode());

		// Beyond the hard expiry a failing lookup degrades to FREE: a
		// revoked credential can never keep premium alive forever.
		clock.nowMs += EntitlementsService.HARD_EXPIRY_MS;
		service.refreshIfStale();
		transport.pendingFailure.run();
		assertEquals("FREE", service.planCode());
		assertFalse(service.has("ge_assist"));
		assertEquals(Arrays.asList("PRO", "FREE"), changes);
	}

	@Test
	public void unknownPlanCodesStayFree()
	{
		FakeTransport transport = new FakeTransport();
		EntitlementsService service = new EntitlementsService(
			transport, plan -> { }, () -> 1_000_000L);

		service.refreshIfStale();
		transport.succeedWith("PLATINUM", proBundle());
		// The plan LABEL stays FREE for unknown codes; the entitlement keys
		// the server sent still apply (keys are the real contract).
		assertEquals("FREE", service.planCode());
	}

	@Test
	public void resetReturnsToTheFreeFloor()
	{
		FakeTransport transport = new FakeTransport();
		List<String> changes = new ArrayList<>();
		EntitlementsService service = new EntitlementsService(
			transport, changes::add, () -> 1_000_000L);

		service.refreshIfStale();
		transport.succeedWith("PRO", proBundle());
		service.reset();

		assertEquals("FREE", service.planCode());
		assertFalse(service.has("items.members"));
		assertEquals(Arrays.asList("PRO", "FREE"), changes);
	}

	/** Mirror contract: the Java FREE bundle equals packages/shared's
	 *  PLAN_FREE_ENTITLEMENTS (via the bundled fixture — same pattern as
	 *  ProdOverviewContractTest). */
	@Test
	public void freeBundleMatchesTheSharedContractFixture()
	{
		InputStream stream = EntitlementsServiceTest.class
			.getResourceAsStream("/plan-free-entitlements.json");
		assertNotNull("fixture plan-free-entitlements.json missing", stream);
		FreeBundleFixture fixture = new Gson().fromJson(
			new InputStreamReader(stream, StandardCharsets.UTF_8),
			FreeBundleFixture.class);
		assertEquals(
			new HashSet<>(fixture.free),
			new HashSet<>(EntitlementsService.FREE_ENTITLEMENTS));
	}

	private static final class FreeBundleFixture
	{
		List<String> free;
	}

	@Test
	public void freeEntitlementsSetIsImmutable()
	{
		Set<String> bundle = EntitlementsService.FREE_ENTITLEMENTS;
		try
		{
			bundle.add("items.members");
			throw new AssertionError("FREE bundle must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
			// The safe floor can never be widened at runtime.
		}
	}
}
