package com.runeflip.companion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, unit-tested choice of which Fast Flip rows the contextual panel renders
 * (hotfix v0.8.5-c). Before this, the panel read ONLY {@code topFlips}: an
 * overview whose Top ranking was emptied by a restrictive saved strategy — but
 * that still carried fast-buy / fast-sell candidates — rendered "Fast flip · 0"
 * even though the backend had ideas to show.
 *
 * <p>The choice now degrades honestly, in order:
 * <ol>
 *   <li>{@code topFlips} has items → show them ({@link Source#TOP}).</li>
 *   <li>else fast-buy then fast-sell have items → show those as
 *       {@link Source#GENERAL} "General ideas", de-duplicated by itemId with the
 *       original order preserved.</li>
 *   <li>else → {@link Source#NONE}: the panel shows the current strategy plus
 *       relax hints instead of an empty box.</li>
 * </ol>
 *
 * <p>Display only: this decides WHAT to render from figures the backend already
 * computed — it never derives a price/profit/risk itself and never acts on the
 * game.
 */
public final class FastFlipSelection
{
	/** Where the shown rows came from (drives the panel's heading/banner). */
	public enum Source
	{
		TOP,
		GENERAL,
		NONE
	}

	public final Source source;
	public final List<RuneFlipData.FastFlipItem> rows;

	private FastFlipSelection(Source source, List<RuneFlipData.FastFlipItem> rows)
	{
		this.source = source;
		this.rows = rows;
	}

	/**
	 * Picks up to {@code max} rows from the overview. Top ranking wins; when it
	 * is empty the fast-buy then fast-sell candidates fill in (de-duplicated by
	 * itemId, order preserved). A null response, a non-positive max, or an
	 * all-empty overview yields {@link Source#NONE} with no rows.
	 */
	public static FastFlipSelection select(
		RuneFlipData.FastFlipOverviewResponse response, int max)
	{
		if (response == null || max <= 0)
		{
			return new FastFlipSelection(Source.NONE, new ArrayList<>());
		}
		List<RuneFlipData.FastFlipItem> top = take(response.topFlips, max);
		if (!top.isEmpty())
		{
			return new FastFlipSelection(Source.TOP, top);
		}
		List<RuneFlipData.FastFlipItem> general =
			mergeDistinct(response.fastBuy, response.fastSell, max);
		if (!general.isEmpty())
		{
			return new FastFlipSelection(Source.GENERAL, general);
		}
		return new FastFlipSelection(Source.NONE, new ArrayList<>());
	}

	/** First {@code max} non-null items of a list (null-safe copy). */
	private static List<RuneFlipData.FastFlipItem> take(
		List<RuneFlipData.FastFlipItem> items, int max)
	{
		List<RuneFlipData.FastFlipItem> out = new ArrayList<>();
		if (items == null)
		{
			return out;
		}
		for (RuneFlipData.FastFlipItem item : items)
		{
			if (item == null)
			{
				continue;
			}
			out.add(item);
			if (out.size() >= max)
			{
				break;
			}
		}
		return out;
	}

	/**
	 * Concatenates two lists, dropping nulls and any itemId already seen, up to
	 * {@code max}. The first list is preferred (fast-buy before fast-sell), and
	 * insertion order is preserved so the panel shows a stable ranking.
	 */
	private static List<RuneFlipData.FastFlipItem> mergeDistinct(
		List<RuneFlipData.FastFlipItem> first,
		List<RuneFlipData.FastFlipItem> second,
		int max)
	{
		Map<Integer, RuneFlipData.FastFlipItem> byId = new LinkedHashMap<>();
		for (List<RuneFlipData.FastFlipItem> list : Arrays.asList(first, second))
		{
			if (list == null)
			{
				continue;
			}
			for (RuneFlipData.FastFlipItem item : list)
			{
				if (item == null || byId.containsKey(item.itemId))
				{
					continue;
				}
				byId.put(item.itemId, item);
				if (byId.size() >= max)
				{
					return new ArrayList<>(byId.values());
				}
			}
		}
		return new ArrayList<>(byId.values());
	}
}
