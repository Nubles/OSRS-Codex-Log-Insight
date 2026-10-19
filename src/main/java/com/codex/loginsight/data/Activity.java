package com.codex.loginsight.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
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

	/** This activity with its drops at an account's levels; itself when none of its rates depend on a level. */
	public Activity atLevels(Map<String, Integer> levels, Set<String> maxed)
	{
		List<Drop> scaled = null;
		for (int i = 0; i < drops.size(); i++)
		{
			Drop drop = drops.get(i);
			Drop at = drop.atLevels(levels, maxed);
			if (at != drop)
			{
				if (scaled == null)
				{
					scaled = new ArrayList<>(drops);
				}
				scaled.set(i, at);
			}
		}
		return scaled == null ? this : new Activity(index, name, category, perHourMain, perHourIron, setupHours, scaled, skills, quests);
	}

	/** One slot on an activity's table. */
	@Getter
	public static final class Drop
	{
		private static final int EXACT = 1;
		private static final int INDEPENDENT = 2;
		private static final int SEQUENTIAL = 4;
		/** Skilling pets roll 1 in (base - 25 x level). */
		static final int PET_LEVEL_STEP = 25;
		/** With 200M experience in the skill, a skilling pet is this many times likelier. */
		static final int MAXED_BOOST = 15;

		private final int activityIndex;
		private final int itemId;
		/** Average attempts per drop, so 512 means 1 in 512. For a skilling pet, at level 99 until levels are known. */
		private final double attempts;
		private final int flags;
		/** The skill a skilling pet's chance depends on, by RuneLite name, or null when the chance is flat. */
		private final String scaleSkill;
		/** A skilling pet's base: the chance per action is 1 in (base - 25 x level). */
		private final double scaleBase;

		public Drop(int activityIndex, int itemId, double attempts, int flags)
		{
			this(activityIndex, itemId, attempts, flags, null, 0);
		}

		public Drop(int activityIndex, int itemId, double attempts, int flags, String scaleSkill, double scaleBase)
		{
			this.activityIndex = activityIndex;
			this.itemId = itemId;
			this.attempts = attempts;
			this.flags = flags;
			this.scaleSkill = scaleBase > 0 ? scaleSkill : null;
			this.scaleBase = scaleBase;
		}

		/** Average attempts for a skilling pet at this level, with 200M experience or without. */
		public static double petAttempts(double base, int level, boolean maxed)
		{
			int clamped = Math.max(1, Math.min(99, level));
			double attempts = Math.max(1, base - PET_LEVEL_STEP * clamped);
			return maxed ? Math.max(1, attempts / MAXED_BOOST) : attempts;
		}

		/**
		 * This slot at an account's real levels: a skilling pet's odds follow its skill, everything else is
		 * unchanged. Itself when the level is unknown or the odds do not move.
		 */
		public Drop atLevels(Map<String, Integer> levels, Set<String> maxed)
		{
			if (scaleSkill == null || levels == null)
			{
				return this;
			}
			Integer level = levels.get(scaleSkill);
			if (level == null || level <= 0)
			{
				return this;
			}
			double at = petAttempts(scaleBase, level, maxed != null && maxed.contains(scaleSkill));
			return at == attempts ? this : new Drop(activityIndex, itemId, at, flags, scaleSkill, scaleBase);
		}

		/** True when this slot's odds depend on a skill level. */
		public boolean isLevelScaled()
		{
			return scaleSkill != null;
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
