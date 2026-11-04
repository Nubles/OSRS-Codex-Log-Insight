package com.codex.loginsight.core;

import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * The luck oracle: for every rare slot you own or still chase, where your own kill count puts you among every player
 * at the same count, and for every unfinished page the time it should take at your own pace.
 *
 * <p>A drop at a fixed chance per kill makes the kill it first comes on a geometric wait, so "luckier than" for a
 * slot found at kill k is the share of players who would need more kills than k (half of those who need exactly k
 * count, as ties do everywhere in Log Insight). Several copies of a slot at a known count are a binomial count. A
 * slot still missing is read the way players say it: how many times its rate you have done ("2.3x the rate") and
 * the share of players who would have it by now. Kept free of game types so it can be tested on its own.
 */
@Getter
public final class Oracle
{
	/** How a slot's luck was read. */
	public enum Basis
	{
		/** The kill count it first dropped at was seen. */
		AT_KILL,
		/** Read from how many copies the log counts at the counts they were read at. */
		COPIES
	}

	/** Owned slots with a reading, luckiest first. */
	private final List<Owned> owned;
	/** Missing slots with a reading, driest first. */
	private final List<Chase> chasing;
	/** Unfinished pages that can be timed, soonest done first. */
	private final List<Forecast> pages;

	public Oracle(List<Owned> owned, List<Chase> chasing, List<Forecast> pages)
	{
		this.owned = Collections.unmodifiableList(owned);
		this.chasing = Collections.unmodifiableList(chasing);
		this.pages = Collections.unmodifiableList(pages);
	}

	/** The luckiest owned slot, or null. */
	public Owned luckiest()
	{
		return owned.isEmpty() ? null : owned.get(0);
	}

	/** The driest slot still missing, or null. */
	public Chase driest()
	{
		return chasing.isEmpty() ? null : chasing.get(0);
	}

	/** True when there is nothing to say yet. */
	public boolean isEmpty()
	{
		return owned.isEmpty() && chasing.isEmpty() && pages.isEmpty();
	}

	/** How many owned readings came out on the lucky side of the middle. */
	public int luckyCount()
	{
		int n = 0;
		for (Owned o : owned)
		{
			if (o.luckier > 0.5)
			{
				n++;
			}
		}
		return n;
	}

	// ---- The math ----

	/**
	 * The share of players a find at this kill is luckier than: those who would need more kills, plus half of those
	 * who would need exactly as many. With a guarantee, nobody waits past it.
	 *
	 * @param chance       the chance per kill
	 * @param kill         the kill it came on, from 1
	 * @param guaranteedBy the kill it is certain by, or 0
	 */
	public static double luckAtKill(double chance, int kill, int guaranteedBy)
	{
		if (!(chance > 0) || kill < 1)
		{
			return -1;
		}
		if (chance >= 1)
		{
			return 0.5;
		}
		double logMiss = Math.log1p(-chance);
		double later;
		double exactly;
		if (guaranteedBy > 0 && kill >= guaranteedBy)
		{
			later = 0;
			// Everyone still waiting by the guaranteed kill gets it there, and nobody after it.
			exactly = kill == guaranteedBy ? Math.exp((guaranteedBy - 1) * logMiss) : 0;
		}
		else
		{
			later = Math.exp(kill * logMiss);
			exactly = chance * Math.exp((kill - 1) * logMiss);
		}
		return clamp(later + exactly / 2);
	}

	/**
	 * Where a count of copies sits among players who rolled as often: the share who would have fewer, plus half of
	 * those with as many. An exact binomial worked in logs, so ten thousand rolls stay accurate.
	 *
	 * @param rolls  how many rolls were made
	 * @param chance the chance per roll
	 * @param got    how many copies came
	 */
	public static double copiesPercentile(long rolls, double chance, int got)
	{
		if (rolls <= 0 || !(chance > 0))
		{
			return got > 0 ? 1 : 0.5;
		}
		if (chance >= 1)
		{
			return got >= rolls ? 0.5 : 0;
		}
		got = (int) Math.max(0, Math.min(got, rolls));
		double logP = Math.log(chance);
		double logQ = Math.log1p(-chance);
		double below = 0;
		double at = 0;
		// log C(n, k) built up one k at a time.
		double logChoose = 0;
		for (int k = 0; k <= got; k++)
		{
			if (k > 0)
			{
				logChoose += Math.log((double) (rolls - k + 1)) - Math.log(k);
			}
			double p = Math.exp(logChoose + k * logP + (rolls - k) * logQ);
			if (k < got)
			{
				below += p;
			}
			else
			{
				at = p;
			}
		}
		return clamp(below + at / 2);
	}

