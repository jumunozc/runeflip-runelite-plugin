package com.runeflip.companion;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Dev launcher (same pattern as the official runelite/example-plugin):
 * runs a full RuneLite client with this plugin side-loaded. Not a unit test.
 *
 *   gradle test --tests "*Test"  runs the real unit tests;
 *   run this main() from an IDE to try the plugin against a live client.
 */
public class RuneFlipCompanionPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RuneFlipCompanionPlugin.class);
		RuneLite.main(args);
	}
}
