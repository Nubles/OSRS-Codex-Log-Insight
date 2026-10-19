package com.codex.loginsight.core;

import com.codex.loginsight.data.ItemOdds;
import com.codex.loginsight.data.LogData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * Notices the kill that takes you past a dry milestone on a slot you are missing: the count by which nine in ten
 * players would have it, then ninety-nine in a hundred. Said once, as it happens, so a long grind has its
 * landmarks; the panel already says how dry you are at any time. Half of players is only the average, so it is
 * left to the tooltips rather than said in chat.
 */
public final class DryWatch
{
	/** Shares of players who would own the slot by now, in order. */
	static final double[] MILESTONES = {0.9, 0.99};

	/**
	 * Ticks a milestone waits before it is checked. On the kill that drops a new slot the game may send the kill
	 * count before the "New item added to your collection log" line, or the pop-up a moment later; checked at once,
	 * the chat would call you dry on the very slot you just got.
	 */
	public static final int SETTLE_TICKS = 3;

	private DryWatch()
	{
	}

	/** A kill count change waiting for the new-slot messages of the same kill before its milestones are checked. */
	@Getter
	public static final class Pending
	{
		private final String label;
		private final int before;
		private int after;
		private int dueTick;

		Pending(String label, int before, int after, int dueTick)
		{
			this.label = label;
			this.before = before;
			this.after = after;
			this.dueTick = dueTick;
		}
	}

	/**
	 * Holds kill count changes until the messages of the same kill have arrived. Two changes of one counter before
	 * the first is due become one (the earlier "before", the later "after"), so a fast kill does not say a
	 * milestone twice or skip one.
	 */
	public static final class Queue
	{
		private final List<Pending> pending = new ArrayList<>();

		/** Notes a counter moving from {@code before} to {@code after} at game tick {@code tick}. */
		public void add(String label, int before, int after, int tick)
		{
			for (Pending p : pending)
			{
				if (p.label.equals(label))
				{
					p.after = after;
					p.dueTick = tick + SETTLE_TICKS;
					return;
				}
			}
			pending.add(new Pending(label, before, after, tick + SETTLE_TICKS));
		}

		/** The changes whose messages have settled by game tick {@code tick}, taken out of the queue. */
		public List<Pending> due(int tick)
		{
			List<Pending> out = new ArrayList<>();
			pending.removeIf(p ->
			{
				// A tick count that went backwards is a new login: whatever waited is settled too.
				if (tick >= p.dueTick || tick < p.dueTick - SETTLE_TICKS)
				{
					out.add(p);
					return true;
				}
				return false;
			});
			return out;
		}

		public void clear()
		{
			pending.clear();
		}

		public boolean isEmpty()
		{
			return pending.isEmpty();
		}
	}

	/** One slot passing a milestone. */
	@Getter
	public static final class Milestone
	{
		private final int itemId;
		/** The share of players who would own it by now that was just passed: 0.9 or 0.99. */
		private final double share;
		/** The chance per count of the main counter, for "1 in N". */
		private final double chance;

		Milestone(int itemId, double share, double chance)
		{
			this.itemId = itemId;
			this.share = share;
			this.chance = chance;
		}

		/** "9 in 10 players" or "99 in 100 players". */
		public String players()
		{
			return share >= 0.99 ? "99 in 100 players" : "9 in 10 players";
		}
	}

	/**
	 * The milestones passed when a counter moves from {@code before} to {@code after}, for slots still missing,
	 * the most notable first (the furthest milestone, then the rarest slot). Nothing when the counter went back
	 * or jumped from nothing, as when a count is first read.
	 */
	public static List<Milestone> passed(LogData data, Set<Integer> owned, Map<String, Integer> counters, String label,
		int before, int after)
	{
		List<Milestone> out = new ArrayList<>();
		if (label == null || before <= 0 || after <= before)
		{
			return out;
		}
		Map<String, Integer> then = new HashMap<>(counters);
		then.put(label, before);
		Map<String, Integer> now = new HashMap<>(counters);
		now.put(label, after);
		for (Map.Entry<Integer, ItemOdds> e : data.allOdds().entrySet())
		{
			ItemOdds odds = e.getValue();
			if (owned.contains(e.getKey()) || !label.equals(odds.mainLabel()) || odds.getGuaranteedBy() > 0)
			{
				continue;
			}
			double shareThen = 1 - odds.dryChance(then);
			double shareNow = 1 - odds.dryChance(now);
			double passed = 0;
			for (double m : MILESTONES)
			{
				if (shareThen < m && shareNow >= m)
				{
					passed = m;
				}
			}
			if (passed > 0)
			{
				out.add(new Milestone(e.getKey(), passed, odds.mainChance()));
			}
		}
		out.sort((a, b) -> a.share != b.share ? Double.compare(b.share, a.share) : Double.compare(a.chance, b.chance));
		return out;
	}

	/**
	 * True when Log Insight knows which slots you own well enough to call one missing: the whole log has been read
	 * once, and the game's own count is no higher than the slots it can name. Before that, a slot owned for years
	 * would read as one you are dry on.
	 */
	public static boolean ownedKnown(long fullReadAt, int officialOwned, int knownOwned)
	{
		return fullReadAt > 0 && officialOwned <= knownOwned;
	}

	/**
	 * The count of the main counter by which this share of players would have a slot of this chance per count,
	 * or -1 when the chance is unknown.
	 */
	public static int countFor(double chance, double share)
	{
		if (!(chance > 0) || chance >= 1)
		{
			return chance >= 1 ? 1 : -1;
		}
		return (int) Math.ceil(Math.log1p(-share) / Math.log1p(-chance));
	}
}
