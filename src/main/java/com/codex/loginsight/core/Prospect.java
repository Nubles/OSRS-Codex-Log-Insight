package com.codex.loginsight.core;

import com.codex.loginsight.data.Activity;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * What a stretch of time at one activity is likely to give: the missing slots it can drop,
 * set out so the chance of each by any number of attempts can be read off. Rolled slots
 * wait side by side, pieces that drop in order wait one after another, and slots earned
 * outright (points, permits) are paid for one by one, cheapest first.
 */
@Getter
public final class Prospect
{
	private final String name;
	private final Activity.Category category;
	/** Completions per hour, your own pace where it is known. */
	private final double perHour;
	private final double setupHours;
	private final boolean ownPace;
	/** What the account still needs to do this, or null. */
	private final String lockedBy;
	/** The slot most likely to come first, or -1. */
	private final int nextItemId;
	private final String nextItemName;
	private final int remaining;
	/** For each run of rolled slots, its average attempts in total. */
	private final double[] means;
	/** How many slots each run holds; more than one only drop in order. */
	private final int[] stages;
	/** Attempts each slot earned outright costs, cheapest first. */
	private final double[] exact;
	/** The slots this covers, for showing them. */
	private final List<Integer> missing;

	public Prospect(String name, Activity.Category category, double perHour, double setupHours, boolean ownPace,
		String lockedBy, int nextItemId, String nextItemName, double[] means, int[] stages, double[] exact,
		List<Integer> missing)
	{
		this.name = name;
		this.category = category;
		this.perHour = perHour;
		this.setupHours = setupHours;
		this.ownPace = ownPace;
		this.lockedBy = lockedBy;
		this.nextItemId = nextItemId;
		this.nextItemName = nextItemName;
		this.means = means.clone();
		this.stages = stages.clone();
		this.exact = exact.clone();
		Arrays.sort(this.exact);
		this.missing = Collections.unmodifiableList(missing);
		int slots = this.exact.length;
		for (int k : this.stages)
		{
			slots += Math.max(1, k);
		}
		this.remaining = slots;
	}

	/** Attempts that fit in this many hours once the one-off setup is done. */
	public double attemptsIn(double hours)
	{
		return perHour <= 0 ? 0 : Math.max(0, hours - setupHours) * perHour;
	}

	/** Hours these attempts take, setup included. */
	public double hoursFor(double attempts)
	{
		if (perHour <= 0 || Double.isInfinite(attempts))
		{
			return Double.POSITIVE_INFINITY;
		}
		return setupHours + attempts / perHour;
	}

	/** New slots an average player would have after this many hours here. */
	public double expectedSlots(double hours)
	{
		double n = attemptsIn(hours);
		if (n <= 0)
		{
			return 0;
		}
		double slots = 0;
		for (int i = 0; i < means.length; i++)
		{
			int k = Math.max(1, stages[i]);
			if (k == 1)
			{
				slots += 1 - miss(means[i], n);
			}
			else
			{
				double x = n * k / means[i];
				for (int j = 1; j <= k; j++)
				{
					slots += Odds.runDone(j, j, x);
				}
			}
		}
		double paid = 0;
		for (double cost : exact)
		{
			paid += cost;
			if (paid > n)
			{
				break;
			}
			slots++;
		}
		return slots;
	}

	/** The chance of at least one new slot in this many hours. */
	public double chanceOfAny(double hours)
	{
		double n = attemptsIn(hours);
		if (n <= 0 || remaining == 0)
		{
			return 0;
		}
		if (exact.length > 0 && exact[0] <= n)
		{
			return 1;
		}
		double none = 1;
		for (int i = 0; i < means.length; i++)
		{
			int k = Math.max(1, stages[i]);
			// Only a run's first piece can come first; with k pieces sharing the run's mean, it takes a k-th of it.
			none *= k == 1 ? miss(means[i], n) : Math.exp(-n * k / means[i]);
		}
		return 1 - none;
	}

	/** The chance every slot here is owned after this many hours. */
	public double chanceOfAll(double hours)
	{
		return chanceOfAllBy(attemptsIn(hours));
	}

	double chanceOfAllBy(double n)
	{
		if (remaining == 0)
		{
			return 1;
		}
		double fixed = 0;
		for (double cost : exact)
		{
			fixed += cost;
		}
		if (n <= 0 || n < fixed)
		{
			return 0;
		}
		double all = 1;
		for (int i = 0; i < means.length; i++)
		{
			int k = Math.max(1, stages[i]);
			all *= k == 1 ? 1 - miss(means[i], n) : Odds.runDone(means[i], k, n);
		}
		return all;
	}

	/**
	 * Hours until the chance of owning every slot here reaches q: 0.5 for the hours by which
	 * half of all players would be done, 0.9 for nine in ten.
	 */
	public double hoursToAll(double q)
	{
		if (remaining == 0)
		{
			return 0;
		}
		if (perHour <= 0)
		{
			return Double.POSITIVE_INFINITY;
		}
		double lo = 0;
		double hi = 1;
		while (chanceOfAllBy(hi) < q)
		{
			lo = hi;
			hi *= 2;
			if (hi > 1e12)
			{
				return Double.POSITIVE_INFINITY;
			}
		}
		for (int i = 0; i < 60; i++)
		{
			double mid = (lo + hi) / 2;
			if (chanceOfAllBy(mid) < q)
			{
				lo = mid;
			}
			else
			{
				hi = mid;
			}
		}
		return hoursFor(hi);
	}

	/** The chance of still not having a slot of this average wait after n attempts. */
	private static double miss(double mean, double n)
	{
		double p = 1 / Math.max(1, mean);
		if (p >= 1)
		{
			return n >= 1 ? 0 : 1 - n;
		}
		return Math.exp(n * Math.log1p(-p));
	}
}
