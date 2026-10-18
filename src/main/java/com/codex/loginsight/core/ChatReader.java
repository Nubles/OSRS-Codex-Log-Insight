package com.codex.loginsight.core;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/** Reads the game messages that move the collection log: new slots and kill counts. */
public final class ChatReader
{
	private static final Pattern NEW_SLOT = Pattern.compile("New item added to your collection log: (.+)");
	// The same shape RuneLite's chat commands read, so every boss, raid and minigame count is caught.
	private static final Pattern COUNT = Pattern.compile(
		"Your (?<pre>completion count for |subdued |completed )?(?<name>.+?) (?<post>(?:(?:kill|harvest|lap|completion|success|chest|opened) )?(?:count )?)is: ?(?<count>[0-9,]+)");
	private static final Pattern CLUES = Pattern.compile("You have completed (?<count>[0-9,]+) (?<tier>beginner|easy|medium|hard|elite|master) Treasure Trails?", Pattern.CASE_INSENSITIVE);
	private static final Pattern COFFINS = Pattern.compile("You have opened the Grand Hallowed Coffin (?<count>[0-9,]+) times?!");
	private static final Pattern RIFTS = Pattern.compile("Amount of Rifts you have closed: (?<count>[0-9,]+)\\.", Pattern.CASE_INSENSITIVE);
	private static final Pattern RUMOURS = Pattern.compile("You have completed (?<count>[0-9,]+) rumours? for the Hunter Guild\\.");
	private static final Pattern SUFFIX = Pattern.compile(" (kills|completions|completion count|opened|chests opened|kill count|count)$", Pattern.CASE_INSENSITIVE);

	/** Chat names that differ from the counter the collection log shows. */
	private static final Map<String, String> ALIASES = new HashMap<>();

	static
	{
		ALIASES.put("barrows", "Barrows Chests opened");
		ALIASES.put("barrows chest", "Barrows Chests opened");
		ALIASES.put("barrows chests", "Barrows Chests opened");
		ALIASES.put("lunar chest", "Lunar Chests opened");
		ALIASES.put("lunar chests", "Lunar Chests opened");
		ALIASES.put("gauntlet", "Gauntlet completion count");
		ALIASES.put("corrupted gauntlet", "Corrupted Gauntlet completion count");
		ALIASES.put("theatre of blood: hard mode", "Theatre of Blood (Hard) completions");
		ALIASES.put("theatre of blood: entry mode", "Theatre of Blood (Entry) completions");
		ALIASES.put("theatre of blood: story mode", "Theatre of Blood (Entry) completions");
		ALIASES.put("tombs of amascut: expert mode", "Tombs of Amascut (Expert) completions");
		ALIASES.put("tombs of amascut: entry mode", "Tombs of Amascut (Entry) completions");
		ALIASES.put("chambers of xeric challenge mode", "Chambers of Xeric (CM) completions");
		ALIASES.put("chambers of xeric: challenge mode", "Chambers of Xeric (CM) completions");
		ALIASES.put("the leviathan", "Leviathan kills");
		ALIASES.put("the whisperer", "Whisperer kills");
		ALIASES.put("the nightmare", "Nightmare kills");
		ALIASES.put("the hueycoatl", "Hueycoatl kills");
		ALIASES.put("tztok-jad", "TzTok-Jad kills");
		ALIASES.put("tzkal-zuk", "TzKal-Zuk kills");
		ALIASES.put("wintertodt", "Wintertodt kills");
		ALIASES.put("hespori", "Hespori kills");
		ALIASES.put("grotesque guardians", "Grotesque Guardian kills");
		ALIASES.put("royal titans", "Royal Titan kills");
		ALIASES.put("high-level gamble", "High-level Gambles");
		ALIASES.put("barrows chests", "Barrows Chests opened");
	}

	/** Counters RuneLite's chat commands save under a name of their own. */
	private static final Map<String, String> SAVED_AS = new HashMap<>();

	static
	{
		SAVED_AS.put("Barrows Chests opened", "barrows chests");
		SAVED_AS.put("Grand Hallowed Coffins opened", "hallowed sepulchre");
		SAVED_AS.put("Rifts closed", "guardians of the rift");
		SAVED_AS.put("Rumours Completed", "hunter rumours");
	}

