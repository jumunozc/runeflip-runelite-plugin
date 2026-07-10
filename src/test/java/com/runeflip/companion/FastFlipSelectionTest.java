package com.runeflip.companion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Fast Flip row selection + empty-state text (hotfix v0.8.5-c). These pin the
 * fix for the contextual panel showing "Fast flip · 0" while the web still had a
 * Top Fast Flip: the panel used to read ONLY {@code topFlips}, so a restrictive
 * saved strategy that emptied the Top ranking blanked the whole card even when
 * the overview still carried fast-buy / fast-sell candidates.
 *
 * <p>Goal coverage: (a) Top with 3 → 3 rows; (b) nothing matches → NONE + the
 * strategy/suggestion text; (c) Top empty but overview has items → General
 * fallback; (d)/(e) contextual focus + selected-over-Top priority (asserted via
 * the pure {@link PanelVisibility} helper the panel uses).
 */
public class FastFlipSelectionTest
{
	private static RuneFlipData.FastFlipItem item(int itemId)
	{
		RuneFlipData.FastFlipItem flip = new RuneFlipData.FastFlipItem();
		flip.itemId = itemId;
		flip.itemName = "Item " + itemId;
		return flip;
	}

	private static RuneFlipData.FastFlipOverviewResponse overview(
		List<RuneFlipData.FastFlipItem> fastBuy,
		List<RuneFlipData.FastFlipItem> fastSell,
		List<RuneFlipData.FastFlipItem> topFlips)
	{
		RuneFlipData.FastFlipOverviewResponse response =
			new RuneFlipData.FastFlipOverviewResponse();
		response.fastBuy = fastBuy;
		response.fastSell = fastSell;
		response.topFlips = topFlips;
		return response;
	}

	// (a) Top endpoint with items => render exactly those Top rows (capped at 3).
	@Test
	public void topRankingIsUsedWhenPresent()
	{
		RuneFlipData.FastFlipOverviewResponse response = overview(
			Arrays.asList(item(1), item(2)),
			new ArrayList<>(),
			Arrays.asList(item(10), item(11), item(12)));

		FastFlipSelection selection = FastFlipSelection.select(response, 3);

		assertEquals(FastFlipSelection.Source.TOP, selection.source);
		assertEquals(3, selection.rows.size());
		assertEquals(10, selection.rows.get(0).itemId);
		assertEquals(12, selection.rows.get(2).itemId);
	}

	@Test
	public void topRankingIsCappedAtMax()
	{
		RuneFlipData.FastFlipOverviewResponse response = overview(
			new ArrayList<>(), new ArrayList<>(),
			Arrays.asList(item(10), item(11), item(12), item(13), item(14)));

		FastFlipSelection selection = FastFlipSelection.select(response, 3);

		assertEquals(FastFlipSelection.Source.TOP, selection.source);
		assertEquals(3, selection.rows.size());
	}

	// (c) Top empty but the overview still has fast-buy/-sell candidates =>
	// General-ideas fallback, de-duplicated by itemId, order preserved.
	@Test
	public void emptyTopFallsBackToGeneralIdeas()
	{
		RuneFlipData.FastFlipOverviewResponse response = overview(
			Arrays.asList(item(1), item(2)),
			Arrays.asList(item(2), item(3)), // item 2 duplicates fast-buy
			new ArrayList<>());

		FastFlipSelection selection = FastFlipSelection.select(response, 3);

		assertEquals(FastFlipSelection.Source.GENERAL, selection.source);
		assertEquals(Arrays.asList(1, 2, 3),
			Arrays.asList(
				selection.rows.get(0).itemId,
				selection.rows.get(1).itemId,
				selection.rows.get(2).itemId));
	}

	@Test
	public void generalFallbackIsCappedAtMaxAndPrefersFastBuy()
	{
		RuneFlipData.FastFlipOverviewResponse response = overview(
			Arrays.asList(item(1), item(2), item(3), item(4)),
			Arrays.asList(item(5)),
			new ArrayList<>());

		FastFlipSelection selection = FastFlipSelection.select(response, 3);

		assertEquals(FastFlipSelection.Source.GENERAL, selection.source);
		assertEquals(3, selection.rows.size());
		assertEquals(1, selection.rows.get(0).itemId);
		assertEquals(3, selection.rows.get(2).itemId);
	}

