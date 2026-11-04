package com.codex.loginsight.core;

import java.util.ArrayList;
import java.util.List;

/**
 * This account's delves at the Doom of Mokhaiotl, saved per RuneScape profile: how deep each run went and how it ended.
 * Plain fields so Gson can save it as it is.
 *
 * <p>A run is every level cleared from delve 1 until the loot is claimed, the player leaves, or the Doom kills them.
 * The wiki: "Claiming the rewards, dying to the boss, or teleporting out will reset the delve level back to 1", and "If
 * the player descends into a deeper delve level and is killed by the Doom, they will forfeit all accumulated drops from
 * that run." So the levels of a run that ended in death rolled for nothing the log can show; only the levels of a run
 * that was claimed or left (its loot waits in the lobby chest) count towards the uniques.
 */
public final class DelveLog
{
	/** Rows 1 to 9: a level past 9 rolls, and fights, as 9 does. */
	static final int ROWS = Delve.DEEPEST_ROW + 1;
	/** Runs kept for the strip of recent runs and the usual depth. */
	static final int MAX_RECENT = 60;
	static final int MAX_FINDS = 40;
	/** A gap longer than this between two levels is a break, not the time a level took. */
	static final long MAX_LEVEL_MS = 15 * 60_000L;
	/** A unique announced this long after a run ended still came from it. */
	static final long FIND_WINDOW_MS = 10 * 60_000L;
	/** A run left open this long was left: the next visit starts again at delve 1. */
	static final long STALE_MS = 6 * 3_600_000L;
	/** Two death animations this close together are one death. */
	static final long SAME_DEATH_MS = 20_000L;

	public static final String CLAIMED = "claimed";
	public static final String LEFT = "left";
	public static final String DIED = "died";

	/** When the first run was seen. */
	private long since;
	/** Levels cleared in runs whose loot was kept, by row. */
	private int[] banked = new int[ROWS];
	/** Levels cleared in runs that ended in death, by row. */
	private int[] lost = new int[ROWS];
	/** Deaths on each row's level. */
	private int[] deaths = new int[ROWS];
	/** Milliseconds timed on each row's levels, and how many levels were timed. */
	private long[] timeMs = new long[ROWS];
	private int[] timed = new int[ROWS];
	private int bankedRuns;
	private int diedRuns;
	private int deepest;
	/** The run under way, or null. */
	private Run open;
	/** Finished runs, newest first. */
	private List<Run> recent = new ArrayList<>();
	/** Uniques seen as they dropped, newest first. */
	private List<Find> finds = new ArrayList<>();
	/** When the last run ended, for timing the first level of the next. */
	private long lastEnd;

	public static final class Run
	{
		private long start;
		private long last;
		private int deepest;
		private String outcome;

		Run(long start, int deepest)
		{
			this.start = start;
			this.last = start;
			this.deepest = deepest;
		}

		public int getDeepest()
		{
			return deepest;
		}

		public String getOutcome()
		{
			return outcome;
		}

		public long getLast()
		{
			return last;
		}

		public boolean banked()
		{
			return CLAIMED.equals(outcome) || LEFT.equals(outcome);
		}
	}

	/** A unique seen as it dropped, with what it took to get it. */
	public static final class Find
	{
		private int item;
		private long at;
		/** The deepest level of the run it came from, or 0 when that run was not seen. */
		private int depth;
		/** Log of the chance of no copy from every banked level before that run. */
		private double prior;
		/** The chance of a copy from that run. */
		private double chance;

		Find(int item, long at, int depth, double prior, double chance)
		{
			this.item = item;
			this.at = at;
			this.depth = depth;
			this.prior = prior;
			this.chance = chance;
		}

		public int getItem()
		{
			return item;
		}

		public long getAt()
		{
			return at;
		}

		public int getDepth()
		{
			return depth;
		}

		public double getChance()
		{
			return chance;
		}

		/** Banked levels' chance of having it before this run: the share of players who would have had it sooner. */
		public double sooner()
		{
			return depth <= 0 ? -1 : -Math.expm1(prior);
		}

		/**
		 * The share of players who would have needed more runs, plus half of those who would have needed exactly as
		 * many: the oracle's luck at the kill, run by run.
		 */
		public double luckierThan()
		{
			if (depth <= 0)
			{
				return -1;
			}
			double before = Math.exp(prior);
			return before * (1 - chance) + before * chance / 2;
		}
	}

	/** Missing lists and arrays from damaged or older saves are put back. */
	public DelveLog repair()
	{
		banked = fit(banked);
		lost = fit(lost);
		deaths = fit(deaths);
		timed = fit(timed);
		if (timeMs == null || timeMs.length != ROWS)
		{
			long[] fixed = new long[ROWS];
			if (timeMs != null)
			{
				System.arraycopy(timeMs, 0, fixed, 0, Math.min(ROWS, timeMs.length));
			}
			timeMs = fixed;
		}
		if (recent == null)
		{
			recent = new ArrayList<>();
		}
		recent.removeIf(r -> r == null);
		if (finds == null)
		{
			finds = new ArrayList<>();
		}
		finds.removeIf(f -> f == null);
		return this;
	}

	private static int[] fit(int[] values)
	{
		if (values != null && values.length == ROWS)
		{
			return values;
		}
		int[] fixed = new int[ROWS];
		if (values != null)
		{
			System.arraycopy(values, 0, fixed, 0, Math.min(ROWS, values.length));
		}
		return fixed;
	}

	public static SavedJson.Read<DelveLog> load(com.google.gson.Gson gson, String json)
	{
		SavedJson.Read<DelveLog> read = SavedJson.read(gson, json, DelveLog.class);
		DelveLog value = read.getValue();
		return new SavedJson.Read<>(value == null ? null : value.repair(), read.isDamaged());
	}

