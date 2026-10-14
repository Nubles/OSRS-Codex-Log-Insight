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
	 * Expected attempts until any one of these slots drops.
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
			return 1;
		}
		double rate = -Math.log1p(-chanceSum);
		if (Double.isInfinite(exact))
		{
			return 1 / chanceSum;
		}
		// The roll and the fixed count race; whichever comes first ends the wait.
		return (1 - Math.exp(-rate * exact)) / rate;
	}

	/**
	 * Expected attempts until every one of these slots is owned: the expected maximum of
	 * one waiting time per slot, integrated numerically.
	 *
	 * @param attempts average attempts per drop for each slot that is rolled
	 * @param exact    attempts until the last slot that is earned outright, or 0
	 */
	public static double attemptsToAll(double[] attempts, double exact)
	{
		int n = attempts.length;
		if (n == 0)
		{
			return exact;
		}
		double[] rates = new double[n];
		double slowest = 0;
		for (int i = 0; i < n; i++)
		{
			double p = clamp(1 / Math.max(1, attempts[i]));
			rates[i] = p >= 1 ? 50 : -Math.log1p(-p);
			slowest = Math.max(slowest, 1 / rates[i]);
		}
		// Past this point the chance of still missing a slot is negligible.
		double end = Math.max(exact, slowest * (Math.log(n) + 14));
		double h = end / STEPS;
		double total = 0;
		double previous = missing(rates, 0, exact);
		for (int s = 1; s <= STEPS; s++)
		{
			double t = s * h;
			double current = missing(rates, t, exact);
			total += (previous + current) / 2 * h;
			previous = current;
		}
		return Math.max(total, exact);
	}

	/** The chance that at time t some slot is still missing. */
	private static double missing(double[] rates, double t, double exact)
	{
		if (t < exact)
		{
			return 1;
		}
		double all = 1;
		for (double rate : rates)
		{
			all *= -Math.expm1(-rate * t);
		}
		return 1 - all;
	}

	private static double clamp(double p)
	{
		return Math.max(0, Math.min(1, p));
	}
}
