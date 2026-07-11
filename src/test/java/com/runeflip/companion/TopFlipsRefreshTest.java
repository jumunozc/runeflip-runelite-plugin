package com.runeflip.companion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Top Fast Flips ↻ Refresh hotfix (v0.8.20). The section's Refresh looked
 * dead: it triggered the FULL panel refresh, whose short response cache
 * (20s TTL) served the previous overview right back. These tests pin the
 * fix: the click goes through a dedicated FRESH path that bypasses the
 * cache read, shows a visible in-flight state without clearing the list,
 * and the existing retention rules keep page/selection sane afterwards.
 */
public class TopFlipsRefreshTest
{
	private static RuneFlipData.FastFlipItem item(int itemId)
	{
		RuneFlipData.FastFlipItem flip = new RuneFlipData.FastFlipItem();
		flip.itemId = itemId;
		flip.itemName = "Item " + itemId;
		return flip;
	}

	private static List<RuneFlipData.FastFlipItem> items(int... ids)
	{
		List<RuneFlipData.FastFlipItem> out = new ArrayList<>();
		for (int id : ids)
		{
			out.add(item(id));
		}
		return out;
	}

	// ── the fresh path exists and is the one the button uses ────────────────

	@Test
	public void sectionRefreshUsesTheFreshCallbackNotTheFullRefresh()
		throws IOException
	{
		String panel = ComplianceScanTest.stripComments(read("RuneFlipPanel.java"));
		// The section link is wired to the dedicated click handler…
		assertTrue(panel.contains("this::onFastFlipRefreshClicked"));
		// …which hands off to the Fast-Flip-only fresh callback.
		assertTrue(panel.contains("onFastFlipRefresh.accept(fastFlipPage)"));
	}

	@Test
	public void freshFetchBypassesTheShortCache() throws IOException
	{
		String plugin = ComplianceScanTest.stripComments(
			read("RuneFlipCompanionPlugin.java"));
		// The load path takes a bypass flag and only reads the cache without it.
		assertTrue(plugin.contains("boolean bypassCache"));
		assertTrue(plugin.contains("if (!bypassCache)"));
		// The click handler forces the bypass and keeps the CURRENT strategy
		// (same overriddenQuery path as every other fast-flip fetch).
		assertTrue(plugin.contains("loadFastFlip(url, query, clientId, target, true)"));
		assertTrue(plugin.contains("private void onTopFlipsRefresh(int page)"));
	}

	@Test
	public void refreshClickLogsTheSafeDiagnosticOnly() throws IOException
	{
		String plugin = ComplianceScanTest.stripComments(
			read("RuneFlipCompanionPlugin.java"));
		// The requested debug line, with strategy/page/selection/cache state.
		assertTrue(plugin.contains("Top Fast Flips refresh clicked"));
		int at = plugin.indexOf("private void onTopFlipsRefresh");
		String method = plugin.substring(at, plugin.indexOf("\n\t}", at));
		assertTrue(method.contains("cacheFresh"));
		assertTrue(method.contains("page"));
		// Never a token or the client id inside the diagnostic format.
		int dbg = method.indexOf("log.debug");
		String logCall = method.substring(dbg, method.indexOf(";", dbg));
		assertFalse(logCall.contains("clientId"));
		assertFalse(logCall.contains("token"));
	}

	// ── the click never clears the list; the response re-enables the button ─

	@Test
	public void clickShowsInFlightStateWithoutClearingTheList() throws IOException
	{
		String panel = read("RuneFlipPanel.java");
		int at = panel.indexOf("private void onFastFlipRefreshClicked");
		assertTrue("the click handler must exist", at >= 0);
		String method = panel.substring(at, panel.indexOf("\n\t}", at));
		// Visible feedback: "Updating…" + disabled button while in flight.
		assertTrue(method.contains("updatingLabel.setVisible(true)"));
		assertTrue(method.contains("setEnabled(false)"));
		// The old rows stay on screen: the handler never rebuilds the card
		// — only the arriving response (updateFastFlip) does.
		assertFalse(method.contains("removeAll"));
		assertFalse(method.contains("renderFastFlip"));

		// And the response path re-enables the button (the only setEnabled(true)
		// on that button lives in updateFastFlip).
		assertTrue(panel.contains("fastFlipRefreshButton.setEnabled(true)"));
	}

	// ── retention after the fresh response (pure rules, unchanged) ──────────

	@Test
	public void pageSurvivesWhenStillValidAndClampsWhenNot()
	{
		// 7 rows → page 2 (items 7) is valid and stays.
		assertEquals(2, SuggestionPager.clampPage(2, 7, 3));
		// Fresh list shrank to 4 rows → page 2 clamps to the last valid page.
		assertEquals(1, SuggestionPager.clampPage(2, 4, 3));
		// The label follows the clamped page.
		assertEquals("Suggestion 4", SuggestionPager.pageLabel(2, 4, 3));
	}

	@Test
	public void selectionSurvivesWhenStillListedAndFallsBackWhenGone()
	{
		// Pinned #5 still in the fresh list → kept (fresh row object).
		assertEquals(5, SuggestionPager.effectiveSelection(
			items(1, 2, 3, 4, 5, 6), 5, 1, 3).itemId);
		// Pinned item gone → the visible default of the current page.
		assertEquals(4, SuggestionPager.effectiveSelection(
			items(1, 2, 3, 4, 5, 6), 99, 1, 3).itemId);
		// Plugin-side retention mirrors it.
		assertEquals(2, SuggestionPager.retainSelection(
			items(1, 2, 3), item(2)).itemId);
		assertEquals(1, SuggestionPager.retainSelection(
			items(1, 2, 3), item(99)).itemId);
		assertNull(SuggestionPager.retainSelection(items(), item(99)));
	}

	// ── the fresh path can never stomp the selected-item card ───────────────

	@Test
	public void freshRefreshOnlyTouchesTheFastFlipCard() throws IOException
	{
		String plugin = read("RuneFlipCompanionPlugin.java");
		int at = plugin.indexOf("private void onTopFlipsRefresh");
		String method = plugin.substring(at, plugin.indexOf("\n\t}", at));
		// It only re-renders the Fast Flip card — never the selected-item
		// context (no context fetch, no selection clear).
		assertFalse(method.contains("fetchItemContext"));
		assertFalse(method.contains("lastSelectedGeItem"));
		assertFalse(method.contains("updateSelected"));
		// And with a GE item open, the selected card keeps priority over the
		// Top list regardless of what the fast-flip card re-renders.
		assertTrue(PanelVisibility.showSelectedItem(true, true));
		assertFalse(PanelVisibility.showTopThree(true, true));
	}

	private static String read(String fileName) throws IOException
	{
		return new String(
			Files.readAllBytes(
				Paths.get("src/main/java/com/runeflip/companion", fileName)),
			StandardCharsets.UTF_8);
	}
}
