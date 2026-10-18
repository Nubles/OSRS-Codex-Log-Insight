package com.codex.loginsight.core;

/**
 * The probability behind every number Log Insight shows. Kept free of game types so
 * it can be tested on its own.
 */
public final class Odds
{
	private static final int STEPS = 1600;

	private Odds()
	{
	}

	/**
	 * Where an account sits among everyone at the same counts, from 0 (driest) to 1
	 * (luckiest): the share of players who would own fewer of these slots, counting
	 * half of those who own exactly as many.
	 *
	 * @param chances each slot's chance of being owned by now
	 * @param owned   how many of those slots are owned
	 */
	public static double luckPercentile(double[] chances, int owned)
	{
		int n = chances.length;
		if (n == 0)
		{
			return 0.5;
		}
		// Distribution of the number of owned slots (Poisson binomial), built one slot at a time.
		double[] dist = new double[n + 1];
		dist[0] = 1;
		for (int i = 0; i < n; i++)
		{
			double q = clamp(chances[i]);
			for (int k = i + 1; k >= 1; k--)
			{
				dist[k] = dist[k] * (1 - q) + dist[k - 1] * q;
			}
			dist[0] *= 1 - q;
		}
		owned = Math.max(0, Math.min(n, owned));
		double below = 0;
		for (int k = 0; k < owned; k++)
		{
			below += dist[k];
		}
		return Math.max(0, Math.min(1, below + dist[owned] / 2));
	}

	/**
	 * Expected attempts until any one of these slots arrives: the first success of a roll
	 * with this summed chance per attempt, or the fixed count, whichever comes first.
	 *
	 * @param chanceSum the summed chance per attempt of the slots that can roll
	 * @param exact     attempts until a slot that is earned outright, or infinity
	 */
	public static double attemptsToNext(double chanceSum, double exact)
	{
		if (chanceSum <= 0)
		{
			return exact;
		}
		if (chanceSum >= 1)
		{
			return Math.min(1, exact);
		}
		if (Double.isInfinite(exact))
		{
			return 1 / chanceSum;
		}
		// E[min(T, n)] for a geometric T is the sum of P(T > t) for t below n.
		double attempts = Math.ceil(exact);
		return -Math.expm1(attempts * Math.log1p(-chanceSum)) / chanceSum;
	}

	/**
	 * Expected attempts until every one of these slots is owned, for slots rolled on their
	 * own odds. Each value is one slot's average attempts.
	 */
	public static double attemptsToAll(double[] attempts, double fixed)
	{
		int[] stages = new int[attempts.length];
		java.util.Arrays.fill(stages, 1);
		return attemptsToAll(fixed, attempts, stages);
	}

	/**
	 * Expected attempts until every slot is owned: the expected latest of every waiting time,
	 * integrated numerically.
	 *
	 * @param fixed  attempts spent on slots earned outright (points, permits, milestones), which add up
	 *               because each one is paid for separately
	 * @param means  for each run of rolled slots, its average attempts in total; a run is one slot, or
	 *               several that only drop in order (bludgeon pieces), which wait one after another
	 * @param stages how many slots each run holds
	 */
	public static double attemptsToAll(double fixed, double[] means, int[] stages)
	{
		int n = means.length;
		if (n == 0)
		{
			return fixed;
		}
		double slowest = 0;
		for (double mean : means)
		{
			slowest = Math.max(slowest, mean);
		}
		if (slowest <= 0)
		{
			return fixed;
		}
		// Every run is done by then, apart from a negligible chance.
		double end = fixed + slowest * (Math.log(n) + 14);
		double h = (end - fixed) / STEPS;
		double total = fixed;
		double previous = missing(means, stages, fixed);
		for (int s = 1; s <= STEPS; s++)
		{
			double t = fixed + s * h;
			double current = missing(means, stages, t);
			total += (previous + current) / 2 * h;
			previous = current;
		}
		return total;
	}

	/** The chance that at time t some run still has a slot missing. */
	private static double missing(double[] means, int[] stages, double t)
	{
		double all = 1;
		for (int i = 0; i < means.length; i++)
		{
			all *= runDone(means[i], Math.max(1, stages[i]), t);
		}
		return 1 - all;
	}

	/**
	 * The chance a run of k slots, each waited for in turn, is done by time t. The waits are
	 * taken as equal shares of the run's mean, which makes the total an Erlang wait: much
	 * tighter than one long wait with the same mean, as several pieces in a row really are.
	 */
	static double runDone(double mean, int k, double t)
	{
		if (mean <= 0)
		{
			return 1;
		}
		double x = t * k / mean;
		if (k == 1)
		{
			return -Math.expm1(-x);
		}
		double term = 1;
		double sum = 1;
		for (int j = 1; j < k; j++)
		{
			term *= x / j;
			sum += term;
		}
		return Math.max(0, 1 - Math.exp(-x) * sum);
	}

	private static double clamp(double p)
	{
		return Math.max(0, Math.min(1, p));
	}
}
