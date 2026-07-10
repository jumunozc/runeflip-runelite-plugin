package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Explicit GE Field Assist (v0.8.11) — the pure decision logic. Official
 * rule: RuneFlip can prepare GE fields after explicit user action, but must
 * never submit or execute the offer. These tests pin the "explicit" gate
 * (USER_CLICK only), the "right item" gate (open item only — #2/#3 can never
 * assist), the buy/sell leg selection and the compact labels.
 */
public class GeFieldAssistTest
{
	// ── the explicit-click + valid-editor gate ───────────────────────────────

	@Test
	public void onlyAUserClickInAValidEditorMayPrepare()
	{
		assertTrue(GeFieldAssist.canPrepare(
			GeFieldAssist.ActionSource.USER_CLICK, true));
		// A user click outside a valid GE editor: rejected.
		assertFalse(GeFieldAssist.canPrepare(
			GeFieldAssist.ActionSource.USER_CLICK, false));
		// Automatic/background execution: rejected even in a valid editor.
		assertFalse(GeFieldAssist.canPrepare(
			GeFieldAssist.ActionSource.AUTOMATIC, true));
		assertFalse(GeFieldAssist.canPrepare(
			GeFieldAssist.ActionSource.AUTOMATIC, false));
		assertFalse(GeFieldAssist.canPrepare(null, true));
	}

	@Test
	public void theUsersOwnHotkeyMayPrepareInAValidEditorOnly()
	{
		// v0.8.13: the user's own key press counts as explicit user action…
		assertTrue(GeFieldAssist.canPrepare(
			GeFieldAssist.ActionSource.USER_HOTKEY, true));
		// …but never outside the matching GE editor.
		assertFalse(GeFieldAssist.canPrepare(
			GeFieldAssist.ActionSource.USER_HOTKEY, false));
	}

	// ── prompt classification (no assist for an unidentified editor) ────────

	@Test
	public void promptsClassifyIntoSearchQuantityPriceOrNothing()
	{
		// The real GE prompts (v0.8.13).
		assertEquals(GeFieldAssist.Field.ITEM_SEARCH,
			GeFieldAssist.fieldForPrompt("What would you like to buy?"));
		assertEquals(GeFieldAssist.Field.ITEM_SEARCH,
			GeFieldAssist.fieldForPrompt("What would you like to sell?"));
		assertEquals(GeFieldAssist.Field.QUANTITY,
			GeFieldAssist.fieldForPrompt("How many do you wish to buy?"));
		assertEquals(GeFieldAssist.Field.QUANTITY,
			GeFieldAssist.fieldForPrompt("How many do you wish to sell?"));
		assertEquals(GeFieldAssist.Field.PRICE,
			GeFieldAssist.fieldForPrompt("Set a price for each item:"));
		// Color tags or prefixes around the prompt do not matter.
		assertEquals(GeFieldAssist.Field.PRICE,
			GeFieldAssist.fieldForPrompt("<col=000000>Set a price for each item:</col>"));
		// Unknown or missing prompt → no assist at all.
		assertEquals(GeFieldAssist.Field.NONE,
			GeFieldAssist.fieldForPrompt("Enter your name:"));
		assertEquals(GeFieldAssist.Field.NONE, GeFieldAssist.fieldForPrompt(null));
	}

	// ── GE search detection (v0.8.14 hotfix) ─────────────────────────────────

	@Test
	public void theSearchHelperPromptCountsAsItemSearch()
	{
		// The line the real GE search shows before the user types anything.
		assertEquals(GeFieldAssist.Field.ITEM_SEARCH,
			GeFieldAssist.fieldForPrompt(
				"Start typing the name of an item to search for it."));
	}

	@Test
	public void realBuyAndSellSearchPromptsOpenTheSearchEditor()
	{
		// The live client does not always report the SEARCH meslayer mode —
		// the exact prompt alone must identify the editor.
		assertTrue(GeFieldAssist.searchEditorOpen(
			true, false, false, "What would you like to buy?"));
		assertTrue(GeFieldAssist.searchEditorOpen(
			true, false, false, "What would you like to sell?"));
	}

