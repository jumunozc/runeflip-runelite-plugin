package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * The log/status lines derived from backend responses must be stable,
 * human-readable, and can never contain the token (they are built from the
 * status code alone).
 */
public class ResponseLoggingTest
{
	private static final String TOKEN = "local-dev-runeflip-token";

	@Test
	public void describesEveryContractStatusCode()
	{
		assertEquals("ok", RuneFlipCompanionPlugin.describeResponse(200));
		assertEquals(
			"400 rejected payload (plugin/backend version mismatch?)",
			RuneFlipCompanionPlugin.describeResponse(400));
		assertEquals(
			"401 unauthorized (check the ingest token in the plugin settings)",
			RuneFlipCompanionPlugin.describeResponse(401));
		assertEquals(
			"429 rate limited (backend will accept again shortly)",
			RuneFlipCompanionPlugin.describeResponse(429));
		assertEquals(
			"503 ingest disabled (set OSRS_GE_INGEST_TOKEN in the backend .env)",
			RuneFlipCompanionPlugin.describeResponse(503));
		assertEquals("HTTP 418", RuneFlipCompanionPlugin.describeResponse(418));
	}

	@Test
	public void statusLinesNeverContainAToken()
	{
		int[] codes = {200, 400, 401, 429, 503, 500};
		for (int code : codes)
		{
			assertFalse(RuneFlipCompanionPlugin.describeResponse(code).contains(TOKEN));
		}
	}
}