	// (b) Nothing matches anywhere => NONE, no rows (the panel then shows the
	// strategy + relax hints instead of a blank box).
	@Test
	public void allEmptyYieldsNone()
	{
		FastFlipSelection selection = FastFlipSelection.select(
			overview(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()), 3);

		assertEquals(FastFlipSelection.Source.NONE, selection.source);
		assertTrue(selection.rows.isEmpty());
	}

	@Test
	public void nullResponseOrNonPositiveMaxYieldsNone()
	{
		assertEquals(FastFlipSelection.Source.NONE,
			FastFlipSelection.select(null, 3).source);
		assertEquals(FastFlipSelection.Source.NONE,
			FastFlipSelection.select(
				overview(Arrays.asList(item(1)), null, null), 0).source);
	}

	@Test
	public void nullSectionsAndNullEntriesAreTolerated()
	{
		RuneFlipData.FastFlipOverviewResponse response = overview(
			Arrays.asList(null, item(7)), null, null);

		FastFlipSelection selection = FastFlipSelection.select(response, 3);

		assertEquals(FastFlipSelection.Source.GENERAL, selection.source);
		assertEquals(1, selection.rows.size());
		assertEquals(7, selection.rows.get(0).itemId);
	}

	// (b) Empty-state copy: the strategy line + the relax suggestions.
	@Test
	public void emptyStateShowsStrategyAndSuggestions()
	{
		RuneFlipData.FastFlipStrategy strategy =
			new RuneFlipData.FastFlipStrategy();
		strategy.description = "5m timeframe · risk up to LOW · min profit 50,000 gp";
		assertEquals("Strategy: 5m timeframe · risk up to LOW · min profit 50,000 gp",
			RuneFlipPanel.emptyStrategyLine(strategy));

		// Pre-0.8.0 backend (no echo) still names a strategy, never blank.
		assertEquals("Strategy: default (risk up to HIGH)",
			RuneFlipPanel.emptyStrategyLine(null));

		// The concrete relax hint names the levers a user can loosen.
		assertTrue(RuneFlipPanel.NO_MATCH_LINE.contains("No matches"));
		assertTrue(RuneFlipPanel.RELAX_HINT_LINE.contains("Medium risk"));
		assertTrue(RuneFlipPanel.RELAX_HINT_LINE.contains("30m"));
		assertTrue(RuneFlipPanel.RELAX_HINT_LINE.contains("min profit"));
	}

	@Test
	public void fastFlipFooterStatesRuneFlipNeverConfirms()
	{
		assertTrue(RuneFlipPanel.FAST_FLIP_FOOTER.contains("Review manually"));
		assertTrue(RuneFlipPanel.FAST_FLIP_FOOTER.contains("never confirms trades"));
	}

	// (d) Contextual mode hides the legacy dashboard + GE completed; (e) a
	// selected item takes priority over the Top 3 (mutually exclusive).
	@Test
	public void contextualFocusAndSelectedItemPriority()
	{
		// (d) contextual mode: no legacy list, no GE completed.
		assertEquals(false, PanelVisibility.showLegacyDashboard(true));
		assertEquals(false, PanelVisibility.showGeCompleted(true));

		// (e) with an item selected, the Top 3 yields to the context card.
		assertEquals(true, PanelVisibility.showSelectedItem(true, true));
		assertEquals(false, PanelVisibility.showTopThree(true, true));
		// with no selection, contextual mode shows the Top 3.
		assertEquals(true, PanelVisibility.showTopThree(true, false));
	}

	@Test
	public void selectionRowsAreTheBackendItemsVerbatim()
	{
		RuneFlipData.FastFlipItem flip = item(4151);
		FastFlipSelection selection = FastFlipSelection.select(
			overview(null, null, Arrays.asList(flip)), 3);
		// The panel renders backend figures verbatim — same object, not a copy.
		assertSame(flip, selection.rows.get(0));
	}
}
