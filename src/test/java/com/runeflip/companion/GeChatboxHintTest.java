package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Visible chatbox hint layout (v0.8.16 polish) — the pure geometry that
 * keeps the hint on its OWN line, Copilot-style: the search row lives in
 * the native previous-search slot of the results area (below the prompt
 * and typed-input line, above the "Start typing…" helper, clear of the
 * scrollbar and the "Show last searched" checkbox), and the value-editor
 * line sits left-aligned under the qty/price prompt. Rendering itself
 * (widget children on a chatbox layer) stays display-only and is exercised
 * in-game.
 */
public class GeChatboxHintTest
{
	// ── search row: [icon] "RuneFlip item: …" ───────────────────────────────

	@Test
	public void searchRowUsesTheNativePreviousSearchSlot()
	{
		// Top of the results area — the layer starts BELOW the prompt and
		// the typed-input line, so the top slot never overlaps either.
		assertEquals(0, GeChatboxHint.searchRowY(false));
		// With the game's own "Last search:" row showing, the hint drops
		// exactly one native row height — consistent vertical spacing.
		assertEquals(GeChatboxHint.SEARCH_ROW_H, GeChatboxHint.searchRowY(true));
	}

	@Test
	public void searchRowPutsTheIconLeftOfTheText()
	{
		// Icon column first, text right of it, no overlap between the two.
		assertTrue(GeChatboxHint.SEARCH_ICON_X + GeChatboxHint.SEARCH_ICON_W
			<= GeChatboxHint.SEARCH_TEXT_X);
		// Both share the row's left indent — the native row's own indent.
		assertEquals(114, GeChatboxHint.SEARCH_ICON_X);
	}

	@Test
	public void searchRowStaysClearOfScrollbarAndShowLastSearchedControl()
	{
		// The native previous-search row's content ends at x=370; keeping
		// the hint inside that edge leaves the right-hand column (scrollbar
		// + "Show last searched" checkbox) untouched.
		assertTrue(GeChatboxHint.SEARCH_TEXT_X + GeChatboxHint.SEARCH_TEXT_W
			<= 370);
		// The full-row click surface spans exactly the native row content
		// (114..370) — same bound, and it covers icon + text completely so
		// one click anywhere on the row selects.
		assertEquals(370,
			GeChatboxHint.SEARCH_ICON_X + GeChatboxHint.SEARCH_ROW_W);
		assertTrue(GeChatboxHint.SEARCH_TEXT_X + GeChatboxHint.SEARCH_TEXT_W
			<= GeChatboxHint.SEARCH_ICON_X + GeChatboxHint.SEARCH_ROW_W);
	}

	@Test
	public void searchRowIsExactlyOneNativeRowTall()
	{
		// One row, same height the game uses — the "Start typing…" helper
		// below keeps its usual distance.
		assertEquals(32, GeChatboxHint.SEARCH_ROW_H);
	}

	// ── value line: top-left column, third slot (v0.8.17) ───────────────────

	@Test
	public void valueLineSitsTopLeftBelowTheTrackerLines()
	{
		// Left column, not centered: same x indent as the flipping tracker
		// lines ("no buy tracked" / "set to wiki insta buy: …").
		assertEquals(10, GeChatboxHint.VALUE_LINE_X);
		// Directly below the trackers' two slots (y=5 and y=20, text ends
		// at 34) — aligned with them, never over them.
		assertTrue(GeChatboxHint.VALUE_LINE_Y
			>= GeChatboxHint.TRACKER_LINES_BOTTOM);
	}

	@Test
	public void valueLineEndsAboveTheNativePromptAndInput()
	{
		// The centered "Set a price…"/"How many…" prompt starts around
		// y=47 with the typed input below it — the line must end first, so
		// it never steps on the prompt, the asterisk or the input.
		assertTrue(GeChatboxHint.VALUE_LINE_Y + GeChatboxHint.VALUE_LINE_H
			<= GeChatboxHint.NATIVE_PROMPT_TOP);
		assertEquals(GeChatboxHint.VALUE_LINE_Y, GeChatboxHint.valueLineY(142));
	}

	@Test
	public void valueLineClampsIntoShortContainers()
	{
		// Unknown height → the classic top-left slot.
		assertEquals(GeChatboxHint.VALUE_LINE_Y, GeChatboxHint.valueLineY(0));
		// 34 + 13 would overflow a 40px container → bottom-anchored.
		assertEquals(40 - GeChatboxHint.VALUE_LINE_H,
			GeChatboxHint.valueLineY(40));
		// Degenerate containers never yield a negative y.
		assertEquals(0, GeChatboxHint.valueLineY(5));
	}

	@Test
	public void valueLineWidthFollowsTheContainerWithSymmetricMargins()
	{
		assertEquals(495 - 2 * GeChatboxHint.VALUE_LINE_X,
			GeChatboxHint.valueLineWidth(495));
		assertEquals(GeChatboxHint.DEFAULT_VALUE_WIDTH,
			GeChatboxHint.valueLineWidth(0));
		// Absurdly narrow containers never produce a negative width.
		assertEquals(0, GeChatboxHint.valueLineWidth(10));
	}
}
