package com.runeflip.companion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BackendUrlTest
{
	@Test
	public void acceptsBaseUrlWithApiSuffix()
	{
		assertEquals(
			"http://localhost:3005/api/ge-slots/snapshot",
			BackendUrl.snapshotEndpoint("http://localhost:3005/api"));
	}

	@Test
	public void appendsApiWhenMissing()
	{
		assertEquals(
			"http://localhost:3005/api/ge-slots/snapshot",
			BackendUrl.snapshotEndpoint("http://localhost:3005"));
	}

	@Test
	public void stripsTrailingSlashesAndWhitespace()
	{
		assertEquals(
			"https://runeflip.local/api/ge-slots/snapshot",
			BackendUrl.snapshotEndpoint("  https://runeflip.local/api//  "));
	}

	@Test
	public void rejectsEmptyOrNonHttpUrls()
	{
		assertNull(BackendUrl.snapshotEndpoint(null));
		assertNull(BackendUrl.snapshotEndpoint(""));
		assertNull(BackendUrl.snapshotEndpoint("   "));
		assertNull(BackendUrl.snapshotEndpoint("localhost:3005"));
		assertNull(BackendUrl.snapshotEndpoint("ftp://host/api"));
		assertNull(BackendUrl.snapshotEndpoint("http://"));
	}
}
