package com.codex.loginsight.core;

/**
 * The sums behind a wanted slot: how many more counts until half, or nine in ten, of players would have it, and
 * the chance of it turning up in a stretch of play. All of it comes from the slot's own report, so it uses the
 * same odds and pace as the rest of the panel.
 */
public final class Wanted
{
	private Wanted()
	{
	}

	/** The count of the slot's main counter by which this share of players would have it, or -1 when unknown. */
	public static int countFor(Report.ItemReport item, double share)
	{
		if (item.getChance() <= 0 || item.getLabel() == null)
		{
			return -1;
		}
		return DryWatch.countFor(item.getChance(), share);
	}

	/**
	 * More counts from where you are until this share of players would have the slot: 0 once you are past it,
	 * -1 when the odds or your count are unknown.
	 */
	public static int moreUntil(Report.ItemReport item, double share)
	{
		int at = countFor(item, share);
		if (at < 0 || item.getCount() < 0)
		{
			return -1;
		}
		return Math.max(0, at - item.getCount());
	}

	/**
	 * The chance of the slot in the next {@code hours} of play at its fastest source and your pace, or -1 when it
	 * cannot be timed. Each attempt is a fresh roll, so the hours already spent change nothing here.
	 */
	public static double chanceWithin(Report.ItemReport item, double hours)
	{
		double expected = item.getHours();
		if (!(expected > 0) || Double.isInfinite(expected) || hours <= 0)
		{
			return -1;
		}
		return 1 - Math.exp(-hours / expected);
	}

	/**
	 * The play, in hours, after which there is an even chance of having the slot from its fastest source: the
	 * median wait, which reads better than the mean for a single rare drop. -1 when it cannot be timed.
	 */
	public static double evenChanceHours(Report.ItemReport item)
	{
		double expected = item.getHours();
		if (!(expected > 0) || Double.isInfinite(expected))
		{
			return -1;
		}
		return expected * Math.log(2);
	}
}
