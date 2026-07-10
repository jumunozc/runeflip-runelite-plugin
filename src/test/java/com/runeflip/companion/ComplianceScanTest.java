package com.runeflip.companion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Anti-bot compliance guard. Official rule since v0.8.11: <b>RuneFlip can
 * prepare GE fields after explicit user action, but must never submit or
 * execute the offer.</b> This test SCANS the plugin's own source (comments
 * stripped so prose that merely explains an API does not trip it) and fails
 * if any code path could act on the game client:
 *
 *   (d) no OCR / screenshot / screen scraping;
 *   (e) no confirm / buy / sell / cancel / abort / collect — i.e. none of
 *       the synthetic-input, menu-invocation or server-state mutation APIs
 *       those would require, anywhere;
 *   (f) the ONLY game writes allowed are the pending-input prepares in
 *       {@link GeFieldAssistService} (v0.8.11): {@code setVarcStrValue} +
 *       {@code runScript} are forbidden in every other file, and inside the
 *       service they may only redraw the input being prepared — never run
 *       any other client script.
 *
 * The context-aware GE panel (v0.8.4) keeps its one read touch-point:
 * READING the current-GE-item VarPlayer via getVarpValue — asserted present
 * so the feature stays wired to the safe, read-only signal.
 */
public class ComplianceScanTest
{
	/**
	 * Forbidden mechanisms — in EVERY file, the assist service included. Each
	 * would let the plugin drive the client (input automation, menu
	 * invocation, server-visible var writes, OCR/screen scraping). Kept as
	 * method/class-name fragments so the scan catches invocations regardless
	 * of the receiver.
	 */
	private static final String[] FORBIDDEN_EVERYWHERE = {
		// Synthetic input / menu invocation / server-state mutation.
		"setVarcIntValue", "invokeMenuAction", "menuAction(",
		"setVarbit", "setVarpValue", "setVarp(", "setVarbitValue",
		// Keyboard / mouse SYNTHESIS — creating, dispatching or replaying
		// input is forbidden everywhere, the hotkey holder included.
		"java.awt.Robot", "Robot(", "MouseEvent",
		"mousePress", "mouseMove", "dispatchEvent", "processKey",
		// OCR / screenshot / screen scraping.
		"createScreenCapture", "getBufferedImageProvider", "drawManager",
		"DrawManager", "ImageCapture", "Tesseract", "screenshot", "getCanvas",
	};

	/**
	 * Field-prepare writes (v0.8.11): the pending-input write is allowed
	 * ONLY inside the field-assist service; scripts (v0.8.17) only inside
	 * the two services, each pinned to its single allowed script below.
	 * Anywhere else fails.
	 */
	private static final String[] INPUT_WRITE_TOKENS = {
		"setVarcStrValue",
	};
	private static final String[] SCRIPT_TOKENS = {
		"runScript",
	};

	/**
	 * Key LISTENING types (v0.8.13): hearing the user's OWN key press via
	 * RuneLite's KeyManager/HotkeyListener is the opposite of synthesis, but
	 * it stays quarantined in ONE file so nothing else can grow input
	 * handling. ("keyPress" also matches HotkeyListener#hotkeyPressed.)
	 */
	private static final String[] KEY_LISTEN_TOKENS = {
		"KeyEvent", "keyPress", "keyRelease", "KeyListener",
	};

	private static final String ASSIST_SERVICE = "GeFieldAssistService.java";
	private static final String SEARCH_SERVICE = "GeSearchAssistService.java";
	private static final String HOTKEY_HOLDER = "GeFieldAssistHotkey.java";

	@Test
	public void pluginSourceContainsNoGameActingApis() throws IOException
	{
		List<Path> sources = mainSources();
		assertTrue("expected to find plugin sources to scan", sources.size() >= 5);

		List<String> violations = new ArrayList<>();
		for (Path source : sources)
		{
			String code = stripComments(
				new String(Files.readAllBytes(source), StandardCharsets.UTF_8));
			for (String token : FORBIDDEN_EVERYWHERE)
			{
				if (code.contains(token))
				{
					violations.add(source.getFileName() + " → " + token);
				}
			}
			String fileName = source.getFileName().toString();
			if (!fileName.equals(HOTKEY_HOLDER))
			{
				for (String token : KEY_LISTEN_TOKENS)
				{
					if (code.contains(token))
					{
						violations.add(fileName + " → " + token
							+ " (key listening allowed only in "
							+ HOTKEY_HOLDER + ")");
					}
				}
			}
			if (!fileName.equals(ASSIST_SERVICE))
			{
				for (String token : INPUT_WRITE_TOKENS)
				{
					if (code.contains(token))
					{
						violations.add(fileName + " → " + token
							+ " (allowed only in " + ASSIST_SERVICE + ")");
					}
				}
			}
			if (!fileName.equals(ASSIST_SERVICE)
				&& !fileName.equals(SEARCH_SERVICE))
			{
				for (String token : SCRIPT_TOKENS)
				{
					if (code.contains(token))
					{
						violations.add(fileName + " → " + token
							+ " (allowed only in " + ASSIST_SERVICE + " and "
							+ SEARCH_SERVICE + ")");
					}
				}
			}
		}
		if (!violations.isEmpty())
		{
			fail("Forbidden game-acting API(s) found in plugin code (RuneFlip "
				+ "prepares fields only after an explicit user click, and only "
				+ "inside GeFieldAssistService; it never executes intent): "
				+ violations);
		}
	}

