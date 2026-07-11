package com.runeflip.companion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Selectable GE suggestions (v0.8.18, replaces the v0.8.10 fixed-#1 rule).
 * Every VISIBLE Fast Flip row is selectable by the user's own click on its
 * "Use in GE" button; the selected row carries the "GE selected" chip and is
 * the ONE suggestion the (click-time gated) GE assist may prepare. The
 * behavioral rules live in {@link SuggestionPagerTest} and
 * {@link GeFieldAssistTest}; this test pins the panel copy and the
 * source-level guarantees the compliance story relies on.
 */
public class SelectableGeSuggestionTest
{
	@Test
	public void chipAndButtonCopyAreCompact()
	{
		assertEquals("GE selected", RuneFlipPanel.GE_SELECTED_CHIP);
		assertEquals("Use in GE", RuneFlipPanel.USE_IN_GE_LABEL);
	}

	@Test
	public void closedSearchHintTellsTheUserToOpenItManually()
	{
		// A row click with the GE search closed prepares NOTHING — the
		// selection is kept and the user is told to open the search manually.
		assertEquals("Open GE search to use this item.",
			RuneFlipPanel.OPEN_GE_SEARCH_HINT);
	}

	/**
	 * The suggestion rows must be selectable through plain JButtons only —
	 * never a MouseListener/MouseEvent (globally forbidden and scanned by
	 * {@link ComplianceScanTest}); this pins that the panel actually wires
	 * the button label to the click handler.
	 */
	@Test
	public void rowSelectionGoesThroughThePlainButtonHelper() throws IOException
	{
		String panel = ComplianceScanTest.stripComments(read("RuneFlipPanel.java"));
		assertTrue("the Use in GE button must exist",
			panel.contains("USE_IN_GE_LABEL"));
		assertTrue("the row click must land in the selection handler",
			panel.contains("onSuggestionRowClicked"));
		assertFalse("no MouseListener may ever appear in the panel",
			panel.contains("MouseListener"));
	}

	// ── Copy price/qty removal (v0.8.10) — source-level guarantees ──────────

	/**
	 * The panel must render no "Copy price"/"Copy qty" button anywhere — not
	 * in the TopFastFlipRow, not in the SelectedItemCard, not in the legacy
	 * dashboard card. Comments are stripped first so prose explaining the
	 * removal does not mask a real button. "Copy name" is the one deliberate
	 * survivor (the manual-search aid).
	 */
	@Test
	public void panelRendersNoCopyPriceOrQuantityButtons() throws IOException
	{
		String panel = ComplianceScanTest.stripComments(read("RuneFlipPanel.java"));
		assertFalse(panel.contains("\"Copy price\""));
		assertFalse(panel.contains("\"Copy qty\""));
		assertFalse(panel.contains("\"Copy buy price\""));
		assertFalse(panel.contains("\"Copy sell price\""));
		assertFalse(panel.contains("\"Copy target price\""));
		assertTrue("Copy name stays as the manual-search aid",
			panel.contains("\"Copy name\""));
	}

	/**
	 * Turning enableAssistedOfferSetup ON must revive nothing: the retired
	 * flag is read by no code — the panel and the plugin no longer reference
	 * it, so no value it takes can reach a render decision.
	 */
	@Test
	public void retiredAssistedSetupFlagIsReadByNothing() throws IOException
	{
		String panel = ComplianceScanTest.stripComments(read("RuneFlipPanel.java"));
		assertFalse(panel.contains("enableAssistedOfferSetup"));
		assertFalse(panel.contains("assistedSetup"));

		String plugin = ComplianceScanTest.stripComments(
			read("RuneFlipCompanionPlugin.java"));
		assertFalse(plugin.contains("enableAssistedOfferSetup"));
	}

	private static String read(String fileName) throws IOException
	{
		return new String(
			Files.readAllBytes(
				Paths.get("src/main/java/com/runeflip/companion", fileName)),
			StandardCharsets.UTF_8);
	}
}
