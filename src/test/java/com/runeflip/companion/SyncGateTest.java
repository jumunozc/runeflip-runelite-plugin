package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SyncGateTest
{
	private static final String URL = "http://localhost:3005/api";
	private static final String TOKEN = "local-dev-runeflip-token";

	@Test
	public void syncsOnlyWithUrlAndTokenPresent()
	{
		assertTrue(SyncGate.canSync(true, URL, TOKEN));
	}

	@Test
	public void neverSyncsWhenDisabled()
	{
		assertFalse(SyncGate.canSync(false, URL, TOKEN));
	}

	@Test
	public void neverSyncsWithoutToken()
	{
		assertFalse(SyncGate.canSync(true, URL, null));
		assertFalse(SyncGate.canSync(true, URL, ""));
		assertFalse(SyncGate.canSync(true, URL, "   "));
	}

	@Test
	public void neverSyncsWithoutUsableUrl()
	{
		assertFalse(SyncGate.canSync(true, null, TOKEN));
		assertFalse(SyncGate.canSync(true, "", TOKEN));
		assertFalse(SyncGate.canSync(true, "not-a-url", TOKEN));
	}
}
