package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pairing exchange safety (v0.6.3): only complete responses are accepted,
 * and every user-visible failure line is generic — it can never contain the
 * pasted code or a token.
 */
public class PairingClientTest
{
	private static RuneFlipData.PairingResponse response(String clientId, String token)
	{
		RuneFlipData.PairingResponse r = new RuneFlipData.PairingResponse();
		r.clientId = clientId;
		r.token = token;
		return r;
	}

	@Test
	public void acceptsOnlyCompletePairingResponses()
	{
		assertTrue(RuneFlipApiClient.isValidPairingResponse(
			response("3f1f9e8a-8f4e-4a5d-9b3c-2f6f1f9e8a4e", "rfp_abc123")));

		assertFalse(RuneFlipApiClient.isValidPairingResponse(null));
		assertFalse(RuneFlipApiClient.isValidPairingResponse(response(null, "rfp_abc123")));
		assertFalse(RuneFlipApiClient.isValidPairingResponse(response("  ", "rfp_abc123")));
		assertFalse(RuneFlipApiClient.isValidPairingResponse(response("client", null)));
		assertFalse(RuneFlipApiClient.isValidPairingResponse(response("client", "   ")));
	}

	@Test
	public void failureMessagesAreHumanReadablePerCode()
	{
		assertEquals(
			"Code invalid, expired or already used — generate a new one.",
			RuneFlipApiClient.describePairingFailure(401));
		assertEquals(
			"This backend has no pairing support yet (needs v0.6.3+).",
			RuneFlipApiClient.describePairingFailure(404));
		assertEquals(
			"Too many attempts — wait a minute and try again.",
			RuneFlipApiClient.describePairingFailure(429));
		assertEquals("Pairing failed (HTTP 500).",
			RuneFlipApiClient.describePairingFailure(500));
	}

	@Test
	public void failureMessagesNeverEchoSecrets()
	{
		String token = "rfp_super-secret-token";
		int[] codes = {400, 401, 404, 429, 500, 503};
		for (int code : codes)
		{
			assertFalse(RuneFlipApiClient.describePairingFailure(code).contains(token));
		}
	}
}
