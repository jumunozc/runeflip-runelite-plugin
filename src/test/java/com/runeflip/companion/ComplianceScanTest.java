package com.runeflip.companion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Anti-bot compliance guard (v0.8.4). RuneFlip may assist input, but never
 * execute intent. This test SCANS the plugin's own source (comments stripped so
 * prose that merely explains why an API is forbidden does not trip it) and
 * fails if any code path could act on the game client:
 *
 *   (d) no OCR / screenshot / screen scraping;
 *   (e) no confirm / buy / sell / cancel / collect — i.e. none of the
 *       synthetic-input, menu-invocation or widget/var mutation APIs those
 *       would require;
 *   (f) no in-game GE field setup — the research finding is "Absent — no safe
 *       API", so the code must contain no synthetic-input mechanism to fill it.
 *
 * The context-aware GE panel (v0.8.4) is allowed exactly ONE game touch-point:
 * READING the current-GE-item VarPlayer via getVarpValue — asserted present so
 * the feature stays wired to the safe, read-only signal and nothing else.
 */
public class ComplianceScanTest
{
	/**
	 * Forbidden mechanisms. Each would let the plugin drive the client — none
	 * may appear in real code. Kept as method/class-name fragments so the scan
	 * catches invocations regardless of the receiver.
	 */
	private static final String[] FORBIDDEN = {
		// Synthetic input / scripting into the client.
		"setVarcStrValue", "setVarcIntValue", "runScript",
		"invokeMenuAction", "menuAction(", "setVarbit", "setVarpValue",
		"setVarp(", "setVarbitValue",
		// Keyboard / mouse automation.
		"java.awt.Robot", "Robot(", "KeyEvent", "MouseEvent",
		"keyPress", "keyRelease", "mousePress", "mouseMove", "dispatchEvent",
		// OCR / screenshot / screen scraping.
		"createScreenCapture", "getBufferedImageProvider", "drawManager",
		"DrawManager", "ImageCapture", "Tesseract", "screenshot", "getCanvas",
	};

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
			for (String token : FORBIDDEN)
			{
				if (code.contains(token))
				{
					violations.add(source.getFileName() + " → " + token);
				}
			}
		}
		if (!violations.isEmpty())
		{
			fail("Forbidden game-acting API(s) found in plugin code (RuneFlip "
				+ "may assist input, never execute intent): " + violations);
		}
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
	public void inGameSetupStaysDisabledAsAbsentNoSafeApi() throws IOException
	{
		// (f) The in-game GE search prefill remains DISABLED — the documented
		// "Absent — no safe API" conclusion. The rationale comment must survive.
		String panel = read("RuneFlipPanel.java");
		assertTrue("the in-game search affordance must stay disabled",
			panel.contains("disabledSearchButton"));
		assertTrue("the no-safe-API rationale must remain documented",
			panel.contains("synthetic input"));
	}

	@Test
	public void primaryGeSearchAssistStaysBlockedAndDocumented() throws IOException
	{
		// v0.8.10: the primary GE suggestion is a display-only chip. The
		// investigation's conclusion (previous-search / search-prepare /
		// search-suggest all require setVarcStrValue + runScript / widget
		// mutation / synthetic input) must stay documented at the chip, so
		// nobody "finishes" the feature with a forbidden write path.
		String panel = read("RuneFlipPanel.java");
		assertTrue("the blocked conclusion must remain documented",
			panel.contains("Primary GE Search Assist blocked — no safe API found"));
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
