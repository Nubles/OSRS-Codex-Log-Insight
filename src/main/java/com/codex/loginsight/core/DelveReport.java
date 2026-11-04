package com.codex.loginsight.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.Getter;

/**
 * What the delve log says: each unique's chance from the levels you actually banked, how dry or lucky that makes you,
 * and, depth by depth, how soon the next new slot comes if you cash out there, at your own pace and with your own
 * deaths. Kept free of game types.
 */
@Getter
public final class DelveReport
{
	/**
	 * Seconds a delve level takes until your own are timed: the OSRS Wiki's money making guide assumes "6 Delve 1-8
	 * completions per hour", which is 600 seconds a run over 8 levels, lobby and banking included.
	 */
	public static final double TYPICAL_LEVEL_SECONDS = 3600.0 / 6 / 8;
	/** Levels timed before your own pace replaces the typical one. */
	static final int OWN_PACE_LEVELS = 5;
	/** How many levels' worth of your overall survival a level you have barely tried leans on. */
	static final double SURVIVAL_PRIOR = 4;
	/** Banked runs the usual depth is read from. */
	static final int USUAL_RUNS = 25;
	/** Depths shown past the deepest you have been. */
	static final int MAX_DEPTH_SHOWN = 14;

	/** One unique: owned or not, its chance by now, and for a find, how lucky it was. */
	@Getter
	public static final class Unique
	{
		private final int itemId;
		private final String name;
		private final boolean owned;
		/** Owned, but not seen to drop since the delve log began: no luck can be read from it. */
		private final boolean ownedBefore;
		/** The share of players who would have it from the levels you banked. */
		private final double byNow;
		/** How many copies an average player would have had. */
		private final double expected;
		/** The first find of it the delve log saw, or null. */
		private final DelveLog.Find find;
		private final int firstLevel;
		/** "1 in N" at delve 9 and deeper, its best rate. */
		private final int bestOneIn;

		Unique(int itemId, String name, boolean owned, boolean ownedBefore, double byNow, double expected,
			DelveLog.Find find, int firstLevel, int bestOneIn)
		{
			this.itemId = itemId;
			this.name = name;
			this.owned = owned;
			this.ownedBefore = ownedBefore;
			this.byNow = byNow;
			this.expected = expected;
			this.find = find;
			this.firstLevel = firstLevel;
			this.bestOneIn = bestOneIn;
		}
	}

	/** Cashing out after clearing a depth, every run. */
	@Getter
	public static final class Depth
	{
		private final int depth;
		/** The chance a run that gets there gives a slot you are after. */
		private final double perRun;
		/** The chance of getting there alive, from your own deaths. */
		private final double reach;
		/** Average hours a run takes, counting the runs that end in death. */
		private final double hoursPerRun;
		/** Average hours until the next slot you are after, run after run. */
		private final double hoursToNext;
		/** You have cleared this depth at least once. */
		private final boolean tried;

		Depth(int depth, double perRun, double reach, double hoursPerRun, boolean tried)
		{
			this.depth = depth;
			this.perRun = perRun;
			this.reach = reach;
			this.hoursPerRun = hoursPerRun;
			this.tried = tried;
			double gain = perRun * reach;
			this.hoursToNext = gain > 0 ? hoursPerRun / gain : Double.POSITIVE_INFINITY;
		}

		/** The chance a run started at delve 1 ends banked with a slot you are after. */
		public double perStart()
		{
			return perRun * reach;
		}
	}

	private final boolean empty;
	private final long since;
	private final int bankedRuns;
	private final int diedRuns;
	private final int deepest;
	/** The level the run under way has cleared, or 0. */
	private final int openDepth;
	/** The depth you most often cash out at lately, or 0. */
	private final int usual;
	private final int bankedLevels;
	private final List<Unique> uniques;
	/** Your luck on the uniques the delve log has seen you chase from the start, or null. */
	private final Luck luck;
	/** The depths compared, shallowest first. */
	private final List<Depth> depths;
	/** The depth with the shortest wait for the next slot, or 0. */
	private final int best;
	/** True when the depths are compared on missing slots; false when every unique is owned and any copy counts. */
	private final boolean forNewSlots;
	/** True when the level times are your own. */
	private final boolean ownPace;
	/** Your survival over every level you have fought, or -1 before any. */
	private final double survival;
	/** Deaths so far, and the levels you have cleared, for the line under the comparison. */
	private final int deaths;
	private final int cleared;
	/** Recent runs, newest first: the depth each reached and whether its loot was kept. */
	private final List<DelveLog.Run> recent;
	/** The whole collection log has been read, so a unique not owned is really missing. */
	private final boolean ownedKnown;

