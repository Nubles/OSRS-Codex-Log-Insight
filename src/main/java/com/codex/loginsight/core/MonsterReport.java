package com.codex.loginsight.core;

import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * One monster's share of the collection log, for the "Log" option on a right-clicked monster: only the slots it drops,
 * each owned or missing with its chance per kill and the chance an average player would have it by your kill count,
 * plus the kills an average player needs from here for the next slot it can still give you.
 */
@Getter
public final class MonsterReport
{
	/** The monster's name as the game shows it. */
	private final String npc;
	/** The collection log counter its kills add to, or null when the game keeps none. */
	private final String label;
	/** Your count on that counter, or -1 when unknown or not kept. */
	private final int kills;
	/** The log page most of its slots are on, and that page's tab. */
	private final String page;
	private final String tab;
	/**
	 * The activity whose rates stand in where the counter has no odds, such as "Killing wyrms (on task)", or null when
	 * every slot shown has odds per kill.
	 */
	private final String basis;
	/**
	 * Its slots: each report's chance is per kill of this monster, its count is your kill count, and its dry chance is
	 * the chance of still being without it by now (so one minus it is your chance to have had it).
	 */
	private final List<Report.ItemReport> items;
	/** How these slots compare with an average player at your counts, or null until they can be read. */
	private final Luck luck;
	/** Average kills from here until the next slot it can give you, or infinity when none is left or rated. */
	private final double killsToNext;
	/** The slot most likely to come next, or -1. */
	private final int nextItem;
	private final String nextName;
	/** Hours for those kills at your pace, or infinity when there is no pace for this monster. */
	private final double hoursToNext;
	/** Kills per hour behind {@link #hoursToNext}, and whether it is your own. */
	private final double perHour;
	private final boolean ownPace;
	/** True when owned slots are known (the page or the whole log has been read), so a missing slot is really missing. */
	private final boolean read;
	/** Missing slots that only drop once the piece before them is owned. */
	private final int locked;
	/** Its drop table beside what you have actually had from it, or null when it was not worked out. */
	private LootLookup lookup;

	MonsterReport(String npc, String label, int kills, String page, String tab, String basis, List<Report.ItemReport> items,
		Luck luck, double killsToNext, int nextItem, String nextName, double hoursToNext, double perHour, boolean ownPace,
		boolean read, int locked)
	{
		this.npc = npc;
		this.label = label;
		this.kills = kills;
		this.page = page;
		this.tab = tab;
		this.basis = basis;
		this.items = Collections.unmodifiableList(items);
		this.luck = luck;
		this.killsToNext = killsToNext;
		this.nextItem = nextItem;
		this.nextName = nextName;
		this.hoursToNext = hoursToNext;
		this.perHour = perHour;
		this.ownPace = ownPace;
		this.read = read;
		this.locked = locked;
	}

	void setLookup(LootLookup lookup)
	{
		this.lookup = lookup;
	}

	public int owned()
	{
		int owned = 0;
		for (Report.ItemReport item : items)
		{
			if (item.isOwned())
			{
				owned++;
			}
		}
		return owned;
	}

	public int total()
	{
		return items.size();
	}

	public boolean isComplete()
	{
		return owned() == total();
	}

	/** The chance an average player would have had this slot by your count, or -1 when that cannot be said. */
	public static double byNow(Report.ItemReport item)
	{
		return item.getDryChance() < 0 ? -1 : 1 - item.getDryChance();
	}
}
