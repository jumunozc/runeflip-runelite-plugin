package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * StrategyPill override arithmetic (v0.8.7 design). The pills only tune the
 * read-only fetch params; anything invalid degrades to the base query so a
 * corrupted config can never produce a malformed URL.
 */
public class StrategyParamsTest
{
	@Test
	public void noOverrideKeepsTheBaseQueryVerbatim()
	{
		assertEquals("", StrategyParams.override("", 0, ""));
		assertEquals("&timeframeMinutes=30&riskLevel=MEDIUM",
			StrategyParams.override("&timeframeMinutes=30&riskLevel=MEDIUM", 0, null));
	}

	@Test
	public void timeframePillReplacesOnlyTheTimeframeParam()
	{
		String query = StrategyParams.override(
			"&timeframeMinutes=30&riskLevel=MEDIUM&minPredictedProfit=25000",
			480, "");
		assertTrue(query.contains("&timeframeMinutes=480"));
		assertFalse(query.contains("timeframeMinutes=30"));
		// Saved floors and risk survive untouched.
		assertTrue(query.contains("&riskLevel=MEDIUM"));
		assertTrue(query.contains("&minPredictedProfit=25000"));
	}

	@Test
	public void riskPillReplacesOnlyTheRiskParam()
	{
		String query = StrategyParams.override(
			"&timeframeMinutes=30&riskLevel=MEDIUM", 0, "HIGH");
		assertTrue(query.contains("&riskLevel=HIGH"));
		assertFalse(query.contains("riskLevel=MEDIUM"));
		assertTrue(query.contains("&timeframeMinutes=30"));
	}

	@Test
	public void overridesApplyOnTopOfAnEmptyBaseToo()
	{
		String query = StrategyParams.override("", 30, "MEDIUM");
		assertTrue(query.contains("&timeframeMinutes=30"));
		assertTrue(query.contains("&riskLevel=MEDIUM"));
	}

	@Test
	public void invalidOverridesNeverReachTheUrl()
	{
		assertEquals("", StrategyParams.override("", -5, "BANANA"));
		assertEquals("", StrategyParams.override("", 100_000, null));
		String base = "&riskLevel=LOW";
		assertEquals(base, StrategyParams.override(base, 0, "banana"));
	}

	@Test
	public void pillSetsMatchTheDesign()
	{
		assertEquals(4, StrategyParams.TIMEFRAME_PILLS.length);
		assertEquals(5, StrategyParams.TIMEFRAME_PILLS[0]);
		assertEquals(480, StrategyParams.TIMEFRAME_PILLS[3]);
		assertEquals(3, StrategyParams.RISK_PILLS.length);
		assertTrue(StrategyParams.isValidRisk("MEDIUM"));
		assertFalse(StrategyParams.isValidRisk("AVOID"));
		// Pill labels: enum → human case.
		assertEquals("Low", RuneFlipPanel.riskPillLabel("LOW"));
		assertEquals("Medium", RuneFlipPanel.riskPillLabel("MEDIUM"));
	}
}
