package com.runeflip.companion;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Monotonic request sequence (v0.8.10 responsiveness): every fetch takes a
 * ticket via {@link #begin()}; a response only renders when its ticket is
 * still the newest ({@link #isCurrent}). Rapid pill clicks (Low→Med→High) or
 * quick item re-selections thus render ONLY the answer to the latest request
 * — an older, slower response can never overwrite a newer one. Pure counter,
 * no I/O.
 */
public class RequestSequencer
{
	private final AtomicLong counter = new AtomicLong();

	/** Starts a new request generation and returns its ticket. */
	public long begin()
	{
		return counter.incrementAndGet();
	}

	/** True while the ticket is the newest one issued. */
	public boolean isCurrent(long ticket)
	{
		return counter.get() == ticket;
	}
}
