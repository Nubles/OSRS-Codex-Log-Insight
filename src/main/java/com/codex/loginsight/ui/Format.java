package com.codex.loginsight.ui;

/** Turns hours, odds and counts into the short phrases the panel uses. */
public final class Format
{
	private Format()
	{
	}

	/** "<1m", "45m", "3h 12m", "120h" or "8,420h"; "-" when it cannot be estimated. */
	public static String hours(double hours)
	{
		if (Double.isNaN(hours) || Double.isInfinite(hours) || hours < 0)
		{
			return "-";
		}
		double minutes = hours * 60;
		if (minutes < 1)
		{
			return "<1m";
		}
		if (minutes < 60)
		{
			return Math.round(minutes) + "m";
		}
		if (hours < 10)
		{
			long h = (long) hours;
			long m = Math.round((hours - h) * 60);
			if (m == 60)
			{
				h++;
				m = 0;
			}
			return m == 0 ? h + "h" : h + "h " + m + "m";
		}
		return String.format("%,dh", Math.round(hours));
	}

	/** "about 3h", or "under 1m" when it is that short. */
	public static String about(double hours)
	{
		String text = hours(hours);
		if (text.startsWith("<"))
		{
			return "under " + text.substring(1);
		}
		return "-".equals(text) ? text : "~" + text;
	}

	/** "1/512", or "1/3.4" for common drops; "-" when unknown. */
	public static String chance(double chance)
	{
		if (chance <= 0 || Double.isNaN(chance))
		{
			return "-";
		}
		double in = 1 / chance;
		if (in < 1.05)
		{
			return "Always";
		}
		if (in < 10 && Math.abs(in - Math.round(in)) >= 0.05)
		{
			return String.format("1/%.1f", in);
		}
		return String.format("1/%,d", Math.round(in));
	}

	/**
	 * How dry a missing slot is, the way players say it: the share of players who would
	 * already have it at this kill count. "88% dry" means 88 in 100 would have it by now; "Overdue" means it is
	 * guaranteed by a count you have passed.
	 *
	 * @param stillWaiting the share of players still without it
	 */
	public static String dry(double stillWaiting)
	{
		// Only a slot guaranteed by a count you have passed leaves nobody waiting: "100% dry" beside ">99% dry" rows
		// read as a figure, where it is a slot that should have come.
		return stillWaiting == 0 ? "Overdue" : have(stillWaiting) + " dry";
	}

	/**
	 * The share of players who would have it by now, from the share still without it. Worked from the share still
	 * waiting, since one minus a tiny share rounds to exactly 1 in a double and would read as a certain 100%.
	 */
	public static String have(double stillWaiting)
	{
		if (stillWaiting > 0 && stillWaiting < 0.01)
		{
			return ">99%";
		}
		return percent(1 - stillWaiting);
	}

	/**
	 * An expected number of slots: "0.48", "2.4", "12"; "<0.01" when it rounds to nothing. A figure that rounds to a
	 * whole number drops its zeros ("9", not "9.0"), so a list reads 74, 39, 11, 9 rather than switching to decimals.
	 */
	public static String slots(double slots)
	{
		if (Double.isNaN(slots) || slots <= 0)
		{
			return "0";
		}
		if (slots < 0.01)
		{
			return "<0.01";
		}
		if (slots < 1)
		{
			return trimZeros(String.format(java.util.Locale.ENGLISH, "%.2f", slots));
		}
		if (slots < 9.95)
		{
			return trimZeros(String.format(java.util.Locale.ENGLISH, "%.1f", slots));
		}
		return String.valueOf(Math.round(slots));
	}

	/** "slot" for a figure that reads as exactly one, "slots" for any other: "1 slot", "0.99 slots", "2.4 slots". */
	public static String slotWord(double slots)
	{
		return "1".equals(slots(slots)) ? "slot" : "slots";
	}

	/** "9.0" to "9", "0.50" to "0.5"; whole numbers and other decimals are left as they are. */
	private static String trimZeros(String number)
	{
		if (number.indexOf('.') < 0)
		{
			return number;
		}
		String out = number.replaceAll("0+$", "");
		return out.endsWith(".") ? out.substring(0, out.length() - 1) : out;
	}

