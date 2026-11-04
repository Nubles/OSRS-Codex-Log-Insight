package com.codex.loginsight.core;

/**
 * The Doom of Mokhaiotl's unique table, per delve level, as the OSRS Wiki gives it ("Doom of Mokhaiotl", unique drop
 * rates). Every completed delve level from 2 rolls once; a run's chance of a unique is the chance over every level it
 * cleared. The rates rise until delve 9 ("The drop rate of these items will increase until delve 9, at which they are
 * rolled at their highest possible rate"), so every level from 9 down uses the delve 9 row. Kept free of game types.
 */
public final class Delve
{
	/** The deepest level with its own rates and its own stats; every level below it is pooled with it. */
	public static final int DEEPEST_ROW = 9;

	/** Item ids (RuneLite gameval ItemID) of the four uniques, in the order they unlock. */
	public static final int CLOTH = 31109;
	public static final int EYE = 31115;
	public static final int TREADS = 31088;
	public static final int DOM = 31130;
	public static final int[] UNIQUES = {CLOTH, EYE, TREADS, DOM};
	public static final String[] NAMES = {"Mokhaiotl cloth", "Eye of Ayak (uncharged)", "Avernic treads", "Dom"};

	/**
	 * "1 in N" for each unique at each delve level, indexed [unique][level]; 0 where it cannot drop. Delve 1 drops no
	 * unique. Wiki table rows 2 to 9; Dom's footnote: "1/1000 at delve level 6, 1/750 at delve level 7, 1/500 at delve
	 * level 8, and 1/250 at delve level 9 and beyond."
	 */
	private static final int[][] ONE_IN = {
		{0, 0, 2500, 2000, 1350, 810, 765, 720, 630, 540},
		{0, 0, 0, 2000, 1350, 810, 765, 720, 630, 540},
		{0, 0, 0, 0, 1350, 810, 765, 720, 630, 540},
		{0, 0, 0, 0, 0, 0, 1000, 750, 500, 250},
	};

	private Delve()
	{
	}

	/** The row a level reads from: levels past 9 roll as 9 does. */
	public static int row(int level)
	{
		return Math.max(1, Math.min(DEEPEST_ROW, level));
	}

	/** The index of a unique in {@link #UNIQUES}, or -1. */
	public static int index(int itemId)
	{
		for (int i = 0; i < UNIQUES.length; i++)
		{
			if (UNIQUES[i] == itemId)
			{
				return i;
			}
		}
		return -1;
	}

	/** The chance of this unique on clearing one delve level. */
	public static double chance(int unique, int level)
	{
		if (level < 1)
		{
			return 0;
		}
		int n = ONE_IN[unique][row(level)];
		return n == 0 ? 0 : 1.0 / n;
	}

	/** "1 in N" for one level, or 0 when it cannot drop there. */
	public static int oneIn(int unique, int level)
	{
		return level < 1 ? 0 : ONE_IN[unique][row(level)];
	}

	/** The first level this unique can drop on. */
	public static int firstLevel(int unique)
	{
		for (int level = 1; level <= DEEPEST_ROW; level++)
		{
			if (ONE_IN[unique][level] > 0)
			{
				return level;
			}
		}
		return Integer.MAX_VALUE;
	}

	/** The log of the chance of no copy of this unique from a run that cleared every level from 1 to {@code deepest}. */
	public static double runMissLog(int unique, int deepest)
	{
		double log = 0;
		for (int level = 2; level <= deepest; level++)
		{
			log += Math.log1p(-chance(unique, level));
		}
		return log;
	}

	/** The chance of this unique from a run that cleared every level from 1 to {@code deepest}. */
	public static double runChance(int unique, int deepest)
	{
		return -Math.expm1(runMissLog(unique, deepest));
	}

	/**
	 * The chance of one of the three non-pet uniques on clearing one level: the wiki's "Overall chance" column, which is
	 * their sum (delve 3: 1/2,000 + 1/2,000 = 1/1,000).
	 */
	public static double overall(int level)
	{
		double sum = 0;
		for (int u = 0; u < 3; u++)
		{
			sum += chance(u, level);
		}
		return sum;
	}

	/**
	 * The log of the chance of no copy of this unique from a number of cleared levels at each row: {@code cleared[r]} is
	 * how many times a level of row r was cleared and banked.
	 */
	public static double missLog(int unique, int[] cleared)
	{
		double log = 0;
		for (int r = 1; r < cleared.length && r <= DEEPEST_ROW; r++)
		{
			if (cleared[r] > 0)
			{
				log += cleared[r] * Math.log1p(-chance(unique, r));
			}
		}
		return log;
	}

	/** How many copies of this unique an average player would have from these cleared levels. */
	public static double expected(int unique, int[] cleared)
	{
		double sum = 0;
		for (int r = 1; r < cleared.length && r <= DEEPEST_ROW; r++)
		{
			sum += cleared[r] * chance(unique, r);
		}
		return sum;
	}
}