	private final Map<String, String> labelsByBase = new HashMap<>();
	private final java.util.Set<String> labels = new java.util.HashSet<>();

	/** @param labels every counter name the odds use */
	public ChatReader(List<String> labels)
	{
		for (String label : labels)
		{
			labelsByBase.put(base(label), label);
			this.labels.add(label);
		}
	}

	/** The item name of a "New item added" message, or null. */
	public static String newSlot(String message)
	{
		Matcher m = NEW_SLOT.matcher(Text.removeTags(message));
		return m.find() ? m.group(1).trim() : null;
	}

	private static final Pattern PAGE_COUNTER = Pattern.compile("^(?<label>[^:]+?):\\s*(?<count>[0-9,]+)$");

	/** A counter line from a collection log page header, such as "Vorkath kills: 312", or null. */
	public static Count pageCounter(String line)
	{
		Matcher m = PAGE_COUNTER.matcher(Text.removeTags(line).trim());
		if (!m.find())
		{
			return null;
		}
		int value = parse(m.group("count"));
		return value < 0 ? null : new Count(m.group("label").trim(), value);
	}

	/** A kill count message read as a collection log counter, or null. */
	public Count count(String message)
	{
		// Some counters arrive with colour shorthand such as @red@ as well as tags.
		String text = Text.removeTags(message).replaceAll("@[a-z]{2,6}@", "");
		Matcher clue = CLUES.matcher(text);
		if (clue.find())
		{
			String tier = clue.group("tier");
			String label = Character.toUpperCase(tier.charAt(0)) + tier.substring(1).toLowerCase(Locale.ENGLISH) + " clues completed";
			return new Count(label, parse(clue.group("count")));
		}
		Count other = fixed(text, COFFINS, "Grand Hallowed Coffins opened");
		other = other != null ? other : fixed(text, RIFTS, "Rifts closed");
		other = other != null ? other : fixed(text, RUMOURS, "Rumours Completed");
		if (other != null)
		{
			return other;
		}
		Matcher m = COUNT.matcher(text);
		if (!m.find())
		{
			return null;
		}
		String name = m.group("name").trim();
		String label = labelFor(name);
		return label == null ? null : new Count(label, parse(m.group("count")));
	}

	private Count fixed(String text, Pattern pattern, String label)
	{
		if (!labels.contains(label))
		{
			return null;
		}
		Matcher m = pattern.matcher(text);
		return m.find() ? new Count(label, parse(m.group("count"))) : null;
	}

	/**
	 * Where RuneLite's chat commands may have saved this counter, best guess first. They
	 * save the name as the chat message gives it, lower case and without colons.
	 */
	public static List<String> savedKeys(String label)
	{
		List<String> keys = new java.util.ArrayList<>();
		String saved = SAVED_AS.get(label);
		if (saved != null)
		{
			keys.add(saved);
		}
		keys.add(base(label));
		for (Map.Entry<String, String> alias : ALIASES.entrySet())
		{
			String key = alias.getKey().replace(":", "");
			if (alias.getValue().equals(label) && !keys.contains(key))
			{
				keys.add(key);
			}
		}
		return keys;
	}

	/** The counter a chat name refers to, or null if the log has no such counter. */
	String labelFor(String chatName)
	{
		String key = chatName.trim().toLowerCase(Locale.ENGLISH);
		String alias = ALIASES.get(key);
		if (alias != null)
		{
			return alias;
		}
		String label = labelsByBase.get(key);
		if (label == null && key.startsWith("the "))
		{
			label = labelsByBase.get(key.substring(4));
		}
		return label;
	}

	/** "Vorkath kills" to "vorkath", the key RuneLite's chat commands save counts under. */
	public static String base(String label)
	{
		return SUFFIX.matcher(label.trim()).replaceFirst("").toLowerCase(Locale.ENGLISH);
	}

	private static int parse(String digits)
	{
		try
		{
			return Integer.parseInt(digits.replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	public static final class Count
	{
		public final String label;
		public final int value;

		Count(String label, int value)
		{
			this.label = label;
			this.value = value;
		}
	}
}
