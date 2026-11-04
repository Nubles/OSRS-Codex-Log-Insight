package com.codex.loginsight.core;

import java.util.Locale;
import net.runelite.api.gameval.ItemID;

/**
 * The three raids' purple (unique) chances, as the OSRS Wiki states them, worked from what the game tells the client
 * once a raid is over. Only what the wiki says plainly is a fact here; where a share of the team's chance has to be
 * assumed, {@link Chance#isEstimate()} says so. Kept free of client types.
 *
 * <ul>
 * <li>Chambers of Xeric (wiki, "Ancient chest"): "For every 8,676 total points obtained, a 1% chance to obtain a
 * unique loot is given. This chance is capped at 65.7% (570,000 points) - any further points will be sent to roll for
 * a second unique loot." and "Up to six unique rewards can be obtained per raid". Who gets it: "In groups, the
 * player's points will also determine who is more likely to obtain the unique drop", with no formula given, so your
 * share is assumed to follow your share of the points.</li>
 * <li>Theatre of Blood (wiki, "Monumental chest"): "Without any deaths, there is a 1/9.1 (~11%) chance of a unique
 * reward across the team; dying will decrease this chance. The drop rate is the same regardless of the number of
 * players completing the raid." and "For Hard Mode, the unique chance is increased to 1/7.7 (~13%)". Who gets it:
 * "The item is allocated to one player, with the weighting based on deaths and damage dealt to each boss.", with no
 * formula given, so an even share is assumed. The wiki gives no Entry Mode chance, so Entry runs are not counted.</li>
 * <li>Tombs of Amascut (wiki, "Chest (Tombs of Amascut)"): "Players will have a 1% chance to receive a unique item
 * for every 10,500−20×RL total reward points", RL being the raid level scaled down past 310 and 430, "with a
 * maximum rate of 55% to see a unique at the end of the raid", and "the individual probabilities being proportional
 * to the players' relative contribution to the total." "At the end of the raid, the starting 5,000 points are
 * subtracted when calculating loot and purple percentage."</li>
 * </ul>
 */
public final class Raids
{
	public enum Kind
	{
		COX("cox", "Chambers of Xeric", "CoX"),
		TOB("tob", "Theatre of Blood", "ToB"),
		TOA("toa", "Tombs of Amascut", "ToA");

		private final String key;
		private final String title;
		private final String abbreviation;

		Kind(String key, String title, String abbreviation)
		{
			this.key = key;
			this.title = title;
			this.abbreviation = abbreviation;
		}

		/** The name saved with each run. */
		public String getKey()
		{
			return key;
		}

		/** The raid's name, as its collection log page has it. */
		public String getTitle()
		{
			return title;
		}

		public String getAbbreviation()
		{
			return abbreviation;
		}

		public static Kind ofKey(String key)
		{
			for (Kind kind : values())
			{
				if (kind.key.equals(key))
				{
					return kind;
				}
			}
			return null;
		}

		/** The raid whose collection log page this is, or null. */
		public static Kind ofPage(String page)
		{
			if (page == null)
			{
				return null;
			}
			String p = page.toLowerCase(Locale.ENGLISH);
			if (p.contains("chambers of xeric"))
			{
				return COX;
			}
			if (p.contains("theatre of blood"))
			{
				return TOB;
			}
			if (p.contains("tombs of amascut"))
			{
				return TOA;
			}
			return null;
		}
	}

	/** Chambers of Xeric: total points for each 1% of a unique roll. */
	public static final int COX_POINTS_PER_PERCENT = 8676;
	/** Chambers of Xeric: the points one roll can use; the rest go to the next roll. */
	public static final int COX_POINTS_PER_ROLL = 570_000;
	/** Chambers of Xeric: the cap on one roll, as the wiki writes it. */
	public static final double COX_ROLL_CAP = 0.657;
	public static final int COX_MAX_UNIQUES = 6;
	/** Theatre of Blood: the team's chance without deaths, normal and Hard Mode. */
	public static final double TOB_NORMAL = 1 / 9.1;
	public static final double TOB_HARD = 1 / 7.7;
	/** Tombs of Amascut: the cap on the team's chance. */
	public static final double TOA_CAP = 0.55;
	/**
	 * Tombs of Amascut: the reward points every player starts with, which the wiki says "are subtracted when
	 * calculating loot and purple percentage".
	 */
	public static final int TOA_START_POINTS = 5000;

