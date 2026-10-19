package com.codex.loginsight.core;

import lombok.Getter;

/**
 * The card for the grind you are on: the log page your last kill count belongs to, the count and what this session
 * added to it, the wait for the page's next slot, and the one slot most worth watching there (a wanted one first,
 * else the one you are driest on). Built from a finished report, so it costs nothing on the client thread.
 */
@Getter
public final class Grind
{
	/** The card stays up this long after the last kill count, then steps aside. */
	public static final long SHOW_MS = 5 * 60_000L;

	private final String page;
	private final String label;
	private final int count;
	/** Counts added to this counter since logging in, or 0. */
	private final int added;
	private final double hoursToNext;
	private final int missing;
	/** The slot to watch, or null when the page has none with odds. */
	private final Report.ItemReport focus;
	private final boolean focusWanted;
	/** When the kill count that put this grind on screen arrived. */
	private final long at;

	private Grind(String page, String label, int count, int added, double hoursToNext, int missing, Report.ItemReport focus,
		boolean focusWanted, long at)
	{
		this.page = page;
		this.label = label;
		this.count = count;
		this.added = added;
		this.hoursToNext = hoursToNext;
		this.missing = missing;
		this.focus = focus;
		this.focusWanted = focusWanted;
		this.at = at;
	}

	/**
	 * The grind for a counter, or null when no unfinished page counts by it. Of several pages counted by one
	 * counter, the one with the most slots left is the grind.
	 *
	 * @param at when the counter last moved
	 */
	public static Grind of(Report report, String label, long at)
	{
		if (report == null || label == null)
		{
			return null;
		}
		Report.PageReport best = null;
		for (Report.PageReport page : report.getPages())
		{
			if (label.equals(page.getCountLabel()) && !page.isComplete()
				&& (best == null || page.getTotal() - page.getOwned() > best.getTotal() - best.getOwned()))
			{
				best = page;
			}
		}
		if (best == null)
		{
			return null;
		}
		Report.ItemReport focus = null;
		boolean wanted = false;
		for (Report.ItemReport item : report.getWanted())
		{
			if (label.equals(item.getLabel()) && !item.isOwned())
			{
				focus = item;
				wanted = true;
				break;
			}
		}
		if (focus == null)
		{
			for (Report.ItemReport item : best.getItems())
			{
				if (!item.isOwned() && item.getDryChance() >= 0 && label.equals(item.getLabel())
					&& (focus == null || item.getDryChance() < focus.getDryChance()))
				{
					focus = item;
				}
			}
		}
		int added = 0;
		Session.Summary session = report.getSession();
		if (session != null)
		{
			for (Session.Gain gain : session.getGains())
			{
				if (label.equals(gain.getLabel()))
				{
					added = gain.getAdded();
				}
			}
		}
		return new Grind(best.getName(), label, Math.max(0, best.getCount()), added, best.getHoursToNext(),
			best.getTotal() - best.getOwned(), focus, wanted, at);
	}

	/** True while the card should be on screen. */
	public boolean isShowing(long now)
	{
		return now - at < SHOW_MS;
	}
}
