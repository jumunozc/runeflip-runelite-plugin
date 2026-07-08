package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

/**
 * Assisted Offer Setup (v0.8.3) must be strictly opt-in: the config default
 * is OFF, so a fresh install never shows the clipboard Copy buttons until the
 * user turns it on. Uses the interface default method directly (config
 * defaults are plain Java defaults, no RuneLite runtime needed).
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