	@Test
	public void searchIsDetectedWhenOnlyTheSecondaryPromptIsVisible()
	{
		// The results layer carries "Start typing…" — its visibility alone
		// identifies the search even when no title prompt is readable…
		assertTrue(GeFieldAssist.searchEditorOpen(true, false, true, null));
		// …as does the helper text itself when it is the only prompt found.
		assertTrue(GeFieldAssist.searchEditorOpen(true, false, false,
			"Start typing the name of an item to search for it."));
		// And the original meslayer-mode signal still counts.
		assertTrue(GeFieldAssist.searchEditorOpen(true, true, false, null));
	}

	@Test
	public void unknownPromptOrClosedOfferSetupMeansNoSearchEditor()
	{
		assertFalse(GeFieldAssist.searchEditorOpen(
			true, false, false, "Enter your name:"));
		assertFalse(GeFieldAssist.searchEditorOpen(true, false, false, null));
		// Outside the GE offer setup no signal counts (e.g. a bank search).
		assertFalse(GeFieldAssist.searchEditorOpen(
			false, true, true, "What would you like to buy?"));
	}

	@Test
	public void classifyEditorTrustsTheValueEditorsRealPromptFirst()
	{
		assertEquals(GeFieldAssist.Field.QUANTITY, GeFieldAssist.classifyEditor(
			true, false, "How many do you wish to buy?"));
		assertEquals(GeFieldAssist.Field.PRICE, GeFieldAssist.classifyEditor(
			true, false, "Set a price for each item:"));
		// The v0.8.14 regression case: the GE search's own input is a
		// full-input widget under a search prompt — that IS the search
		// editor (previously misread as "value editor" and dropped to NONE).
		assertEquals(GeFieldAssist.Field.ITEM_SEARCH,
			GeFieldAssist.classifyEditor(true, true, "What would you like to buy?"));
		// A value editor whose prompt we cannot classify stays unassisted.
		assertEquals(GeFieldAssist.Field.NONE, GeFieldAssist.classifyEditor(
			true, false, "Enter your name:"));
		assertEquals(GeFieldAssist.Field.NONE,
			GeFieldAssist.classifyEditor(false, false, null));
	}

	// ── clickable search row: the select gate (v0.8.17) ──────────────────────

	@Test
	public void searchSelectRequiresAUserClickOnly()
	{
		// The full gate: user's own click + open search + valid #1 match.
		assertTrue(GeFieldAssist.canSelectSearchItem(
			GeFieldAssist.ActionSource.USER_CLICK, true, 560, 560));
		// Even the hotkey is rejected — it keeps prepare-text semantics;
		// the row click is the only select path.
		assertFalse(GeFieldAssist.canSelectSearchItem(
			GeFieldAssist.ActionSource.USER_HOTKEY, true, 560, 560));
		// Automatic/background execution: always rejected.
		assertFalse(GeFieldAssist.canSelectSearchItem(
			GeFieldAssist.ActionSource.AUTOMATIC, true, 560, 560));
		assertFalse(GeFieldAssist.canSelectSearchItem(null, true, 560, 560));
	}

	@Test
	public void searchSelectRequiresTheOpenSearchEditor()
	{
		// Search closed (or unknown prompt → not detected as open): no-op.
		assertFalse(GeFieldAssist.canSelectSearchItem(
			GeFieldAssist.ActionSource.USER_CLICK, false, 560, 560));
	}

	@Test
	public void searchSelectAcceptsOnlyTheNumberOnePrimary()
	{
		// Any item other than the #1 primary — #2/#3 of the Top 3 included —
		// can never activate the search assist.
		assertFalse(GeFieldAssist.canSelectSearchItem(
			GeFieldAssist.ActionSource.USER_CLICK, true, 4151, 560));
		// Invalid item ids never select.
		assertFalse(GeFieldAssist.canSelectSearchItem(
			GeFieldAssist.ActionSource.USER_CLICK, true, 0, 0));
		assertFalse(GeFieldAssist.canSelectSearchItem(
			GeFieldAssist.ActionSource.USER_CLICK, true, -1, -1));
	}

	// ── visible hint model (v0.8.14) ─────────────────────────────────────────

	@Test
	public void primaryPlusSearchEditorYieldsTheVisibleHint()
	{
		assertEquals("RuneFlip item: Death rune", GeFieldAssist.hintFor(
			GeFieldAssist.Field.ITEM_SEARCH, "Death rune",
			null, null, "Right Brace"));
	}