	public static String count(long value)
	{
		return String.format("%,d", value);
	}

	/** Whole percent, with "<1%" and ">99%" at the ends so rare cases never read as 0 or 100. */
	public static String percent(double share)
	{
		if (share <= 0)
		{
			return "0%";
		}
		if (share < 0.01)
		{
			return "<1%";
		}
		if (share > 0.99 && share < 1)
		{
			return ">99%";
		}
		return Math.round(share * 100) + "%";
	}

	/** A stretch of play: "12m", "1h 20m", "6h". */
	public static String duration(long millis)
	{
		long minutes = Math.max(0, millis / 60_000);
		if (minutes < 60)
		{
			return minutes + "m";
		}
		long h = minutes / 60;
		long m = minutes % 60;
		return m == 0 ? h + "h" : h + "h " + m + "m";
	}

	/** "just now", "5m ago", "3h ago", "2d ago". */
	public static String ago(long then, long now)
	{
		long seconds = Math.max(0, (now - then) / 1000);
		if (seconds < 60)
		{
			return "just now";
		}
		if (seconds < 3600)
		{
			return seconds / 60 + "m ago";
		}
		if (seconds < 86400)
		{
			return seconds / 3600 + "h ago";
		}
		return seconds / 86400 + "d ago";
	}

	/** "Vorkath kills" to "Vorkath", for compact lines that already say "kc". */
	public static String counterName(String label)
	{
		if (label == null)
		{
			return "";
		}
		return label.replaceAll("(?i) (kills|completions|completion count|opened|completed)$", "");
	}

	private static final java.util.regex.Pattern LEAD_VERB =
		java.util.regex.Pattern.compile("^(?i)(killing|defeating|looting|opening|finishing|completing) (the )?");
	private static final java.util.Set<String> SMALL_WORDS = new java.util.HashSet<>(java.util.Arrays.asList(
		"of", "the", "and", "at", "in", "on", "from", "with", "for", "to", "a", "an", "or", "by"));

