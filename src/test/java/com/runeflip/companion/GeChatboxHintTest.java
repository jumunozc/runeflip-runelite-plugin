package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Visible chatbox hint geometry (v0.8.14 hotfix) — the pure bounds math that
 * keeps the "RuneFlip item: …" line INSIDE the container's visible area, so
 * a created hint can never sit off-screen. Rendering itself (widget child on
 * a chatbox layer) stays display-only and is exercised in-game.
 */
public class GeChatboxHintTest
{
	@Test
	public void classicChatboxKeepsTheUnderPromptLine()
	{
		// The standard fixed-mode chatbox comfortably fits the classic
		// under-the-prompt position.
		assertEquals(GeChatboxHint.DEFAULT_Y, GeChatboxHint.hintY(142));
	}

	@Test
	public void unknownContainerHeightFallsBackToTheClassicLine()
	{
		assertEquals(GeChatboxHint.DEFAULT_Y, GeChatboxHint.hintY(0));
		assertEquals(GeChatboxHint.DEFAULT_Y, GeChatboxHint.hintY(-1));
	}

	@Test
	public void shortContainersClampTheLineOntoTheVisibleArea()
	{
		// 55 + 16 would overflow a 60px container → bottom-anchored instead.
		assertEquals(60 - GeChatboxHint.LINE_HEIGHT, GeChatboxHint.hintY(60));
		// Degenerate containers never yield a negative y.
		assertEquals(0, GeChatboxHint.hintY(10));
	}

	@Test
	public void widthFollowsTheContainerWithAClassicFallback()
	{
		assertEquals(519, GeChatboxHint.hintWidth(519));
		assertEquals(GeChatboxHint.DEFAULT_WIDTH, GeChatboxHint.hintWidth(0));
		assertEquals(GeChatboxHint.DEFAULT_WIDTH, GeChatboxHint.hintWidth(-5));
	}
}
