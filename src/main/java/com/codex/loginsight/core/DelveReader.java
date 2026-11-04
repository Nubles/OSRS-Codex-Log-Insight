package com.codex.loginsight.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/**
 * Reads which delve level was just cleared at the Doom of Mokhaiotl, from the game message at the end of each level and
 * from the title of the window that offers to claim or go deeper.
 */
public final class DelveReader
{
	/**
	 * "Delve level: 4 duration: ..." up to delve 8, then "Delve level: 8+ (9) duration: ...". The wording is not on the
	 * OSRS Wiki; it is the line the Deep Delve Pacer plugin reads (BSD 2-Clause, DustinKieler), re-implemented here.
	 */
	private static final Pattern LEVEL = Pattern.compile("^Delve level: (\\d+)(?:\\+ \\((\\d+)\\))? duration:");
	/** The end-of-level window's title, "Level 2 Complete!", as RuneLite's loot tracker reads it. */
	private static final Pattern TITLE = Pattern.compile("^Level (\\d+) Complete!");

	private DelveReader()
	{
	}

	/** The level a "Delve level" game message says was cleared, or -1. */
	public static int levelMessage(String message)
	{
		if (message == null)
		{
			return -1;
		}
		Matcher m = LEVEL.matcher(Text.removeTags(message).trim());
		if (!m.find())
		{
			return -1;
		}
		return parse(m.group(2) != null ? m.group(2) : m.group(1));
	}

	/** The level the end-of-level window's title names, or -1. */
	public static int levelTitle(String title)
	{
		if (title == null)
		{
			return -1;
		}
		Matcher m = TITLE.matcher(Text.removeTags(title).trim());
		return m.find() ? parse(m.group(1)) : -1;
	}

	private static int parse(String digits)
	{
		try
		{
			int level = Integer.parseInt(digits);
			return level >= 1 ? level : -1;
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}
}
