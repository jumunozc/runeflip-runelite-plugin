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
 * Selected-item stability (v0.8.14). Real-play regression: selecting a GE
 * item showed the loading card for a moment and then FELL BACK to the Top 3
 * — caused by (1) the old deployed backend rejecting the new `side` query
 * param (fetch failed → card cleared) and (2) the selection var flickering
 * to -1 during buy↔sell/search transitions. These tests pin the rules that
 * keep the selected view up: a valid item always beats the Top 3, an empty
 * read only clears after a grace period, and a failed fetch NEVER clears.
 */
public class SelectedItemStabilityTest
{
	// ── (a) loading state keeps the Top 3 hidden ─────────────────────────────

	@Test
	public void selectedLoadingViewNeverShowsTheTopThree()
	{
		// showSelectedItemLoading() sets hasSelection=true; in contextual mode
		// the Top 3 stays hidden and the selected card owns the panel.
		assertFalse(PanelVisibility.showTopThree(true, true));
		assertTrue(PanelVisibility.showSelectedItem(true, true));
	}

	// ── (b)/(c) empty selection reads: grace, then Top 3 ─────────────────────

	@Test
	public void emptyReadArmsTheGraceOnceAndKeepsTheView()
	{
		long armed = SelectedItemStability.clearDueAfter(-1, 10_000, 0);
		assertEquals(10_000 + SelectedItemStability.GRACE_MS, armed);
		// Repeated empty flickers do NOT push the deadline forward.
		assertEquals(armed, SelectedItemStability.clearDueAfter(-1, 10_300, armed));
		// Inside the grace the selected view is kept…
		assertTrue(SelectedItemStability.keepSelectedView(-1, armed - 1, armed));
		assertFalse(SelectedItemStability.shouldClear(-1, armed - 1, armed));
	}

	@Test
	public void stillEmptyAfterTheGraceReturnsToTheTopThree()
	{
		long armed = SelectedItemStability.clearDueAfter(0, 10_000, 0);
		assertTrue(SelectedItemStability.shouldClear(0, armed, armed));
		assertFalse(SelectedItemStability.keepSelectedView(0, armed + 1, armed));
	}

	@Test
	public void aValidItemCancelsThePendingClear()
	{
		long armed = SelectedItemStability.clearDueAfter(-1, 10_000, 0);
		// The user reopened (or the transition finished) within the grace:
		// the pending clear is cancelled and the view stays selected.
		assertEquals(0, SelectedItemStability.clearDueAfter(4151, 10_400, armed));
		assertTrue(SelectedItemStability.keepSelectedView(4151, 10_400, 0));
		assertFalse(SelectedItemStability.shouldClear(4151, 99_999, armed));
	}

	// ── (d) an overview response cannot stomp the selected view ─────────────

	@Test
	public void overviewRendersOnlyIntoTheHiddenTopThreeWhileSelected()
	{
		// updateFastFlip only rebuilds the Fast Flip card; while an item is
		// selected that card is not visible (PanelVisibility), so no overview
		// response can replace the selected view.
		assertFalse(PanelVisibility.showTopThree(true, true));
		// Without a selection the Top 3 is back — the normal state.
		assertTrue(PanelVisibility.showTopThree(true, false));
	}

	// ── (e) recommended:false on SELL keeps the card (regression pin) ───────

	@Test
	public void notRecommendedSellWithTargetsStillShowsTheCard()
	{
		RuneFlipData.FastFlipItemContextResponse res =
			new RuneFlipData.FastFlipItemContextResponse();
		res.recommended = Boolean.FALSE;
		res.priceEdge = new RuneFlipData.PriceEdge();
		res.priceEdge.recommendedSellPrice = 1_049L;
		assertTrue(RuneFlipPanel.showFullContextCard(res, "SELL"));
	}

	// ── (f) a failed fetch never clears the selection ────────────────────────

	/**
	 * Source-level pin: the ONLY code path allowed to clear the selected card
	 * is the post-grace check ({@code maybeClearSelection}) — fetch failures
	 * keep the retained card (+ discreet stale note) or show the
	 * "unavailable" card, and the empty-var handler only arms the grace.
	 */
	@Test
	public void onlyThePostGraceCheckMayClearTheSelection() throws IOException
	{
		String plugin = ComplianceScanTest.stripComments(new String(
			Files.readAllBytes(Paths.get(
				"src/main/java/com/runeflip/companion/RuneFlipCompanionPlugin.java")),
			StandardCharsets.UTF_8));
		int occurrences = 0;
		int index = 0;
		while ((index = plugin.indexOf("clearSelectedItem", index)) >= 0)
		{
			occurrences++;
			index++;
		}
		assertEquals("clearSelectedItem must be reachable only from the "
			+ "post-grace check", 1, occurrences);
		assertTrue("the clear must be gated by the stability decision",
			plugin.contains("SelectedItemStability.shouldClear"));
		assertTrue("fetch failures must keep the selection",
			plugin.contains("showSelectedItemUnavailable")
				&& plugin.contains("STALE_CONTEXT_NOTE"));
	}

	@Test
	public void unavailableAndStaleCopyKeepTheSelectionLanguage()
	{
		assertTrue(RuneFlipPanel.ITEM_UNAVAILABLE_LINE.contains("Keeping your selection"));
		assertTrue(RuneFlipPanel.STALE_CONTEXT_NOTE.contains("last RuneFlip context"));
	}
}
