package com.runeflip.companion;

/**
 * Pure gate deciding whether a sync may run at all. No URL or token — no
 * request. Kept free of RuneLite types so it is trivially unit-testable.
 */
public final class SyncGate
{
	private SyncGate()
	{
	}

	public static boolean canSync(boolean syncEnabled, String backendUrl, String token)
	{
		if (!syncEnabled)
		{
			return false;
		}
		if (BackendUrl.snapshotEndpoint(backendUrl) == null)
		{
			return false;
		}
		return token != null && !token.trim().isEmpty();
	}
}
