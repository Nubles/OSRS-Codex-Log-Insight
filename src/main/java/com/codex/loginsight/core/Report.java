package com.codex.loginsight.core;

import com.codex.loginsight.data.Activity;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/** Everything the panel shows, computed in one pass and handed over as a read-only value. */
@Getter
public final class Report
{
	private final boolean loggedIn;
	private final boolean structureLoaded;
	/** Owned slots according to the game's own counter, or -1. */
	private final int officialOwned;
	private final int officialTotal;
	/** Owned slots Log Insight can name. */
	private final int knownOwned;
	private final int totalSlots;
	private final long fullReadAt;
	private final int pagesRead;
	private final int pagesTotal;
	private final int pagesComplete;
	private final Luck luck;
	/** Hours to finish every slot that has rates, at your rates. */
	private final double hoursLeft;
	/** Missing slots without rates (bought, rewarded or unmodelled). */
	private final int unratedMissing;
	private final boolean ironRates;
	private final List<PageReport> pages;
	private final List<Hunt> hunts;
	private final List<ItemReport> driest;
	private final List<Progress.Find> finds;

	public Report(boolean loggedIn, boolean structureLoaded, int officialOwned, int officialTotal, int knownOwned,
		int totalSlots, long fullReadAt, int pagesRead, int pagesTotal, int pagesComplete, Luck luck, double hoursLeft,
		int unratedMissing, boolean ironRates, List<PageReport> pages, List<Hunt> hunts, List<ItemReport> driest,
		List<Progress.Find> finds)
	{
		this.loggedIn = loggedIn;
		this.structureLoaded = structureLoaded;
		this.officialOwned = officialOwned;
		this.officialTotal = officialTotal;
		this.knownOwned = knownOwned;
		this.totalSlots = totalSlots;
		this.fullReadAt = fullReadAt;
		this.pagesRead = pagesRead;
		this.pagesTotal = pagesTotal;
		this.pagesComplete = pagesComplete;
		this.luck = luck;
		this.hoursLeft = hoursLeft;
		this.unratedMissing = unratedMissing;
		this.ironRates = ironRates;
		this.pages = Collections.unmodifiableList(pages);
		this.hunts = Collections.unmodifiableList(hunts);
		this.driest = Collections.unmodifiableList(driest);
		this.finds = Collections.unmodifiableList(finds);
	}

	public static Report loggedOut()
	{
		return new Report(false, false, -1, -1, 0, 0, 0, 0, 0, 0, null, Double.POSITIVE_INFINITY, 0, false,
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	}

	/** The owned count to show: the game's own when it has sent one. */
	public int owned()
	{
		return officialOwned >= 0 ? Math.max(officialOwned, knownOwned) : knownOwned;
	}

	public int total()
	{
		return officialTotal > 0 ? officialTotal : totalSlots;
	}

	/** True when Log Insight knows fewer slots than the game says you own. */
	public boolean needsFullRead()
	{
		return officialOwned > knownOwned || fullReadAt == 0 && knownOwned == 0;
	}

	/** One slot as Log Insight sees it. */
	@Getter
	public static final class ItemReport
	{
		private final int itemId;
		private final String name;
		private final String page;
		private final boolean owned;
		private final boolean pet;
		/** Chance per count of the main counter, or 0 when unknown. */
		private final double chance;
		/** The counter the chance refers to, or null. */
		private final String label;
		/** Count so far on that counter, or -1. */
		private final int count;
		/** Chance of still being without it at these counts, or -1. */
		private final double dryChance;
		/** Where it comes fastest, or null. */
		private final String source;
		/** Expected hours to get it alone from its fastest source, or infinity. */
		private final double hours;

		public ItemReport(int itemId, String name, String page, boolean owned, boolean pet, double chance, String label,
			int count, double dryChance, String source, double hours)
		{
			this.itemId = itemId;
			this.name = name;
			this.page = page;
			this.owned = owned;
			this.pet = pet;
			this.chance = chance;
			this.label = label;
			this.count = count;
			this.dryChance = dryChance;
			this.source = source;
			this.hours = hours;
		}
	}

	/** One page of the log. */
	@Getter
	public static final class PageReport
	{
		private final String tab;
		private final String name;
		private final int owned;
		private final int total;
		/** Counter lines in the order the page shows them, such as "Vorkath kills: 312". */
		private final List<String> counters;
		/** The page's main counter value, or -1. */
		private final int count;
		private final String countLabel;
		/** Null when no slot on the page has odds and a known count. */
		private final Luck luck;
		/** Expected hours to finish the slots with rates, or infinity. */
		private final double hoursToFinish;
		/** Expected hours to the next new slot, or infinity. */
		private final double hoursToNext;
		private final String nextSource;
		/** Missing slots without rates. */
		private final int unrated;
		private final boolean read;
		private final List<ItemReport> items;

		public PageReport(String tab, String name, int owned, int total, List<String> counters, int count,
			String countLabel, Luck luck, double hoursToFinish, double hoursToNext, String nextSource, int unrated,
			boolean read, List<ItemReport> items)
		{
			this.tab = tab;
			this.name = name;
			this.owned = owned;
			this.total = total;
			this.counters = Collections.unmodifiableList(counters);
			this.count = count;
			this.countLabel = countLabel;
			this.luck = luck;
			this.hoursToFinish = hoursToFinish;
			this.hoursToNext = hoursToNext;
			this.nextSource = nextSource;
			this.unrated = unrated;
			this.read = read;
			this.items = Collections.unmodifiableList(items);
		}

		public boolean isComplete()
		{
			return total > 0 && owned >= total;
		}

		public double progress()
		{
			return total == 0 ? 0 : owned / (double) total;
		}
	}

	/** One activity ranked by how soon it gives a new slot. */
	@Getter
	public static final class Hunt
	{
		private final String name;
		private final Activity.Category category;
		private final double hoursToNext;
		private final double hoursToFinish;
		private final int remaining;
		/** The slot most likely to come next. */
		private final int nextItemId;
		private final String nextItemName;
		/** Chance per attempt of the most likely next slot. */
		private final double nextChance;
		/** True when the next slot is earned after a set number of attempts, such as points or permits. */
		private final boolean nextEarned;
		private final double perHour;
		/** True when the rate is your own measured pace rather than a typical one. */
		private final boolean ownPace;
		private final boolean pet;
		private final List<Integer> missing;

		public Hunt(String name, Activity.Category category, double hoursToNext, double hoursToFinish, int remaining,
			int nextItemId, String nextItemName, double nextChance, boolean nextEarned, double perHour, boolean ownPace, boolean pet,
			List<Integer> missing)
		{
			this.name = name;
			this.category = category;
			this.hoursToNext = hoursToNext;
			this.hoursToFinish = hoursToFinish;
			this.remaining = remaining;
			this.nextItemId = nextItemId;
			this.nextItemName = nextItemName;
			this.nextChance = nextChance;
			this.nextEarned = nextEarned;
			this.perHour = perHour;
			this.ownPace = ownPace;
			this.pet = pet;
			this.missing = Collections.unmodifiableList(missing);
		}
	}
}