	private DelveReport(boolean empty, long since, int bankedRuns, int diedRuns, int deepest, int openDepth, int usual,
		int bankedLevels, List<Unique> uniques, Luck luck, List<Depth> depths, int best, boolean forNewSlots,
		boolean ownPace, double survival, int deaths, int cleared, List<DelveLog.Run> recent, boolean ownedKnown)
	{
		this.ownedKnown = ownedKnown;
		this.empty = empty;
		this.since = since;
		this.bankedRuns = bankedRuns;
		this.diedRuns = diedRuns;
		this.deepest = deepest;
		this.openDepth = openDepth;
		this.usual = usual;
		this.bankedLevels = bankedLevels;
		this.uniques = uniques;
		this.luck = luck;
		this.depths = depths;
		this.best = best;
		this.forNewSlots = forNewSlots;
		this.ownPace = ownPace;
		this.survival = survival;
		this.deaths = deaths;
		this.cleared = cleared;
		this.recent = recent;
	}

	/** The depth row for a depth, or null. */
	public Depth depth(int depth)
	{
		for (Depth d : depths)
		{
			if (d.depth == depth)
			{
				return d;
			}
		}
		return null;
	}

	/**
	 * @param log      this account's delve log
	 * @param owned    the slots the collection log says are owned
	 * @param ownedKnown true once the whole log has been read, so a slot not in {@code owned} is really missing
	 */
	public static DelveReport of(DelveLog log, Set<Integer> owned, boolean ownedKnown)
	{
		int[] banked = log.getBanked();
		int[] lost = log.getLost();
		int[] deathRows = log.getDeaths();
		List<DelveLog.Find> finds = log.getFinds();

		int bankedLevels = 0;
		for (int b : banked)
		{
			bankedLevels += b;
		}

		List<Unique> uniques = new ArrayList<>();
		List<Double> chances = new ArrayList<>();
		int foundInLog = 0;
		boolean anyMissing = false;
		for (int u = 0; u < Delve.UNIQUES.length; u++)
		{
			int id = Delve.UNIQUES[u];
			DelveLog.Find first = null;
			for (DelveLog.Find f : finds)
			{
				if (f.getItem() == id)
				{
					first = f;
				}
			}
			boolean has = owned.contains(id) || first != null;
			boolean before = has && first == null;
			double byNow = -Math.expm1(Delve.missLog(u, banked));
			uniques.add(new Unique(id, Delve.NAMES[u], has, before, byNow, Delve.expected(u, banked), first,
				Delve.firstLevel(u), Delve.oneIn(u, Delve.DEEPEST_ROW)));
			anyMissing |= !has;
			if (!before)
			{
				chances.add(byNow);
				foundInLog += first != null ? 1 : 0;
			}
		}
		Luck luck = null;
		if (bankedLevels > 0 && ownedKnown && !chances.isEmpty())
		{
			double[] c = new double[chances.size()];
			for (int i = 0; i < c.length; i++)
			{
				c[i] = chances.get(i);
			}
			luck = Luck.of(c, foundInLog);
		}

		// Your pace, level by level; the first level's time includes the lobby between runs.
		long[] timeMs = log.getTimeMs();
		int[] timed = log.getTimed();
		int timedLevels = 0;
		long pooledMs = 0;
		int pooledN = 0;
		for (int r = 1; r < timed.length; r++)
		{
			timedLevels += timed[r];
			if (r >= 2)
			{
				pooledMs += timeMs[r];
				pooledN += timed[r];
			}
		}
		boolean ownPace = timedLevels >= OWN_PACE_LEVELS;
		double fallback = ownPace && pooledN > 0 ? pooledMs / 1000.0 / pooledN : TYPICAL_LEVEL_SECONDS;
		double[] seconds = new double[DelveLog.ROWS];
		for (int r = 1; r < seconds.length; r++)
		{
			seconds[r] = ownPace && timed[r] >= 2 ? timeMs[r] / 1000.0 / timed[r] : fallback;
		}

		// Your survival, level by level, leaning on your overall rate where a level has few fights.
		int allCleared = 0;
		int allDeaths = 0;
		int[] cleared = new int[DelveLog.ROWS];
		for (int r = 1; r < cleared.length; r++)
		{
			cleared[r] = banked[r] + lost[r];
			allCleared += cleared[r];
			allDeaths += deathRows[r];
		}
		double pool = allCleared + allDeaths == 0 ? 1 : allCleared / (double) (allCleared + allDeaths);
		double[] survive = new double[DelveLog.ROWS];
		for (int r = 1; r < survive.length; r++)
		{
			survive[r] = (cleared[r] + SURVIVAL_PRIOR * pool) / (cleared[r] + deathRows[r] + SURVIVAL_PRIOR);
		}

		// Which uniques the comparison is for: the missing ones, or any copy once all are owned.
		boolean forNew = anyMissing;
		List<Integer> target = new ArrayList<>();
		for (int u = 0; u < uniques.size(); u++)
		{
			if (!forNew || !uniques.get(u).owned)
			{
				target.add(u);
			}
		}

		int deepest = log.getDeepest();
		int maxDepth = Math.max(Delve.DEEPEST_ROW, Math.min(deepest + 1, MAX_DEPTH_SHOWN));
		List<Depth> depths = new ArrayList<>();
		int best = 0;
		double bestHours = Double.POSITIVE_INFINITY;
		for (int d = 2; d <= maxDepth; d++)
		{
			double reach = 1;
			double runSeconds = 0;
			for (int level = 1; level <= d; level++)
			{
				int r = Delve.row(level);
				// Every level fought costs its time, whether it is cleared or ends the run.
				runSeconds += reach * seconds[r];
				reach *= survive[r];
			}
			double miss = 0;
			for (int u : target)
			{
				miss += Delve.runMissLog(u, d);
			}
			Depth depth = new Depth(d, -Math.expm1(miss), reach, runSeconds / 3600.0, d <= deepest);
			depths.add(depth);
			if (depth.hoursToNext < bestHours - 1e-9)
			{
				bestHours = depth.hoursToNext;
				best = d;
			}
		}

		List<DelveLog.Run> recent = log.getRecent();
		List<Integer> recentDepths = new ArrayList<>();
		for (DelveLog.Run run : recent)
		{
			if (run.banked() && recentDepths.size() < USUAL_RUNS)
			{
				recentDepths.add(run.getDeepest());
			}
		}
		int usual = 0;
		if (!recentDepths.isEmpty())
		{
			Collections.sort(recentDepths);
			usual = recentDepths.get((recentDepths.size() - 1) / 2);
		}
		DelveLog.Run open = log.getOpen();
		return new DelveReport(log.isEmpty(), log.getSince(), log.getBankedRuns(), log.getDiedRuns(), deepest,
			open == null ? 0 : open.getDeepest(), usual, bankedLevels, Collections.unmodifiableList(uniques), luck,
			Collections.unmodifiableList(depths), best, forNew, ownPace, allCleared + allDeaths == 0 ? -1 : pool,
			allDeaths, allCleared, Collections.unmodifiableList(recent), ownedKnown);
	}

	/** A report with nothing logged, for the rates alone. */
	public static DelveReport rates(Set<Integer> owned)
	{
		return of(new DelveLog().repair(), owned == null ? Collections.emptySet() : owned, false);
	}

	/** Banked levels at each row, for tests. */
	static int[] rows(int... deepestOfRuns)
	{
		int[] rows = new int[DelveLog.ROWS];
		for (int deepest : deepestOfRuns)
		{
			for (int level = 1; level <= deepest; level++)
			{
				rows[Delve.row(level)]++;
			}
		}
		return Arrays.copyOf(rows, rows.length);
	}
}
