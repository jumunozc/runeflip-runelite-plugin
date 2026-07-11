package com.runeflip.companion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Selectable, paginated suggestions (v0.8.18) — the pure math behind the
 * panel's pages and the selected GE suggestion. Pins the goal's rules:
 * pages of 3 (1–3, 4–6, …), Next disabled on the last page, an empty page
 * after a refresh clamps back to a valid one, a click pins the selection,
 * the default is the FIRST row of the current page, and an item that is not
 * loaded/visible can never become the selection the assist prepares.
 */
public class SuggestionPagerTest
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

	private static List<Integer> ids(List<RuneFlipData.FastFlipItem> rows)
	{
		List<Integer> out = new ArrayList<>();
		for (RuneFlipData.FastFlipItem row : rows)
		{
			out.add(row.itemId);
		}
		return out;
	}

	// ── paging ───────────────────────────────────────────────────────────────

	@Test
	public void pageOneShowsItemsOneToThreeAndPageTwoTheNextThree()
	{
		List<RuneFlipData.FastFlipItem> rows = items(1, 2, 3, 4, 5, 6, 7);

		assertEquals(Arrays.asList(1, 2, 3), ids(SuggestionPager.pageRows(rows, 0, 3)));
		assertEquals(Arrays.asList(4, 5, 6), ids(SuggestionPager.pageRows(rows, 1, 3)));
		// Honest short last page.
		assertEquals(Arrays.asList(7), ids(SuggestionPager.pageRows(rows, 2, 3)));
	}

	@Test
	public void nextIsDisabledOnTheLastPageAndPreviousOnTheFirst()
	{
		assertFalse(SuggestionPager.hasPrevious(0));
		assertTrue(SuggestionPager.hasPrevious(1));

		assertTrue(SuggestionPager.hasNext(0, 7, 3));
		assertTrue(SuggestionPager.hasNext(1, 7, 3));
		assertFalse(SuggestionPager.hasNext(2, 7, 3));
		// A single page never pages.
		assertFalse(SuggestionPager.hasNext(0, 3, 3));
		assertFalse(SuggestionPager.hasNext(0, 0, 3));
	}

	@Test
	public void aPageLeftEmptyByARefreshClampsBackToAValidOne()
	{
		// The user was on page 3 (items 7–9); the refresh shrank the list to 4.
		assertEquals(1, SuggestionPager.clampPage(2, 4, 3));
		// Shrunk to nothing → page 0, and its rows are simply empty.
		assertEquals(0, SuggestionPager.clampPage(2, 0, 3));
		assertTrue(SuggestionPager.pageRows(items(), 2, 3).isEmpty());
		// Negative input never breaks the math.
		assertEquals(0, SuggestionPager.clampPage(-1, 7, 3));
	}

	@Test
	public void pageLabelNamesTheVisibleRange()
	{
		assertEquals("Suggestions 1–3", SuggestionPager.pageLabel(0, 7, 3));
		assertEquals("Suggestions 4–6", SuggestionPager.pageLabel(1, 7, 3));
		assertEquals("Suggestion 7", SuggestionPager.pageLabel(2, 7, 3));
	}

	// ── selection ────────────────────────────────────────────────────────────

	@Test
	public void clickingAVisibleRowPinsThatSuggestion()
	{
		List<RuneFlipData.FastFlipItem> rows = items(1, 2, 3, 4, 5, 6);

		// The user clicked #2 — the selection is #2, not the #1 default.
		assertEquals(2, SuggestionPager.effectiveSelection(rows, 2, 0, 3).itemId);
		// #3 is just as selectable by its own explicit click.
		assertEquals(3, SuggestionPager.effectiveSelection(rows, 3, 0, 3).itemId);
	}

	@Test
	public void withoutAPinTheDefaultIsTheFirstRowOfTheCurrentPage()
	{
		List<RuneFlipData.FastFlipItem> rows = items(1, 2, 3, 4, 5, 6);

		assertEquals(1, SuggestionPager.effectiveSelection(rows, null, 0, 3).itemId);
		// Page 2 without a pin → its own first row (#4), never a hidden item.
		assertEquals(4, SuggestionPager.effectiveSelection(rows, null, 1, 3).itemId);
	}

	@Test
	public void aPinnedSelectionSurvivesPageFlips()
	{
		List<RuneFlipData.FastFlipItem> rows = items(1, 2, 3, 4, 5, 6);

		// The user pinned #2 and then paged to 4–6: the selection stays #2.
		assertEquals(2, SuggestionPager.effectiveSelection(rows, 2, 1, 3).itemId);
	}

	@Test
	public void anUnloadedItemCanNeverBeTheSelection()
	{
		List<RuneFlipData.FastFlipItem> rows = items(1, 2, 3);

		// A pin for an item the refreshed list no longer carries falls back
		// to the visible default — a stale/unloaded item never selects.
		assertEquals(1, SuggestionPager.effectiveSelection(rows, 99, 0, 3).itemId);
		assertNull(SuggestionPager.byItemId(rows, 99));
		// An empty list has no selection at all.
		assertNull(SuggestionPager.effectiveSelection(items(), 2, 0, 3));
	}

	// ── plugin refresh rule ──────────────────────────────────────────────────

	@Test
	public void refreshKeepsTheSelectedItemWithFreshFigures()
	{
		RuneFlipData.FastFlipItem stale = item(2);
		List<RuneFlipData.FastFlipItem> fresh = items(1, 2, 3);

		RuneFlipData.FastFlipItem kept =
			SuggestionPager.retainSelection(fresh, stale);

		// Same item, but the NEW row object — fresh backend figures.
		assertEquals(2, kept.itemId);
		assertSame(fresh.get(1), kept);
	}

	@Test
	public void refreshDropsASuggestionTheBackendStoppedListing()
	{
		assertEquals(1, SuggestionPager.retainSelection(
			items(1, 2, 3), item(99)).itemId);
		assertNull(SuggestionPager.retainSelection(items(), item(99)));
		assertNull(SuggestionPager.retainSelection(null, item(99)));
		assertEquals(1, SuggestionPager.retainSelection(items(1), null).itemId);
	}
}
