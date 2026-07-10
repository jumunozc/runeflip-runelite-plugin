package com.runeflip.companion;

import java.util.function.Supplier;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.HotkeyListener;

/**
 * COMPLIANCE — GE field assist hotkey (v0.8.13). This class LISTENS to the
 * user's OWN physical key press through RuneLite's {@link KeyManager} /
 * {@link HotkeyListener} — the standard, client-supported way every hotkey
 * plugin uses. Listening is the exact opposite of the forbidden synthetic
 * input: RuneFlip never synthesizes input — no key or mouse event is ever
 * created, dispatched or replayed, here or anywhere (the ComplianceScanTest
 * still forbids Robot/dispatchEvent/mouse-synthesis everywhere, and allows
 * the key-LISTENING types in this file only).
 *
 * <p>The callback is a plain {@link Runnable} handed in by the plugin; it
 * routes to {@link GeFieldAssistService} with
 * {@code ActionSource.USER_HOTKEY}, where the write is still gated: valid
 * GE editor verified at press time, prepare-only semantics, never
 * submit/confirm/cancel/collect. This file deliberately contains no game
 * write of any kind.
 */
class GeFieldAssistHotkey
{
	private final KeyManager keyManager;
	private final HotkeyListener listener;

	GeFieldAssistHotkey(
		KeyManager keyManager,
		Supplier<Keybind> keybind,
		Runnable onUserHotkey)
	{
		this.keyManager = keyManager;
		this.listener = new HotkeyListener(keybind)
		{
			@Override
			public void hotkeyPressed()
			{
				onUserHotkey.run();
			}
		};
	}

	void register()
	{
		keyManager.registerKeyListener(listener);
	}

	void unregister()
	{
		keyManager.unregisterKeyListener(listener);
	}
}