	/**
	 * The assist service must BE the encapsulation the rule demands: the
	 * click-source gate present, the writes wired, the official rule
	 * documented — and the ONLY client script it ever runs is the redraw of
	 * the input line being prepared. Any other ScriptID would mean a script
	 * that does something (submit, search, confirm) instead of a redraw.
	 */
	@Test
	public void fieldWritesAreClickGatedInsideTheAssistService() throws IOException
	{
		String raw = read(ASSIST_SERVICE);
		String code = stripComments(raw);

		assertTrue("the service must actually hold the prepare writes",
			code.contains("setVarcStrValue") && code.contains("runScript"));
		assertTrue("every prepare must pass the USER_CLICK + editor gate",
			code.contains("GeFieldAssist.canPrepare(source,"));
		assertTrue("the official rule must be documented at the writer",
			raw.contains("must never submit or execute the offer"));

		Matcher scripts = Pattern.compile("ScriptID\\.[A-Z_]+").matcher(code);
		while (scripts.find())
		{
			assertEquals("the service may only redraw the prepared input",
				"ScriptID.CHAT_TEXT_INPUT_REBUILD", scripts.group());
		}
	}

	/**
	 * The search-select service (v0.8.17) must BE its own quarantine: the
	 * ONLY script it may run is the game's previous-search SELECT handler
	 * (the verified script id the native "Last search:" row dispatches),
	 * every select must pass the strict click-time gate (USER_CLICK + open
	 * search + #1 primary only), the official rule must be documented at
	 * the writer, and it must hold no pending-input write of its own.
	 */
	@Test
	public void searchSelectIsClickGatedInsideTheSearchService() throws IOException
	{
		String raw = read(SEARCH_SERVICE);
		String code = stripComments(raw);

		assertTrue("the search service must actually hold the select script",
			code.contains("runScript"));
		assertTrue("every select must pass the click-time gate",
			code.contains("GeFieldAssist.canSelectSearchItem("));
		assertTrue("the official rule must be documented at the writer",
			raw.contains("must never submit or execute the offer"));
		assertTrue("the pending-input write stays out of the search service",
			!code.contains("setVarcStrValue"));

		// The verified select script, pinned: no other script id — by
		// constant or literal — may ever be dispatched from here.
		assertTrue("the select script constant must be the verified id 754",
			code.contains("GE_SEARCH_SELECT_SCRIPT = 754"));
		Matcher calls = Pattern.compile(
			"runScript\\(\\s*([A-Za-z0-9_]+)").matcher(code);
		int callCount = 0;
		while (calls.find())
		{
			callCount++;
			assertEquals("the search service may only run the SELECT script",
				"GE_SEARCH_SELECT_SCRIPT", calls.group(1));
		}
		assertTrue("expected exactly one select call site", callCount == 1);
		assertTrue("no ScriptID constant may be dispatched from here",
			!code.contains("ScriptID."));
	}

	/** Every prepare call site must pass the user's own explicit source —
	 *  USER_CLICK or USER_HOTKEY (v0.8.13) — never AUTOMATIC. */
	@Test
	public void assistCallSitesUseExplicitUserSourcesOnly() throws IOException
	{
		String plugin = stripComments(read("RuneFlipCompanionPlugin.java"));
		assertTrue("the plugin must invoke the assist via USER_CLICK",
			plugin.contains("GeFieldAssist.ActionSource.USER_CLICK"));
		assertTrue("the hotkey path must invoke the assist via USER_HOTKEY",
			plugin.contains("GeFieldAssist.ActionSource.USER_HOTKEY"));
		assertTrue("nothing may ever pass the AUTOMATIC source",
			!plugin.contains("ActionSource.AUTOMATIC"));
	}