	/**
	 * Where a slot comes from, as a short name: "Killing king black dragon" reads "King Black
	 * Dragon", "Opening hard caskets" reads "Hard Caskets". The bracketed detail is left out.
	 */
	public static String source(String name)
	{
		String text = LEAD_VERB.matcher(activity(name)).replaceFirst("");
		StringBuilder out = new StringBuilder();
		for (String word : text.split(" "))
		{
			if (word.isEmpty())
			{
				continue;
			}
			boolean small = out.length() > 0 && SMALL_WORDS.contains(word.toLowerCase(java.util.Locale.ENGLISH));
			out.append(out.length() > 0 ? " " : "")
				.append(small ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1));
		}
		if (out.length() == 0)
		{
			return name;
		}
		String full = out.toString();
		String shortName = SHORT_SOURCES.get(full);
		if (shortName != null)
		{
			return shortName;
		}
		// "Sorting Through Barracuda Salvage" and the barracuda trials are named by what sets them apart.
		return full.replaceFirst("^Sorting Through ", "").replaceFirst(" Barracuda Trials$", " Trials");
	}

	/** Places and activities that name an activity's variant in Log Adviser's names, longest first. */
	private static final String[] PROPER_NAMES = {
		"chambers of xeric challenge mode", "stronghold of security", "guardians of the rift", "hallowed sepulchre",
		"chambers of xeric", "champion's challenge", "corrupted gauntlet", "doom of mokhaiotl", "last man standing",
		"mastering mixology", "creature creation", "fortis colosseum", "temple trekking", "trouble brewing",
		"pyramid plunder", "moons of peril", "royal titans", "pest control", "vale totems", "wintertodt", "prifddinas",
		"zemouregal", "tempoross", "wyrmscraig", "non-zaros"};

	/** Long activity names, as the short name a card has room for. */
	private static final java.util.Map<String, String> SHORT_SOURCES = new java.util.HashMap<>();

	static
	{
		String[][] shorts = {
			{"Spending 100 Numulite at the Fossil Island Notes Chest", "Fossil Island Notes"},
			{"Collecting Unidentified Minerals at the Mining Guild", "Mining Guild Minerals"},
			{"Collecting Gold Nuggets at the Motherlode Mine", "Motherlode Mine"},
			{"Wasting Hours at the Magic Training Arena", "Magic Training Arena"},
			{"Charging Glories at the Fountain of Rune", "Fountain of Rune"},
			{"Earning Mahogany Homes Carpenter Points", "Mahogany Homes"},
			{"Collecting Pearls While Aerial Fishing", "Aerial Fishing"},
			{"Soldiers in the Shayzien Combat Ring", "Shayzien Combat Ring"},
			{"Pickpocketing from Prifddinas Elves", "Prifddinas Elves"},
			{"Mining Stardust from Shooting Stars", "Shooting Stars"},
			{"Earning Volcanic Mine Reward Points", "Volcanic Mine"},
			{"Chests with Shades of Mort'ton Keys", "Shades of Mort'ton"},
			{"Stealing from the Rogues' Den Maze", "Rogues' Den"},
			{"Pickpocketing from Darkmeyer Vyres", "Darkmeyer Vyres"},
			{"In the Wilderness God Wars Dungeon", "Wilderness God Wars"},
			{"Earning Giants' Foundry Reputation", "Giants' Foundry"},
			{"Doing Entling Events for God Eggs", "Entling Events"},
			{"Pulling from the Reward Guardian", "Guardians of the Rift"},
			{"Finding Lumberjack Zombie Events", "Lumberjack Zombies"},
			{"Earning Tithe Farm Reward Points", "Tithe Farm"},
			{"Earning Last Man Standing Points", "Last Man Standing"},
			{"Skeletons in the Ancient Cavern", "Ancient Cavern"},
			{"Cyclopes at the Warriors' Guild", "Warriors' Guild"},
			{"Barronite Deposits in Camdozaal", "Barronite Deposits"},
			{"Tombs of Amascut Reward Chests", "Tombs of Amascut Chests"},
			{"Running Laps of Monkey Agility", "Ape Atoll Agility"},
			{"Barbarian Assault High Gambles", "High Gambles"},
			{"Collecting Anima-infused Bark", "Anima-infused Bark"},
		};
		for (String[] pair : shorts)
		{
			SHORT_SOURCES.put(pair[0], pair[1]);
		}
	}

	private static String titleCase(String text)
	{
		StringBuilder out = new StringBuilder();
		for (String word : text.split(" "))
		{
			boolean small = out.length() > 0 && SMALL_WORDS.contains(word.toLowerCase(java.util.Locale.ENGLISH));
			out.append(out.length() > 0 ? " " : "")
				.append(small || word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1));
		}
		return out.toString();
	}

	/** The bracketed detail of an activity name, such as "on task" or "300", or null. */
	public static String detail(String name)
	{
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(([^)]*)\\)").matcher(name);
		if (!m.find())
		{
			return null;
		}
		String detail = m.group(1).trim();
		// Tombs of Amascut rows differ only by raid level.
		detail = detail.matches("[0-9]+") && name.toLowerCase(java.util.Locale.ENGLISH).contains("amascut")
			? "raid level " + detail : detail;
		// Names of places and activities read as names, the way the log's own pages write them.
		String lower = detail.toLowerCase(java.util.Locale.ENGLISH);
		for (String proper : PROPER_NAMES)
		{
			int at = lower.indexOf(proper);
			if (at >= 0)
			{
				detail = detail.substring(0, at) + titleCase(detail.substring(at, at + proper.length()))
					+ detail.substring(at + proper.length());
			}
		}
		// A tag under a title starts with a capital, like every other line on the card.
		return detail.isEmpty() ? detail : Character.toUpperCase(detail.charAt(0)) + detail.substring(1);
	}

	/** An activity name for a card title: the parenthetical detail moves to the tooltip. */
	public static String activity(String name)
	{
		String shortName = name.replaceAll("\\s*\\([^)]*\\)", "").replaceAll("\\s+", " ").trim();
		return shortName.isEmpty() ? name : Character.toUpperCase(shortName.charAt(0)) + shortName.substring(1);
	}
}