	@Test
	public void valueEditorsHintTheirOwnValues()
	{
		assertEquals(GeFieldAssist.qtyHint(250, "Right Brace"),
			GeFieldAssist.hintFor(GeFieldAssist.Field.QUANTITY,
				"Death rune", 250L, null, "Right Brace"));
		assertEquals(GeFieldAssist.priceHint(1_049, "Right Brace"),
			GeFieldAssist.hintFor(GeFieldAssist.Field.PRICE,
				"Death rune", null, 1_049L, "Right Brace"));
	}

	@Test
	public void closingOrUnknownEditorRemovesTheHint()
	{
		// NONE (editor closed / unknown prompt) renders nothing — the null
		// makes the plugin REMOVE the chatbox hint.
		assertNull(GeFieldAssist.hintFor(GeFieldAssist.Field.NONE,
			"Death rune", 250L, 100L, "Right Brace"));
		// No #1 primary → no search hint (only the primary may ever feed
		// the search assist; #2/#3 are never candidates).
		assertNull(GeFieldAssist.hintFor(GeFieldAssist.Field.ITEM_SEARCH,
			null, null, null, "Right Brace"));
		assertNull(GeFieldAssist.hintFor(GeFieldAssist.Field.ITEM_SEARCH,
			"  ", null, null, "Right Brace"));
		// Value editors without a value render nothing.
		assertNull(GeFieldAssist.hintFor(GeFieldAssist.Field.QUANTITY,
			"Death rune", null, null, "Right Brace"));
		assertNull(GeFieldAssist.hintFor(GeFieldAssist.Field.PRICE,
			"Death rune", null, null, "Right Brace"));
	}

	// ── chatbox hint copy (v0.8.13, Copilot-style) ───────────────────────────

	@Test
	public void chatboxHintsNameTheItemAndTheHotkey()
	{
		assertEquals("RuneFlip item: Death rune",
			GeFieldAssist.searchHint("Death rune"));

		String price = GeFieldAssist.priceHint(106_026, "Right Brace");
		assertTrue(price.startsWith("Press [Right Brace] to set RuneFlip price: 106"));
		assertTrue(price.contains("026"));
		assertTrue(price.endsWith(" gp"));

		String qty = GeFieldAssist.qtyHint(1_883, "Right Brace");
		assertTrue(qty.startsWith("Press [Right Brace] to set RuneFlip quantity: 1"));
		assertTrue(qty.contains("883"));
	}

	// ── fixtures ─────────────────────────────────────────────────────────────

	private static RuneFlipData.FastFlipItem primary()
	{
		RuneFlipData.FastFlipItem flip = new RuneFlipData.FastFlipItem();
		flip.itemId = 560; // death rune — the #1 primary suggestion
		flip.itemName = "Death rune";
		flip.suggestedBuyPrice = 100L;
		flip.suggestedSellPrice = 108L;
		flip.suggestedQuantity = 250L;
		return flip;
	}

	private static RuneFlipData.FastFlipItemContextResponse context(int itemId)
	{
		RuneFlipData.FastFlipItemContextResponse ctx =
			new RuneFlipData.FastFlipItemContextResponse();
		ctx.itemId = itemId;
		ctx.suggestedQuantity = 40L;
		ctx.targetComparison = new RuneFlipData.TargetComparison();
		ctx.targetComparison.targetBuy = 995L;
		ctx.targetComparison.targetSell = 1049L;
		return ctx;
	}

	// ── quantity: open item only, never #2/#3 ────────────────────────────────

	@Test
	public void quantityComesFromTheOpenItemsContextFirst()
	{
		assertEquals(Long.valueOf(40L),
			GeFieldAssist.qtyFor(2, context(2), primary()));
	}

	@Test
	public void quantityFallsBackToThePrimaryWhenItIsTheOpenItem()
	{
		assertEquals(Long.valueOf(250L),
			GeFieldAssist.qtyFor(560, null, primary()));

		// action.targetQuantity backs up a missing suggestedQuantity.
		RuneFlipData.FastFlipItem noQty = primary();
		noQty.suggestedQuantity = null;
		noQty.action = new RuneFlipData.RecommendedAction();
		noQty.action.targetQuantity = 300L;
		assertEquals(Long.valueOf(300L), GeFieldAssist.qtyFor(560, null, noQty));
	}

