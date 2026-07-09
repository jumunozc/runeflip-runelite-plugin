package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Context-aware GE item detection (v0.8.4). The panel switches between the
 * Top-3 Fast Flip list and the selected-item context based purely on the
 * read-only "current GE item" VarPlayer — these tests pin that mapping without
 * a live client. No OCR, no input: just interpreting a var value.
 */
public class GeItemSelectionTest
{
	@Test
	public void noSelectionMeansTopThree()
	{
		// The var is -1 (or 0) when no offer is being set up → show Top 3.
		assertFalse(GeItemSelection.hasSelection(-1));
		assertFalse(GeItemSelection.hasSelection(0));
		assertEquals(-1, GeItemSelection.selectedItemId(-1));
		assertEquals(-1, GeItemSelection.selectedItemId(0));
	}

	@Test
	public void aSelectedItemMeansTheSelectedPanel()
	{
		// A positive var value is the itemId open in the Buy/Sell setup.
		assertTrue(GeItemSelection.hasSelection(4151));
		assertEquals(4151, GeItemSelection.selectedItemId(4151));
	}

	@Test
	public void changeIsNormalizedSoNoSelectionValuesAreEquivalent()
	{
		// -1 ↔ 0 both mean "no selection" — not a spurious change.
		assertFalse(GeItemSelection.changed(-1, 0));
		assertFalse(GeItemSelection.changed(4151, 4151));
		// Opening, swapping and closing an item ARE changes.
		assertTrue(GeItemSelection.changed(-1, 4151));
		assertTrue(GeItemSelection.changed(4151, 561));
		assertTrue(GeItemSelection.changed(4151, -1));
	}

	@Test
	public void usesTheReadOnlyGeCurrentItemVar()
	{
		// The documented VarPlayer id for the item in the GE offer setup.
		assertEquals(1151, GeItemSelection.GE_CURRENT_ITEM_VARP);
	}
}
