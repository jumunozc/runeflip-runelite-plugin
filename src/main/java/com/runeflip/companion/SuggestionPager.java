package com.runeflip.companion;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure pagination + selection math for the selectable Fast Flip suggestions
 * (v0.8.18) — no RuneLite types, fully unit-tested. The panel shows the
 * extended suggestion list in pages of {@link #PAGE_SIZE}; the user may click
 * any VISIBLE row to make it the selected GE suggestion. This class answers
 * WHICH rows a page shows and WHICH row is currently the selection:
 * <ul>
 *   <li>a pinned selection (the user's explicit click) wins while its item is
 *       still part of the refreshed list — page flips never steal it;</li>
 *   <li>without a pin the default is the FIRST row of the current page — a
 *       safe, visible default, never a hidden item;</li>
 *   <li>a page left empty by a refresh clamps back to the last valid page.</li>
 * </ul>
 *
 * <p>Display bookkeeping only: nothing here touches the game. Preparing the
 * selected suggestion into the GE search stays behind the click-time gate in
 * {@link GeFieldAssist#canSelectSearchItem} / {@link GeSearchAssistService}.
 */
final class SuggestionPager
{
	/** Rows per page — the classic Top 3 view is exactly one page. */
	static final int PAGE_SIZE = 3;

	private SuggestionPager()
	{
	}

	/** Number of pages the list needs; an empty list still has one (empty)
	 *  page so the card renders its empty state instead of dividing by zero. */
	static int pageCount(int totalRows, int pageSize)
	{
		if (totalRows <= 0 || pageSize <= 0)
		{
			return 1;
		}
		return (totalRows + pageSize - 1) / pageSize;
	}

	/** The nearest valid page for the (possibly stale) requested one — a
	 *  refresh that shrank the list lands on the last page, never a blank. */
	static int clampPage(int page, int totalRows, int pageSize)
	{
		int last = pageCount(totalRows, pageSize) - 1;
		return Math.max(0, Math.min(page, last));
	}

	/** The rows the given page shows (null-safe copy; empty when the list —
	 *  or the requested slice — has nothing). */
	static List<RuneFlipData.FastFlipItem> pageRows(
		List<RuneFlipData.FastFlipItem> rows, int page, int pageSize)
	{
		List<RuneFlipData.FastFlipItem> out = new ArrayList<>();
		if (rows == null || pageSize <= 0)
		{
			return out;
		}
		int clamped = clampPage(page, rows.size(), pageSize);
		int from = clamped * pageSize;
		for (int i = from; i < rows.size() && i < from + pageSize; i++)
		{
			out.add(rows.get(i));
		}
		return out;
	}

	static boolean hasPrevious(int page)
	{
		return page > 0;
	}

	static boolean hasNext(int page, int totalRows, int pageSize)
	{
		return page < pageCount(totalRows, pageSize) - 1;
	}

	/** Compact page indicator: "Suggestions 1–3", "Suggestions 4–6", … —
	 *  honest about a short last page ("Suggestions 4–5", "Suggestion 7"). */
	static String pageLabel(int page, int totalRows, int pageSize)
	{
		int clamped = clampPage(page, totalRows, pageSize);
		int first = clamped * pageSize + 1;
		int last = Math.min(totalRows, (clamped + 1) * pageSize);
		if (first >= last)
		{
			return "Suggestion " + first;
		}
		return "Suggestions " + first + "–" + last;
	}

	/**
	 * The effective selected GE suggestion: the pinned item while it is still
	 * in the list (the user's explicit choice survives refreshes AND page
	 * flips), else the first row of the current page, else null when the list
	 * is empty. Never an item outside the loaded list — an unloaded item can
	 * never become the suggestion the assist prepares.
	 */
	static RuneFlipData.FastFlipItem effectiveSelection(
		List<RuneFlipData.FastFlipItem> rows,
		Integer pinnedItemId,
		int page,
		int pageSize)
	{
		RuneFlipData.FastFlipItem pinned =
			pinnedItemId == null ? null : byItemId(rows, pinnedItemId);
		if (pinned != null)
		{
			return pinned;
		}
		List<RuneFlipData.FastFlipItem> visible = pageRows(rows, page, pageSize);
		return visible.isEmpty() ? null : visible.get(0);
	}

	/**
	 * Refresh rule for the plugin-held selection: keep the current suggestion
	 * (refreshed to the new row object) while its item is still listed;
	 * otherwise fall back to the first row, or null when the list is empty —
	 * a stale suggestion is never kept once the backend stopped listing it.
	 */
	static RuneFlipData.FastFlipItem retainSelection(
		List<RuneFlipData.FastFlipItem> rows,
		RuneFlipData.FastFlipItem current)
	{
		if (current != null)
		{
			RuneFlipData.FastFlipItem fresh = byItemId(rows, current.itemId);
			if (fresh != null)
			{
				return fresh;
			}
		}
		return rows == null || rows.isEmpty() ? null : rows.get(0);
	}

	/** The row with the given itemId, or null (null-safe). */
	static RuneFlipData.FastFlipItem byItemId(
		List<RuneFlipData.FastFlipItem> rows, int itemId)
	{
		if (rows == null)
		{
			return null;
		}
		for (RuneFlipData.FastFlipItem row : rows)
		{
			if (row != null && row.itemId == itemId)
			{
				return row;
			}
		}
		return null;
	}
}
