package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

/**
 * Assisted Offer Setup was retired in v0.8.10 (the Copy buttons it gated are
 * gone — the game accepts no paste), but the config key survives for saved-
 * config compatibility and must keep its OFF default. The flag is read by no
 * code (asserted in {@link PrimaryGeSuggestionTest}); this pins the default
 * so a leftover key can never look like an active feature.
 */
public class AssistedOfferSetupConfigTest
{
	private final RuneFlipCompanionConfig config =
		new RuneFlipCompanionConfig()
		{
		};

	@Test
	public void assistedOfferSetupIsOffByDefault()
	{
		assertFalse(config.enableAssistedOfferSetup());
	}
}
