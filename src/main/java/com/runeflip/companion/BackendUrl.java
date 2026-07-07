package com.runeflip.companion;

/**
 * Normalizes the user-entered backend URL into the snapshot endpoint.
 * Accepts "http://host:3005", "http://host:3005/", "http://host:3005/api"
 * and "http://host:3005/api/" — all resolve to ".../api/ge-slots/snapshot".
 */
public final class BackendUrl
{
	private BackendUrl()
	{
	}

	/** Base API URL ending in "/api" (no trailing slash), or null if unusable. */
	public static String normalize(String raw)
	{
		if (raw == null)
		{
			return null;
		}
		String url = raw.trim();
		if (url.isEmpty())
		{
			return null;
		}
		if (!url.startsWith("http://") && !url.startsWith("https://"))
		{
			return null;
		}
		while (url.endsWith("/"))
		{
			url = url.substring(0, url.length() - 1);
		}
		if (url.equals("http:/") || url.equals("https:/") || url.equals("http:") || url.equals("https:"))
		{
			return null;
		}
		if (!url.endsWith("/api"))
		{
			url = url + "/api";
		}
		return url;
	}

	/** Full ingest endpoint, or null when the base URL is unusable. */
	public static String snapshotEndpoint(String raw)
	{
		String base = normalize(raw);
		return base == null ? null : base + "/ge-slots/snapshot";
	}

	/** Capital ingest endpoint, or null when the base URL is unusable. */
	public static String capitalEndpoint(String raw)
	{
		String base = normalize(raw);
		return base == null ? null : base + "/player-capital/snapshot";
	}
}