	/** Kills from nothing by which this share of players would have had a drop of this chance; infinity when never. */
	public static double killsFor(double chance, double share)
	{
		if (!(chance > 0) || share <= 0)
		{
			return chance > 0 ? 0 : Double.POSITIVE_INFINITY;
		}
		if (chance >= 1)
		{
			return 1;
		}
		if (share >= 1)
		{
			return Double.POSITIVE_INFINITY;
		}
		return Math.ceil(Math.log1p(-share) / Math.log1p(-chance));
	}

	private static double clamp(double v)
	{
		return Math.max(0, Math.min(1, v));
	}

	// ---- The readings ----

	/** One owned slot and how lucky it was. */
	@Getter
	public static final class Owned
	{
		private final int itemId;
		private final String name;
		private final String page;
		private final String label;
		/** Chance per count of that counter. */
		private final double chance;
		/** The count it dropped at ({@link Basis#AT_KILL}) or the count its copies were read at. */
		private final int count;
		/** Copies the log counts, or 1 when only the find was seen. */
		private final int copies;
		/** Share of players this is luckier than, 0 to 1. */
		private final double luckier;
		private final Basis basis;
		private final boolean pet;

		public Owned(int itemId, String name, String page, String label, double chance, int count, int copies,
			double luckier, Basis basis, boolean pet)
		{
			this.itemId = itemId;
			this.name = name;
			this.page = page;
			this.label = label;
			this.chance = chance;
			this.count = count;
			this.copies = copies;
			this.luckier = luckier;
			this.basis = basis;
			this.pet = pet;
		}

		/** How many times the rate the drop came at: 0.4 means at under half the average wait. */
		public double getTimesRate()
		{
			return chance * count;
		}
	}

	/** One slot still missing and how dry it is. */
	@Getter
	public static final class Chase
	{
		private final int itemId;
		private final String name;
		private final String page;
		private final String label;
		private final double chance;
		private final int count;
		/** Share of players still without it at these counts; 0 when a guarantee has passed. */
		private final double dry;
		/** How many of it an average player would have had by now: "2.3x the rate". */
		private final double timesRate;
		/** Average hours to it from here at your pace, or infinity; drops have no memory, so past kills do not shorten it. */
		private final double hours;
		/** Where it comes soonest, or null. */
		private final String source;
		/** Kills or completions an hour at that source, or 0. */
		private final double perHour;
		/** True when that pace is your own, timed from your kill counts. */
		private final boolean ownPace;
		private final boolean pet;
		private final boolean wanted;

		public Chase(int itemId, String name, String page, String label, double chance, int count, double dry,
			double timesRate, double hours, String source, double perHour, boolean ownPace, boolean pet, boolean wanted)
		{
			this.itemId = itemId;
			this.name = name;
			this.page = page;
			this.label = label;
			this.chance = chance;
			this.count = count;
			this.dry = dry;
			this.timesRate = timesRate;
			this.hours = hours;
			this.source = source;
			this.perHour = perHour;
			this.ownPace = ownPace;
			this.pet = pet;
			this.wanted = wanted;
		}

		/** Share of players who would have it by now. */
		public double haveIt()
		{
			return 1 - dry;
		}
	}

	/** One unfinished page and when it should be done. */
	@Getter
	public static final class Forecast
	{
		private final String tab;
		private final String page;
		private final int owned;
		private final int total;
		/** Average hours to finish the slots with rates. */
		private final double hours;
		/** Hours by which half, and nine in ten, players would be done; infinity when there is no curve. */
		private final double half;
		private final double nineInTen;
		/** The page's main activity, its pace and whether that pace is your own. */
		private final String source;
		private final double perHour;
		private final boolean ownPace;
		/** Missing slots without rates, not in the hours. */
		private final int unrated;
		/** Luck so far on the page, or -1. */
		private final double luck;

		public Forecast(String tab, String page, int owned, int total, double hours, double half, double nineInTen,
			String source, double perHour, boolean ownPace, int unrated, double luck)
		{
			this.tab = tab;
			this.page = page;
			this.owned = owned;
			this.total = total;
			this.hours = hours;
			this.half = half;
			this.nineInTen = nineInTen;
			this.source = source;
			this.perHour = perHour;
			this.ownPace = ownPace;
			this.unrated = unrated;
			this.luck = luck;
		}
	}
}
