package com.codex.loginsight.core;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * When a page is likely to be finished: the chance it is done after any number of hours,
 * with the hours by which half and nine in ten players would be done. A page hunted at
 * several activities is done one activity after another, so their waits add up.
 */
@Getter
public final class Projection
{
	/** Points on the curve, enough for a smooth line at panel width. */
	static final int POINTS = 64;
	private static final int BINS = 480;
	/** Each part's wait is followed out to this chance, so the tail lost to the cut is negligible. */
	private static final double TAIL = 0.9995;

	/** Hours on the curve, from 0 up. */
	private final double[] hours;
	/** The chance the page is done by each of those hours. */
	private final double[] chance;
	private final double median;
	private final double likely;
	/** Missing slots with no rates, which the curve cannot include. */
	private final int unrated;
	/** What the account still needs before it can hunt every part, or null. */
	private String lockedBy;

	Projection(double[] hours, double[] chance, double median, double likely, int unrated)
	{
		this.hours = hours;
		this.chance = chance;
		this.median = median;
		this.likely = likely;
		this.unrated = unrated;
	}

	/**
	 * @param parts   each activity's share of the missing slots, hunted in turn
	 * @param unrated missing slots no activity has rates for
	 * @return null when nothing here can be projected
	 */
	public static Projection of(List<Prospect> parts, int unrated)
	{
		List<Prospect> live = new ArrayList<>();
		for (Prospect p : parts)
		{
			if (p.getRemaining() > 0)
			{
				if (p.getPerHour() <= 0)
				{
					return null;
				}
				live.add(p);
			}
		}
		if (live.isEmpty())
		{
			return null;
		}
		Projection projection = curve(live, unrated);
		if (projection != null)
		{
			for (Prospect p : live)
			{
				if (p.getLockedBy() != null)
				{
					projection.lockedBy = p.getLockedBy();
					break;
				}
			}
		}
		return projection;
	}

	private static Projection curve(List<Prospect> live, int unrated)
	{
		if (live.size() == 1)
		{
			Prospect p = live.get(0);
			double median = p.hoursToAll(0.5);
			double likely = p.hoursToAll(0.9);
			double end = p.hoursToAll(0.995);
			if (!Double.isFinite(end))
			{
				return null;
			}
			double[] h = new double[POINTS];
			double[] c = new double[POINTS];
			for (int i = 0; i < POINTS; i++)
			{
				h[i] = end * i / (POINTS - 1);
				c[i] = p.chanceOfAll(h[i]);
			}
			return new Projection(h, c, median, likely, unrated);
		}
		return convolved(live, unrated);
	}

	/** The waits of several activities added up, on a grid of hours. */
	private static Projection convolved(List<Prospect> parts, int unrated)
	{
		double span = 0;
		for (Prospect p : parts)
		{
			double end = p.hoursToAll(TAIL);
			if (!Double.isFinite(end))
			{
				return null;
			}
			span += end;
		}
		if (span <= 0)
		{
			return null;
		}
		double width = span / BINS;
		double[] sum = null;
		for (Prospect p : parts)
		{
			// The part's chance of finishing within each bin of hours.
			double[] mass = new double[BINS];
			double before = 0;
			for (int b = 0; b < BINS; b++)
			{
				double upTo = p.chanceOfAll((b + 1) * width);
				mass[b] = Math.max(0, upTo - before);
				before = upTo;
			}
			if (sum == null)
			{
				sum = mass;
				continue;
			}
			double[] next = new double[BINS];
			for (int a = 0; a < BINS; a++)
			{
				if (sum[a] == 0)
				{
					continue;
				}
				for (int b = 0; a + b < BINS; b++)
				{
					next[a + b] += sum[a] * mass[b];
				}
			}
			sum = next;
		}
		// Each part's mass sits at its bin's middle, so the sum of k parts sits k half-bins further on.
		double shift = parts.size() * 0.5;
		double[] cumulative = new double[BINS];
		double total = 0;
		for (int b = 0; b < BINS; b++)
		{
			total += sum[b];
			cumulative[b] = Math.min(1, total);
		}
		double[] h = new double[POINTS];
		double[] c = new double[POINTS];
		double end = Math.min(span, quantile(cumulative, width, shift, 0.995) * 1.02);
		if (!Double.isFinite(end) || end <= 0)
		{
			end = span;
		}
		for (int i = 0; i < POINTS; i++)
		{
			h[i] = end * i / (POINTS - 1);
			c[i] = at(cumulative, width, shift, h[i]);
		}
		return new Projection(h, c, quantile(cumulative, width, shift, 0.5), quantile(cumulative, width, shift, 0.9),
			unrated);
	}

	private static double at(double[] cumulative, double width, double shift, double hours)
	{
		double index = hours / width - shift - 0.5;
		if (index < 0)
		{
			return Math.max(0, index + 1) * cumulative[0];
		}
		int lo = (int) Math.floor(index);
		if (lo >= cumulative.length - 1)
		{
			return cumulative[cumulative.length - 1];
		}
		double f = index - lo;
		return cumulative[lo] * (1 - f) + cumulative[lo + 1] * f;
	}

	private static double quantile(double[] cumulative, double width, double shift, double q)
	{
		for (int b = 0; b < cumulative.length; b++)
		{
			if (cumulative[b] >= q)
			{
				double previous = b == 0 ? 0 : cumulative[b - 1];
				double f = cumulative[b] == previous ? 0 : (q - previous) / (cumulative[b] - previous);
				return Math.max(0, (b - 1 + f + shift + 0.5) * width);
			}
		}
		return Double.POSITIVE_INFINITY;
	}

	/** The chance the page is done within this many hours, read off the curve. */
	public double chanceBy(double h)
	{
		if (h <= 0)
		{
			return 0;
		}
		if (h >= hours[hours.length - 1])
		{
			return chance[chance.length - 1];
		}
		for (int i = 1; i < hours.length; i++)
		{
			if (hours[i] >= h)
			{
				double f = (h - hours[i - 1]) / (hours[i] - hours[i - 1]);
				return chance[i - 1] + (chance[i] - chance[i - 1]) * f;
			}
		}
		return chance[chance.length - 1];
	}
}
