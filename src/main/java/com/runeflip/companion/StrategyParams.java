package com.runeflip.companion;

/**
 * StrategyPill support (v0.8.7 design) — pure query arithmetic for the local
 * strategy override. The pills only change WHICH read-only fetch the panel
 * makes (timeframe / risk params on /fast-flip/*): display preferences, never
 * an action, and never written back to the backend's saved preferences.
 *
 * <p>The override lives in the plugin config: {@code 0} / empty = no override
 * (the saved preferences or the backend default apply). A valid override
 * replaces that single param in the already-validated base query built by
 * {@link RuneFlipApiClient#strategyQueryOf}.
 */
public final class StrategyParams
{
	/** The design's timeframe pills, in minutes. */
	static final int[] TIMEFRAME_PILLS = {5, 30, 120, 480};
	/** The design's risk pills. */
	static final String[] RISK_PILLS = {"LOW", "MEDIUM", "HIGH"};

	private StrategyParams()
	{
	}

	/**
	 * Applies the local pill override to a saved-preferences query. Only
	 * whitelisted values ever reach the URL: an unknown risk or out-of-range
	 * timeframe is ignored (the base query stays as-is).
	 */
	public static String override(
		String baseQuery, int timeframeMinutes, String riskLevel)
	{
		String query = baseQuery == null ? "" : baseQuery;
		if (timeframeMinutes >= 1 && timeframeMinutes <= 1440)
		{
			query = replaceParam(
				query, "timeframeMinutes", String.valueOf(timeframeMinutes));
		}
		if (isValidRisk(riskLevel))
		{
			query = replaceParam(query, "riskLevel", riskLevel);
		}
		return query;
	}

	/** True for the three grades the backend accepts. */
	static boolean isValidRisk(String riskLevel)
	{
		if (riskLevel == null)
		{
			return false;
		}
		for (String grade : RISK_PILLS)
		{
			if (grade.equals(riskLevel))
			{
				return true;
			}
		}
		return false;
	}

	/** Drops any existing {@code &name=…} pair, then appends the new value. */
	private static String replaceParam(String query, String name, String value)
	{
		StringBuilder kept = new StringBuilder();
		for (String pair : query.split("&"))
		{
			if (pair.isEmpty() || pair.startsWith(name + "="))
			{
				continue;
			}
			kept.append('&').append(pair);
		}
		return kept + "&" + name + "=" + value;
	}
}
