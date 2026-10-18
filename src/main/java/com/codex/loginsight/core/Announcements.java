package com.codex.loginsight.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;

/**
 * The game can announce one new slot twice: once in chat and once in the pop-up. Several
 * slots from one casket arrive in chat together, while their pop-ups queue and show one
 * after another, so each announcement is matched against an unclaimed one of the same
 * slot from the other channel rather than only the last one seen.
 */
public final class Announcements
{
	public enum Channel
	{
		CHAT,
		POPUP
	}

	/** Pop-ups queue behind each other, so their partner can be a while behind. */
	static final long WINDOW_MS = 120_000;

	private final Deque<Entry> recent = new ArrayDeque<>();

	/** @return true when this slot has not already been announced by the other channel */
	public boolean fresh(String slot, Channel channel, long now)
	{
		if (slot == null || slot.trim().isEmpty())
		{
			return false;
		}
		while (!recent.isEmpty() && now - recent.peekFirst().at > WINDOW_MS)
		{
			recent.removeFirst();
		}
		String key = slot.trim().toLowerCase(Locale.ENGLISH);
		for (Iterator<Entry> it = recent.iterator(); it.hasNext(); )
		{
			Entry entry = it.next();
			if (entry.channel != channel && entry.slot.equals(key))
			{
				it.remove();
				return false;
			}
		}
		recent.addLast(new Entry(key, channel, now));
		return true;
	}

	public void clear()
	{
		recent.clear();
	}

	private static final class Entry
	{
		final String slot;
		final Channel channel;
		final long at;

		Entry(String slot, Channel channel, long at)
		{
			this.slot = slot;
			this.channel = channel;
			this.at = at;
		}
	}
}
