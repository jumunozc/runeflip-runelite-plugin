package com.runeflip.companion;

import java.util.HashMap;
import java.util.Map;

/**
 * Tiny in-memory TTL cache for the panel's read responses (v0.8.10
 * responsiveness). Toggling a strategy pill back and forth, or re-opening the
 * same GE item, re-renders instantly from here instead of waiting a network
 * round-trip. Pure map + timestamps — no I/O, no eviction thread (entries are
 * few: one per strategy key / selected item), caller supplies the clock so
 * tests stay deterministic. Display-only data; a stale entry can at worst
 * show ~TTL-old market numbers, the same staleness the regular refresh cycle
 * already allows.
 */
public class ShortTtlCache<T>
{
	private final long ttlMs;
	private final Map<String, Entry<T>> entries = new HashMap<>();

	private static class Entry<T>
	{
		final T value;
		final long storedAtMs;

		Entry(T value, long storedAtMs)
		{
			this.value = value;
			this.storedAtMs = storedAtMs;
		}
	}

	public ShortTtlCache(long ttlMs)
	{
		this.ttlMs = ttlMs;
	}

	/** The cached value for the key, or null when absent or expired. */
	public synchronized T get(String key, long nowMs)
	{
		Entry<T> entry = entries.get(key);
		if (entry == null)
		{
			return null;
		}
		if (nowMs - entry.storedAtMs > ttlMs)
		{
			entries.remove(key);
			return null;
		}
		return entry.value;
	}

	/** Stores/replaces the value for the key. Null values are not cached. */
	public synchronized void put(String key, T value, long nowMs)
	{
		if (key == null || value == null)
		{
			return;
		}
		entries.put(key, new Entry<>(value, nowMs));
	}

	public synchronized void clear()
	{
		entries.clear();
	}
}
