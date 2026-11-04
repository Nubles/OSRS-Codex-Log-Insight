package com.codex.loginsight.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/**
 * Reads a finished raid from the game's own lines at its end. The wording is the one RuneLite's chat commands plugin
 * reads and tests: "Your completed Chambers of Xeric count is: 51.", "Your completed Chambers of Xeric Challenge Mode
 * count is: 13.", "Your completed Theatre of Blood: Entry Mode count is: 73.", "Your completed Tombs of Amascut:
 * Expert Mode count is: 1.", and for the Chambers "Congratulations - your raid is complete!" followed by "Team size:
 * 3 players Duration: ...".
 */
public final class RaidReader
{
	private static final Pattern COUNT = Pattern.compile(
		"^Your completed (?<name>(?:Chambers of Xeric|Theatre of Blood|Tombs of Amascut)[^\\n]*?) count is: ?(?<kc>[0-9,]+)");
	private static final Pattern COX_DONE = Pattern.compile("^Congratulations - your raid is complete!");
	private static final Pattern TEAM = Pattern.compile("Team size: (?:(?<n>\\d+) players?|(?<solo>Solo))\\b");

	private RaidReader()
	{
	}

	/** A raid's kill count line, read. */
	public static final class Completion
	{
		private final Raids.Kind kind;
		private final String mode;
		private final int count;

		Completion(Raids.Kind kind, String mode, int count)
		{
			this.kind = kind;
			this.mode = mode;
			this.count = count;
		}

		public Raids.Kind getKind()
		{
			return kind;
		}

		public String getMode()
		{
			return mode;
		}

		public int getCount()
		{
			return count;
		}
	}

	/** The raid a kill count line completes, or null when it is not a raid's. */
	public static Completion completion(String message)
	{
		if (message == null)
		{
			return null;
		}
		String text = Text.removeTags(message).replaceAll("@[a-z]{2,6}@", "").trim();
		Matcher m = COUNT.matcher(text);
		if (!m.find())
		{
			return null;
		}
		String name = m.group("name");
		Raids.Kind kind = name.startsWith("Chambers") ? Raids.Kind.COX : name.startsWith("Theatre") ? Raids.Kind.TOB
			: Raids.Kind.TOA;
		int kc;
		try
		{
			kc = Integer.parseInt(m.group("kc").replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			kc = -1;
		}
		return new Completion(kind, Raids.mode(name), kc);
	}

	/** True for the Chambers' "raid is complete" line. */
	public static boolean coxComplete(String message)
	{
		return message != null && COX_DONE.matcher(Text.removeTags(message).trim()).find();
	}

	/**
	 * The team size the Chambers' "raid is complete" line gives, or -1 when it gives a range ("11-15 players") or none.
	 */
	public static int coxTeam(String message)
	{
		if (message == null)
		{
			return -1;
		}
		// The line has a break between its two halves; the tags around the size come off with the rest.
		Matcher m = TEAM.matcher(Text.removeTags(message.replace("<br>", " ")));
		if (!m.find())
		{
			return -1;
		}
		if (m.group("solo") != null)
		{
			return 1;
		}
		return Integer.parseInt(m.group("n"));
	}
}