	/** Theatre of Blood modes, as the kill count line names them. */
	public static final String NORMAL = "Normal";
	public static final String HARD = "Hard";
	public static final String ENTRY = "Entry";
	public static final String EXPERT = "Expert";
	public static final String CHALLENGE = "Challenge";

	/** Each raid's uniques (the wiki's unique tables), as RuneLite gameval item ids. */
	private static final int[] COX_UNIQUES = {
		ItemID.RAIDS_PRAYERSCROLL, ItemID.RAIDS_PRAYERSCROLL_AUGURY, ItemID.TWISTED_BUCKLER, ItemID.DRAGONHUNTER_XBOW,
		ItemID.DINHS_BULWARK, ItemID.ANCESTRAL_HAT, ItemID.ANCESTRAL_ROBE_TOP, ItemID.ANCESTRAL_ROBE_BOTTOM,
		ItemID.DRAGON_CLAWS, ItemID.ELDER_MAUL, ItemID.KODAI_INSIGNIA, ItemID.TWISTED_BOW,
	};
	private static final int[] TOB_UNIQUES = {
		ItemID.INFERNAL_DEFENDER_HILT, ItemID.GHRAZI_RAPIER, ItemID.SANGUINESTI_STAFF_UNCHARGED,
		ItemID.JUSTICIAR_FACEGUARD, ItemID.JUSTICIAR_CHESTGUARD, ItemID.JUSTICIAR_LEG_GUARDS,
		ItemID.SCYTHE_OF_VITUR_UNCHARGED,
	};
	private static final int[] TOA_UNIQUES = {
		ItemID.OSMUMTENS_FANG, ItemID.LIGHTBEARER, ItemID.ELIDINIS_WARD, ItemID.MASORI_MASK, ItemID.MASORI_BODY,
		ItemID.MASORI_CHAPS, ItemID.TUMEKENS_SHADOW_UNCHARGED,
	};

	private Raids()
	{
	}

	/** The raid this item is a unique of, or null. */
	public static Kind uniqueOf(int itemId)
	{
		for (Kind kind : Kind.values())
		{
			for (int id : uniques(kind))
			{
				if (id == itemId)
				{
					return kind;
				}
			}
		}
		return null;
	}

	public static int[] uniques(Kind kind)
	{
		switch (kind)
		{
			case COX:
				return COX_UNIQUES.clone();
			case TOB:
				return TOB_UNIQUES.clone();
			default:
				return TOA_UNIQUES.clone();
		}
	}

	/** One raid's purple chance, for the team and for you. */
	public static final class Chance
	{
		static final Chance UNKNOWN = new Chance(-1, -1, -1, false, null);

		private final double team;
		private final double yours;
		private final double expected;
		private final boolean estimate;
		private final String why;

		Chance(double team, double yours, double expected, boolean estimate, String why)
		{
			this.team = team;
			this.yours = yours;
			this.expected = expected;
			this.estimate = estimate;
			this.why = why;
		}

		/** The chance of at least one purple for the whole team, or -1 when it is not known. */
		public double getTeam()
		{
			return team;
		}

		/** The chance of a purple in your own chest, or -1 when it is not known. */
		public double getYours()
		{
			return yours;
		}

		/** How many purples you would get from this raid on average, or -1 when it is not known. */
		public double getExpected()
		{
			return expected;
		}

		/** True when your share of the team's chance had to be assumed (the wiki gives no formula for it). */
		public boolean isEstimate()
		{
			return estimate;
		}

		/** Why the chance is unknown or assumed, or null. */
		public String getWhy()
		{
			return why;
		}

		public boolean isKnown()
		{
			return yours >= 0;
		}
	}

	/**
	 * The chance of each of a Chambers of Xeric raid's unique rolls: 1% per 8,676 points, each roll using up to 570,000
	 * points (65.7%) and the rest rolling again, up to six rolls.
	 */
	public static double[] coxRolls(long total)
	{
		int n = 0;
		double[] rolls = new double[COX_MAX_UNIQUES];
		long left = Math.max(0, total);
		while (left > 0 && n < COX_MAX_UNIQUES)
		{
			long used = Math.min(left, COX_POINTS_PER_ROLL);
			rolls[n++] = Math.min(COX_ROLL_CAP, used / (COX_POINTS_PER_PERCENT * 100.0));
			left -= used;
		}
		return java.util.Arrays.copyOf(rolls, n);
	}

