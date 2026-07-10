package com.runeflip.companion;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Contract test against a REAL (sanitized) production response of
 * GET /api/fast-flip/overview?limit=3 (hotfix v0.8.6). Captured from
 * runeflip-api.onrender.com with a fresh anonymous client id (no saved
 * preferences, so the backend applied its defaults) — public market data only,
 * no client data, no tokens.
 *
 * <p>Pins two facts the "Fast flip · 0" reports called into question:
 * <ol>
 *   <li>the plugin's Gson DTOs parse the production shape verbatim — the array
 *       names really are {@code fastBuy} / {@code fastSell} / {@code topFlips}
 *       (no {@code fastBuyItems} variants), with the figures where the web
 *       reads them; and</li>
 *   <li>a request without strategy params gets the SAME defaults the web uses:
 *       480 minutes / risk up to HIGH ({@code isDefault: true}).</li>
 * </ol>
 */
public class ProdOverviewContractTest
{
	private static final String FIXTURE = "/fast-flip-overview-prod.json";

	private final Gson gson = new Gson();

	private RuneFlipData.FastFlipOverviewResponse load() throws IOException
	{
		try (InputStream in = getClass().getResourceAsStream(FIXTURE))
		{
			assertNotNull("fixture " + FIXTURE + " must be on the test classpath", in);
			return gson.fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8),
				RuneFlipData.FastFlipOverviewResponse.class);
		}
	}

	@Test
	public void productionShapeParsesIntoThePluginDtos() throws IOException
	{
		RuneFlipData.FastFlipOverviewResponse overview = load();

		// The real array names — the exact fields the plugin maps.
		assertEquals(3, overview.topFlips.size());
		assertEquals(3, overview.fastBuy.size());
		assertEquals(3, overview.fastSell.size());
		assertNotNull(overview.coverage);
		assertNotNull(overview.disclaimer);
	}

	@Test
	public void noParamsGetsTheSameDefaultsAsTheWeb() throws IOException
	{
		RuneFlipData.FastFlipStrategy strategy = load().strategy;

		// The request behind this fixture sent NO strategy params (like the
		// plugin without saved preferences): the backend applied 8h / HIGH —
		// exactly what a fresh web session shows.
		assertNotNull(strategy);
		assertEquals(Integer.valueOf(480), strategy.timeframeMinutes);
		assertEquals("HIGH", strategy.riskLevel);
		assertEquals(Boolean.TRUE, strategy.isDefault);
		assertEquals("8h · HIGH risk", RuneFlipPanel.compactStrategyLine(strategy));
	}

	@Test
	public void productionFiguresLandWhereThePanelReadsThem() throws IOException
	{
		RuneFlipData.FastFlipOverviewResponse overview = load();

		// The web's Fast Buy list starts with Zulrah's scales — same data here.
		assertEquals("Zulrah's scales", overview.fastBuy.get(0).itemName);

		for (RuneFlipData.FastFlipItem flip : overview.topFlips)
		{
			assertTrue(flip.itemId > 0);
			assertNotNull(flip.itemName);
			assertNotNull(flip.suggestedBuyPrice);
			assertNotNull(flip.suggestedSellPrice);
			assertNotNull(flip.estimatedProfit);
			assertNotNull(flip.roi);
			assertNotNull(flip.riskLevel);
			assertNotNull(flip.priceEdge);
			// Recommendation Actions (v0.8.2) present and review-only.
			assertNotNull(flip.action);
			assertEquals(Boolean.TRUE, flip.action.reviewOnly);
		}
	}

	@Test
	public void productionResponseRendersTopThreeNeverTheEmptyState() throws IOException
	{
		FastFlipSelection selection =
			FastFlipSelection.select(load(), RuneFlipPanel.MAX_FAST_FLIP_ROWS);

		assertEquals(FastFlipSelection.Source.TOP, selection.source);
		assertEquals(3, selection.rows.size());
		assertEquals("Top 3 Fast Flips",
			RuneFlipPanel.headerTitleOf(selection.source, selection.rows.size()));
	}

	@Test
	public void emptyTopWithProductionFastBuyRendersGeneralIdeasNotEmptyState()
		throws IOException
	{
		// The reported scenario: web shows Fast Buy items (Zulrah's scales,
		// Ancient essence, Steel cannonball…) while the Top list is empty. The
		// panel must render those as General ideas — never the empty state.
		RuneFlipData.FastFlipOverviewResponse overview = load();
		overview.topFlips = new ArrayList<>();

		FastFlipSelection selection =
			FastFlipSelection.select(overview, RuneFlipPanel.MAX_FAST_FLIP_ROWS);

		assertEquals(FastFlipSelection.Source.GENERAL, selection.source);
		assertEquals(3, selection.rows.size());
		assertEquals("Zulrah's scales", selection.rows.get(0).itemName);
		assertEquals("General ideas",
			RuneFlipPanel.headerTitleOf(selection.source, selection.rows.size()));
	}

	@Test
	public void allSectionsEmptyIsTheOnlyEmptyState() throws IOException
	{
		RuneFlipData.FastFlipOverviewResponse overview = load();
		overview.topFlips = new ArrayList<>();
		overview.fastBuy = new ArrayList<>();
		overview.fastSell = new ArrayList<>();

		FastFlipSelection selection =
			FastFlipSelection.select(overview, RuneFlipPanel.MAX_FAST_FLIP_ROWS);

		assertEquals(FastFlipSelection.Source.NONE, selection.source);
		assertTrue(selection.rows.isEmpty());
	}

	@Test
	public void generalFallbackDeduplicatesTheOverlappingSections() throws IOException
	{
		// Production fastBuy and fastSell list the SAME liquid items — the
		// fallback must not show duplicates.
		RuneFlipData.FastFlipOverviewResponse overview = load();
		overview.topFlips = new ArrayList<>();

		FastFlipSelection selection =
			FastFlipSelection.select(overview, RuneFlipPanel.MAX_FAST_FLIP_ROWS);

		List<Integer> ids = new ArrayList<>();
		for (RuneFlipData.FastFlipItem row : selection.rows)
		{
			assertTrue("duplicate item " + row.itemId, !ids.contains(row.itemId));
			ids.add(row.itemId);
		}
	}
}