	@Test
	public void otherItemsIncludingRanksTwoAndThreeGetNoQuantity()
	{
		// The open item is neither the fetched context nor the #1 → nothing.
		// (#2/#3 are never stored as candidates, so they can never match.)
		assertNull(GeFieldAssist.qtyFor(4151, context(2), primary()));
		// No open item at all → nothing.
		assertNull(GeFieldAssist.qtyFor(-1, context(2), primary()));
		assertNull(GeFieldAssist.qtyFor(0, null, primary()));
		// Zero/negative backend values are never offered.
		RuneFlipData.FastFlipItemContextResponse zero = context(2);
		zero.suggestedQuantity = 0L;
		assertNull(GeFieldAssist.qtyFor(2, zero, null));
	}

	// ── price: correct leg per offer side, open item only ────────────────────

	@Test
	public void priceUsesTheBuyLegForBuyOffersAndSellLegForSellOffers()
	{
		RuneFlipData.FastFlipItemContextResponse ctx = context(2);
		assertEquals(Long.valueOf(995L),
			GeFieldAssist.priceFor(2, false, ctx, null));
		assertEquals(Long.valueOf(1049L),
			GeFieldAssist.priceFor(2, true, ctx, null));
	}

	@Test
	public void priceFallsBackFromTargetsToPriceEdgeToSuggestedLegs()
	{
		// Context without a target pair degrades to its price edge.
		RuneFlipData.FastFlipItemContextResponse ctx = context(2);
		ctx.targetComparison = null;
		ctx.priceEdge = new RuneFlipData.PriceEdge();
		ctx.priceEdge.recommendedBuyPrice = 990L;
		ctx.priceEdge.recommendedSellPrice = 1050L;
		assertEquals(Long.valueOf(990L),
			GeFieldAssist.priceFor(2, false, ctx, null));
		assertEquals(Long.valueOf(1050L),
			GeFieldAssist.priceFor(2, true, ctx, null));

		// Primary #1 without a price edge degrades to its suggested legs.
		assertEquals(Long.valueOf(100L),
			GeFieldAssist.priceFor(560, false, null, primary()));
		assertEquals(Long.valueOf(108L),
			GeFieldAssist.priceFor(560, true, null, primary()));

		// Primary #1 WITH a price edge prefers the recommended legs.
		RuneFlipData.FastFlipItem withEdge = primary();
		withEdge.priceEdge = new RuneFlipData.PriceEdge();
		withEdge.priceEdge.recommendedBuyPrice = 99L;
		withEdge.priceEdge.recommendedSellPrice = 109L;
		assertEquals(Long.valueOf(99L),
			GeFieldAssist.priceFor(560, false, null, withEdge));
		assertEquals(Long.valueOf(109L),
			GeFieldAssist.priceFor(560, true, null, withEdge));
	}

	@Test
	public void otherItemsIncludingRanksTwoAndThreeGetNoPrice()
	{
		assertNull(GeFieldAssist.priceFor(4151, false, context(2), primary()));
		assertNull(GeFieldAssist.priceFor(-1, true, context(2), primary()));
		assertNull(GeFieldAssist.priceFor(560, false, null, null));
	}

	// ── labels and the prepared notice ───────────────────────────────────────

	@Test
	public void menuLabelsAreCompactAndPrefixed()
	{
		assertEquals("RuneFlip: select Death rune",
			GeFieldAssist.selectLabel("Death rune"));
		// Grouping separators are locale-dependent; pin prefix and digits.
		String qty = GeFieldAssist.qtyLabel(1250);
		assertTrue(qty.startsWith("RuneFlip: set qty 1"));
		assertTrue(qty.contains("250"));
		String price = GeFieldAssist.priceLabel(1049);
		assertTrue(price.startsWith("RuneFlip: set price 1"));
		assertTrue(price.contains("049"));
		assertTrue(price.endsWith(" gp"));
	}

	@Test
	public void preparedNoticeSaysReviewManually()
	{
		assertEquals("RuneFlip prepared the value. Review manually.",
			GeFieldAssist.PREPARED_MESSAGE);
	}
}
