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
	}

	@Test
	public void searchRowIsExactlyOneNativeRowTall()
	{
		// One row, same height the game uses — the "Start typing…" helper
		// below keeps its usual distance.
		assertEquals(32, GeChatboxHint.SEARCH_ROW_H);
	}

	// ── value line: own row under the qty/price prompt ──────────────────────

	@Test
	public void valueLineSitsOnItsOwnRowBelowThePrompt()
	{
		// The native prompt band renders above NATIVE_PROMPT_BAND_H; the
		// line starts at or below it, so it never steps on the prompt.
		assertTrue(GeChatboxHint.VALUE_LINE_Y
			>= GeChatboxHint.NATIVE_PROMPT_BAND_H);
		assertEquals(GeChatboxHint.VALUE_LINE_Y, GeChatboxHint.valueLineY(142));
	}

	@Test
	public void valueLineClampsIntoShortContainers()
	{
		// Unknown height → the classic own-line slot.
		assertEquals(GeChatboxHint.VALUE_LINE_Y, GeChatboxHint.valueLineY(0));
		// 40 + 16 would overflow a 50px container → bottom-anchored.
		assertEquals(50 - GeChatboxHint.VALUE_LINE_H,
			GeChatboxHint.valueLineY(50));
		// Degenerate containers never yield a negative y.
		assertEquals(0, GeChatboxHint.valueLineY(10));
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
