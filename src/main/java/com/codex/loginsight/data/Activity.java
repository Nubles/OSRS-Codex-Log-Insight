package com.codex.loginsight.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import lombok.Getter;

/** Something you can repeat to fill log slots: a boss, a raid, a casket, a minigame round. */
@Getter
public final class Activity
{
	public enum Category
	{
		COMBAT("Combat"),
		MINIGAME("Minigames"),
		OTHER("Other");

		@Getter
		private final String label;

		Category(String label)
		{
			this.label = label;
		}

		static Category of(String code)
		{
			if ("C".equals(code))
			{
				return COMBAT;
			}
			return "M".equals(code) ? MINIGAME : OTHER;
		}
	}

	private final int index;
	private final String name;
	private final Category category;
	/** Typical completions per hour on a normal account. */
	private final double perHourMain;
	/** Typical completions per hour for an ironman, who has to supply everything themselves. */
	private final double perHourIron;
	/** One-off hours before the first attempt, such as unlocking the content. */
	private final double setupHours;
	/** In table order, which matters for slots that only drop once the one before is owned. */
	private final List<Drop> drops;
	/** Real skill levels needed, by RuneLite skill name such as "SLAYER". */
	private final Map<String, Integer> skills;
	/** Quests that must be finished, by name. */
	private final List<String> quests;

	public Activity(int index, String name, Category category, double perHourMain, double perHourIron, double setupHours, List<Drop> drops)
	{
		this(index, name, category, perHourMain, perHourIron, setupHours, drops, Collections.emptyMap(), Collections.emptyList());
	}

	public Activity(int index, String name, Category category, double perHourMain, double perHourIron, double setupHours,
		List<Drop> drops, Map<String, Integer> skills, List<String> quests)
	{
		this.skills = Collections.unmodifiableMap(new LinkedHashMap<>(skills));
		this.quests = Collections.unmodifiableList(new ArrayList<>(quests));
		this.index = index;
		this.name = name;
		this.category = category;
		this.perHourMain = perHourMain;
		this.perHourIron = perHourIron;
		this.setupHours = setupHours;
		this.drops = Collections.unmodifiableList(drops);
	}

	public double perHour(boolean iron)
	{
		return iron ? perHourIron : perHourMain;
	}

	/** One slot on an activity's table. */
	@Getter
	public static final class Drop
	{
		private static final int EXACT = 1;
		private static final int INDEPENDENT = 2;
		private static final int SEQUENTIAL = 4;

		private final int activityIndex;
		private final int itemId;
		/** Average attempts per drop, so 512 means 1 in 512. */
		private final double attempts;
		private final int flags;

		public Drop(int activityIndex, int itemId, double attempts, int flags)
		{
			this.activityIndex = activityIndex;
			this.itemId = itemId;
			this.attempts = attempts;
			this.flags = flags;
		}

		/** Earned after a fixed number of attempts (points, permits, a shop) rather than rolled. */
		public boolean isExact()
		{
			return (flags & EXACT) != 0;
		}

		/** Only drops once the slot listed before it on the table is owned (such as bludgeon pieces). */
		public boolean isSequential()
		{
			return (flags & SEQUENTIAL) != 0;
		}

		public boolean isIndependent()
		{
			return (flags & INDEPENDENT) != 0;
		}
	}
}
