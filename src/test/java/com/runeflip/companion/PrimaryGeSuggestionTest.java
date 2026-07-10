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
 * Primary GE suggestion (v0.8.10). The FIRST row of the rendered Fast Flip
 * selection — and only it — is RuneFlip's primary suggestion for the user's
 * next manual GE search (Flipping-Copilot style). The chip itself stays
 * display-only; since v0.8.11 the #1 may additionally be PREPARED into the
 * GE search, but only via the click-gated "RuneFlip: select …" option in
 * GeFieldAssistService — never from the panel. The v0.8.10 patch also
 * removed the v0.8.3 Copy price/qty buttons everywhere: the game accepts no
 * paste, so they assisted nothing.
 */
public class PrimaryGeSuggestionTest
{
	@Test
	public void firstTopRowIsThePrimarySuggestion()
	{
		assertTrue(RuneFlipPanel.isPrimaryGeSuggestion(
			FastFlipSelection.Source.TOP, 1));
	}

	@Test
	public void secondAndThirdRowsAreNeverPrimary()
	{
		assertFalse(RuneFlipPanel.isPrimaryGeSuggestion(
			FastFlipSelection.Source.TOP, 2));
		assertFalse(RuneFlipPanel.isPrimaryGeSuggestion(
			FastFlipSelection.Source.TOP, 3));
		assertFalse(RuneFlipPanel.isPrimaryGeSuggestion(
			FastFlipSelection.Source.GENERAL, 2));
	}

	@Test
	public void generalIdeasFallbackStillMarksItsFirstRow()
	{
		// When the strategy matched nothing and the liquid "General ideas"
		// render instead, the user still gets exactly one search suggestion.
		assertTrue(RuneFlipPanel.isPrimaryGeSuggestion(
			FastFlipSelection.Source.GENERAL, 1));
	}

	@Test
	public void emptyAndOfflineStatesHaveNoSuggestion()
	{
		assertFalse(RuneFlipPanel.isPrimaryGeSuggestion(
			FastFlipSelection.Source.NONE, 1));
	}

	@Test
	public void chipCopyIsCompact()
	{
		assertEquals("GE suggestion", RuneFlipPanel.GE_SUGGESTION_CHIP);
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
