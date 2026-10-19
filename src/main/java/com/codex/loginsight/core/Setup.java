package com.codex.loginsight.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * What still stands between a fresh install and numbers worth trusting: the whole log read once, the game set to
 * announce new slots (Log Insight only sees a find the game announces), and kill counts on the pages you have
 * played. Worked out from a report and the game's notification setting; nothing here touches the client.
 */
public final class Setup
{
	/** The game's setting before it has arrived after login. */
	public static final int NOTICES_UNKNOWN = -1;
	/** Pages named as worth opening, the ones hiding the most finds first. */
	static final int PAGES_SHOWN = 3;

	private final boolean logRead;
	private final int notices;
	private final int pagesCounted;
	private final int pagesCountable;
	private final List<Report.PageReport> toOpen;
	private final int toOpenTotal;

	private Setup(boolean logRead, int notices, int pagesCounted, int pagesCountable, List<Report.PageReport> toOpen,
		int toOpenTotal)
	{
		this.logRead = logRead;
		this.notices = notices;
		this.pagesCounted = pagesCounted;
		this.pagesCountable = pagesCountable;
		this.toOpen = Collections.unmodifiableList(toOpen);
		this.toOpenTotal = toOpenTotal;
	}

	/**
	 * @param notices the game's "new addition" setting for the collection log: 0 none, 1 chat, 2 pop-up, 3 both,
	 *                or {@link #NOTICES_UNKNOWN} before it has been read
	 */
	public static Setup of(Report report, int notices)
	{
		int counted = 0;
		int countable = 0;
		List<Report.PageReport> open = new ArrayList<>();
		for (Report.PageReport page : report.getPages())
		{
			if (page.getCountLabel() == null)
			{
				continue;
			}
			countable++;
			if (page.getCount() >= 0)
			{
				counted++;
			}
			// A page you own something on has been played; without its count, those finds earn no luck. A page
			// already opened that still shows no count would not show one next time either.
			else if (page.getOwned() > 0 && !page.isRead())
			{
				open.add(page);
			}
		}
		open.sort(Comparator.comparingInt(Report.PageReport::getOwned).reversed()
			.thenComparing(Report.PageReport::getName));
		int total = open.size();
		return new Setup(!report.needsFullRead(), notices, counted, countable,
			new ArrayList<>(open.subList(0, Math.min(PAGES_SHOWN, total))), total);
	}

	/** True once the log has been read through its Search button and Log Insight knows every slot the game counts. */
	public boolean isLogRead()
	{
		return logRead;
	}

	/** True when the game is set to announce no new slots, so a find goes unseen. */
	public boolean noticesOff()
	{
		return notices == 0;
	}

	public boolean noticesKnown()
	{
		return notices >= 0;
	}

	public int getPagesCounted()
	{
		return pagesCounted;
	}

	public int getPagesCountable()
	{
		return pagesCountable;
	}

	/** Pages you have finds on but no kill count for, most finds first, at most {@link #PAGES_SHOWN}. */
	public List<Report.PageReport> getToOpen()
	{
		return toOpen;
	}

	/** How many pages in all have finds but no kill count. */
	public int getToOpenTotal()
	{
		return toOpenTotal;
	}

	/** Nothing left to set up: the card steps aside. An unread setting is not held against you. */
	public boolean isDone()
	{
		return logRead && !noticesOff() && toOpenTotal == 0;
	}
}