	/**
	 * The hotkey holder (v0.8.13) may only LISTEN: it must register through
	 * RuneLite's KeyManager, must contain no game write (the field-write
	 * scan above already fails it otherwise), and must never feed events
	 * back into the client (processKey* is globally forbidden).
	 */
	@Test
	public void hotkeyHolderOnlyListens() throws IOException
	{
		String raw = read(HOTKEY_HOLDER);
		String code = stripComments(raw);
		assertTrue("the hotkey must go through RuneLite's KeyManager",
			code.contains("KeyManager"));
		assertTrue("listening must be the documented opposite of synthesis",
			raw.contains("never synthesizes") || raw.contains("never a synthesized"));
		assertTrue("the holder must hold no game write",
			!code.contains("setVarcStrValue") && !code.contains("runScript")
				&& !code.contains("net.runelite.api.Client"));
	}

	@Test
	public void contextPanelReadsOnlyTheSafeGeItemVar() throws IOException
	{
		String plugin = read("RuneFlipCompanionPlugin.java");
		// The single allowed game touch-point for the context feature: a
		// read-only VarPlayer read. Present → the feature is wired to the safe
		// signal, not to any input/scrape path.
		assertTrue("context panel must read the GE item via getVarpValue",
			plugin.contains("getVarpValue"));

		// The var id lives in the pure helper, used directly (read-only).
		String helper = read("GeItemSelection.java");
		assertTrue(helper.contains("GE_CURRENT_ITEM_VARP"));
	}

	@Test
	public void legacySearchButtonStaysDisabled() throws IOException
	{
		// The legacy dashboard's "Search item" button stays DISABLED: the
		// supported in-game path is the click-gated "RuneFlip: select …" menu
		// option (v0.8.11), never a panel-side write. The rationale comment
		// must survive.
		String panel = read("RuneFlipPanel.java");
		assertTrue("the legacy search affordance must stay disabled",
			panel.contains("disabledSearchButton"));
		assertTrue("the synthetic-input rationale must remain documented",
			panel.contains("synthetic input"));
	}

	@Test
	public void explicitFieldAssistRuleIsDocumented() throws IOException
	{
		// The v0.8.11 official rule must stay written at both halves of the
		// feature: the menu-option display (plugin) and the writer (service).
		String rule = "prepare GE fields after explicit user action";
		assertTrue(read("RuneFlipCompanionPlugin.java").contains(rule));
		assertTrue(read(ASSIST_SERVICE).contains(rule));
	}

	// ── helpers ──────────────────────────────────────────────────────────────

	private static List<Path> mainSources() throws IOException
	{
		Path dir = Paths.get("src/main/java/com/runeflip/companion");
		assertTrue("plugin source dir not found at " + dir.toAbsolutePath(),
			Files.isDirectory(dir));
		try (Stream<Path> files = Files.walk(dir))
		{
			return files
				.filter(p -> p.toString().endsWith(".java"))
				.collect(Collectors.toList());
		}
	}

	private static String read(String fileName) throws IOException
	{
		Path path = Paths.get("src/main/java/com/runeflip/companion", fileName);
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	/**
	 * Removes // line comments, block comments and javadoc lines (those starting
	 * with *) so prose that NAMES a forbidden API to explain why it is not used
	 * does not trip the scan. String literals are left intact — the forbidden
	 * tokens are API identifiers, not text the plugin ever prints.
	 */
	static String stripComments(String source)
	{
		StringBuilder out = new StringBuilder(source.length());
		for (String line : source.split("\n", -1))
		{
			String trimmed = line.trim();
			if (trimmed.startsWith("*") || trimmed.startsWith("//")
				|| trimmed.startsWith("/*") || trimmed.startsWith("*/"))
			{
				continue;
			}
			// Strip a trailing line comment while keeping code before it.
			int slash = line.indexOf("//");
			if (slash >= 0)
			{
				line = line.substring(0, slash);
			}
			out.append(line).append('\n');
		}
		return out.toString();
	}

	@Test
	public void commentStripperKeepsCodeButDropsProse()
	{
		// A comment that names an API must be dropped…
		assertEquals("", ComplianceScanTest.stripComments(
			" * requires client.setVarcStrValue(...)\n").trim());
		// …but real code on a non-comment line survives.
		assertTrue(ComplianceScanTest.stripComments(
			"int v = client.getVarpValue(1151);\n").contains("getVarpValue"));
	}
}