	/** A Chambers of Xeric raid's chance from your points and the team's total, as the raid ended. */
	public static Chance cox(long personal, long total)
	{
		if (total <= 0 || personal < 0)
		{
			return new Chance(-1, -1, -1, false, "no points were read");
		}
		double[] rolls = coxRolls(total);
		double share = Math.min(1, personal / (double) total);
		double expected = 0;
		double none = 1;
		for (double roll : rolls)
		{
			expected += roll * share;
			none *= 1 - roll * share;
		}
		boolean shared = personal < total;
		return new Chance(rolls.length == 0 ? 0 : rolls[0], 1 - none, expected, shared,
			shared ? "your share is taken as your share of the points" : null);
	}

	/** A Theatre of Blood raid's chance: the team's chance by mode, shared evenly among the team. */
	public static Chance tob(String mode, int team)
	{
		if (ENTRY.equals(mode))
		{
			return new Chance(-1, -1, -1, false, "the wiki gives no Entry Mode chance");
		}
		if (team < 1)
		{
			return new Chance(-1, -1, -1, false, "the team size was not read");
		}
		double rate = HARD.equals(mode) ? TOB_HARD : TOB_NORMAL;
		double yours = rate / team;
		boolean shared = team > 1;
		return new Chance(rate, yours, yours, shared, shared ? "an even share is assumed; deaths are not counted"
			: "deaths are not counted");
	}

	/** Tombs of Amascut's raid level as the formula scales it ("RL"). */
	public static double toaScaledLevel(int raidLevel)
	{
		if (raidLevel <= 310)
		{
			return raidLevel;
		}
		if (raidLevel <= 430)
		{
			return 310 + (raidLevel - 310) / 3.0;
		}
		return 350 + (raidLevel - 430) / 6.0;
	}

	/** Tombs of Amascut: the points for each 1% of a unique at this raid level, 10,500 − 20 × RL. */
	public static double toaPointsPerPercent(int raidLevel)
	{
		return 10_500 - 20 * toaScaledLevel(raidLevel);
	}

	/**
	 * The points a Tombs of Amascut purple chance is worked from, given a player's total reward points: the total less
	 * the starting 5,000, never below 0.
	 */
	public static long toaChancePoints(long totalRewardPoints)
	{
		return Math.max(0, totalRewardPoints - TOA_START_POINTS);
	}

	/**
	 * A Tombs of Amascut raid's chance from your points without the starting 5,000, and the raid level.
	 *
	 * <p>The game's own value, {@code VarPlayerID.TOA_PERSONAL_CONTRIBUTION}, is taken to be exactly that: points already
	 * without the starting 5,000. This is unconfirmed by the wiki or RuneLite. Its source is the Tombs of Amascut
	 * plugin's points tracker (LlemonDuck), which adds 5,000 to the value to get your total and takes the same 5,000
	 * off again before working out the purple chance; the Dink plugin's RaidTracker uses the value as it is. So the
	 * value is not reduced again here, which would take the 5,000 off twice. Your
	 * share is your points over the team's, so your chance is your own points' percent, unless the team reached the
	 * 55% cap; alone, the team is you. In a team the total is not known to the client, so the cap is assumed unmet.
	 */
	public static Chance toa(long personal, int raidLevel, int team)
	{
		if (personal < 0 || raidLevel < 0)
		{
			return new Chance(-1, -1, -1, false, personal < 0 ? "no points were read" : "the raid level was not read");
		}
		double per = toaPointsPerPercent(raidLevel);
		if (per <= 0)
		{
			return new Chance(-1, -1, -1, false, "the raid level is out of range");
		}
		double yours = Math.min(TOA_CAP, personal / (per * 100));
		boolean solo = team == 1;
		return new Chance(solo ? yours : -1, yours, yours, !solo, solo ? null : "assumes the team stayed under the 55% cap");
	}

	/** "Hard Mode" from the kill count line's raid name, or {@link #NORMAL}. */
	public static String mode(String raidName)
	{
		String name = raidName == null ? "" : raidName.toLowerCase(Locale.ENGLISH);
		if (name.contains("challenge mode"))
		{
			return CHALLENGE;
		}
		if (name.contains("hard mode"))
		{
			return HARD;
		}
		if (name.contains("entry mode") || name.contains("story mode"))
		{
			return ENTRY;
		}
		if (name.contains("expert mode"))
		{
			return EXPERT;
		}
		return NORMAL;
	}
}
