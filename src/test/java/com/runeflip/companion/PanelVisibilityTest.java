package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Contextual panel focus rules (v0.8.5). The panel must show exactly one of the
 * selected-item card or the Top 3, and — in contextual mode — hide the legacy
 * dashboard and the GE completed summary. These pin that logic without a live
 * Swing panel.
 */
public class PanelVisibilityTest
{
	@Test
	public void contextualWithSelectedItemShowsOnlyTheContextCard()
	{
		boolean contextual = true;
		boolean hasSelection = true;
		// (a) selected item => no old recommendation list, no GE completed.
		assertFalse(PanelVisibility.showLegacyDashboard(contextual));
		assertFalse(PanelVisibility.showGeCompleted(contextual));
		assertTrue(PanelVisibility.showSelectedItem(contextual, hasSelection));
		assertFalse(PanelVisibility.showTopThree(contextual, hasSelection));
	}

	@Test
	public void contextualWithNoSelectionShowsOnlyTopThree()
	{
		boolean contextual = true;
		boolean hasSelection = false;
		// (b) no selected item => Top 3 only.
		assertFalse(PanelVisibility.showLegacyDashboard(contextual));
		assertFalse(PanelVisibility.showGeCompleted(contextual));
		assertFalse(PanelVisibility.showSelectedItem(contextual, hasSelection));
		assertTrue(PanelVisibility.showTopThree(contextual, hasSelection));
	}

	@Test
	public void legacyModeKeepsTheFullDashboardAndNeverTheContextCard()
	{
		boolean contextual = false;
		// (c) contextualGePanel=false => legacy dashboard + completed, no context.
		assertTrue(PanelVisibility.showLegacyDashboard(contextual));
		assertTrue(PanelVisibility.showGeCompleted(contextual));
		assertFalse(PanelVisibility.showSelectedItem(contextual, true));
		assertFalse(PanelVisibility.showSelectedItem(contextual, false));
		// The Top-3 Fast Flip card is part of the legacy panel too.
		assertTrue(PanelVisibility.showTopThree(contextual, true));
		assertTrue(PanelVisibility.showTopThree(contextual, false));
	}

	@Test
	public void selectedItemAndTopThreeAreMutuallyExclusive()
	{
		for (boolean ctx : new boolean[] {true, false})
		{
			for (boolean sel : new boolean[] {true, false})
			{
				assertFalse(
					"selected and top-3 must never both show",
					PanelVisibility.showSelectedItem(ctx, sel)
						&& PanelVisibility.showTopThree(ctx, sel));
			}
		}
	}
}