	/**
	 * A delve level was cleared. The same level said twice (the chat line and the end-of-level window) counts once.
	 * Reaching a level with no run under way means the run began before it was seen: the delve level only climbs
	 * from 1 within one run, so every level below it was cleared in it too.
	 *
	 * @return true when this changed the log
	 */
	public boolean levelCleared(int level, long now)
	{
		if (level < 1)
		{
			return false;
		}
		if (open != null && level == open.deepest)
		{
			return false;
		}
		if (open == null && !recent.isEmpty() && recent.get(0).deepest == level && now - recent.get(0).last < SAME_DEATH_MS)
		{
			// The level's chat line arriving after its loot was claimed: the same level, not a new run.
			return false;
		}
		if (open != null && level != open.deepest + 1)
		{
			// Back to 1, or a level that cannot follow: the last run was left without being seen to end.
			finish(LEFT, open.last);
		}
		if (since == 0)
		{
			since = now;
		}
		if (open == null)
		{
			long gap = lastEnd > 0 ? now - lastEnd : -1;
			open = new Run(now, level);
			if (level == 1)
			{
				time(1, gap);
			}
		}
		else
		{
			long gap = now - open.last;
			open.deepest = level;
			open.last = now;
			time(level, gap);
		}
		deepest = Math.max(deepest, level);
		return true;
	}

	private void time(int level, long gap)
	{
		if (gap > 0 && gap <= MAX_LEVEL_MS)
		{
			int row = Delve.row(level);
			timeMs[row] += gap;
			timed[row]++;
		}
	}

	/** The loot was claimed: the run under way ends with its loot kept. A claim from the lobby chest changes nothing. */
	public boolean claimed(long now)
	{
		if (open == null)
		{
			return false;
		}
		open.last = Math.max(open.last, now);
		finish(CLAIMED, now);
		return true;
	}

	/** The Doom killed the player: the run's loot is lost, on the level after the deepest one cleared. */
	public boolean died(long now)
	{
		if (open == null)
		{
			Run last = recent.isEmpty() ? null : recent.get(0);
			if (last != null && DIED.equals(last.outcome) && now - last.last < SAME_DEATH_MS)
			{
				return false;
			}
			// Killed on delve 1, before any level was cleared.
			if (since == 0)
			{
				since = now;
			}
			open = new Run(now, 0);
		}
		open.last = Math.max(open.last, now);
		finish(DIED, now);
		return true;
	}

	/** A run left open for hours was left; the delve level is back to 1 by now. */
	public boolean settle(long now)
	{
		if (open != null && now - open.last > STALE_MS)
		{
			finish(LEFT, open.last);
			return true;
		}
		return false;
	}

	private void finish(String outcome, long at)
	{
		Run run = open;
		open = null;
		if (run == null)
		{
			return;
		}
		run.outcome = outcome;
		boolean keep = run.banked();
		for (int level = 1; level <= run.deepest; level++)
		{
			int row = Delve.row(level);
			if (keep)
			{
				banked[row]++;
			}
			else
			{
				lost[row]++;
			}
		}
		if (keep)
		{
			bankedRuns++;
		}
		else
		{
			diedRuns++;
			deaths[Delve.row(run.deepest + 1)]++;
		}
		lastEnd = at;
		recent.add(0, run);
		while (recent.size() > MAX_RECENT)
		{
			recent.remove(recent.size() - 1);
		}
	}

	/**
	 * A unique was added to the collection log: it is put down to the run under way, or the run that just ended, with
	 * the chance that run gave it and the chance every banked level before it did.
	 *
	 * @return the find, or null when the item is not one of the Doom's uniques
	 */
	public Find found(int itemId, long now)
	{
		int unique = Delve.index(itemId);
		if (unique < 0)
		{
			return null;
		}
		Run run = open;
		boolean counted = false;
		if (run == null && !recent.isEmpty() && now - recent.get(0).last <= FIND_WINDOW_MS && recent.get(0).banked())
		{
			run = recent.get(0);
			counted = true;
		}
		Find find;
		if (run == null || run.deepest < Delve.firstLevel(unique))
		{
			find = new Find(itemId, now, 0, 0, 0);
		}
		else
		{
			double prior = Delve.missLog(unique, banked) - (counted ? Delve.runMissLog(unique, run.deepest) : 0);
			find = new Find(itemId, now, run.deepest, Math.min(0, prior), Delve.runChance(unique, run.deepest));
		}
		finds.add(0, find);
		while (finds.size() > MAX_FINDS)
		{
			finds.remove(finds.size() - 1);
		}
		return find;
	}

	public long getSince()
	{
		return since;
	}

	/** Levels cleared and banked, by row (1 to 9). */
	public int[] getBanked()
	{
		return banked.clone();
	}

	public int[] getLost()
	{
		return lost.clone();
	}

	public int[] getDeaths()
	{
		return deaths.clone();
	}

	public long[] getTimeMs()
	{
		return timeMs.clone();
	}

	public int[] getTimed()
	{
		return timed.clone();
	}

	public int getBankedRuns()
	{
		return bankedRuns;
	}

	public int getDiedRuns()
	{
		return diedRuns;
	}

	public int getDeepest()
	{
		return deepest;
	}

	public Run getOpen()
	{
		return open;
	}

	public List<Run> getRecent()
	{
		return new ArrayList<>(recent);
	}

	public List<Find> getFinds()
	{
		return new ArrayList<>(finds);
	}

	public boolean isEmpty()
	{
		return since == 0;
	}
}
