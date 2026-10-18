package com.codex.loginsight.core;

import com.codex.loginsight.data.Activity;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What this account can do yet: its real skill levels and the quests it still has to
 * finish. Read on the client thread and handed to the analysis as a plain value.
 */
public final class Unlocks
{
	/** Before the account has been read: nothing counts as locked. */
	public static final Unlocks UNKNOWN = new Unlocks(null, Collections.emptySet());

	private final Map<String, Integer> levels;
	private final Set<String> unfinished;

	/**
	 * @param levels     real level by RuneLite skill name, or null when unknown
	 * @param unfinished quests known to be unfinished, by name
	 */
	public Unlocks(Map<String, Integer> levels, Set<String> unfinished)
	{
		this.levels = levels == null ? null : new HashMap<>(levels);
		this.unfinished = new HashSet<>();
		for (String quest : unfinished)
		{
			this.unfinished.add(normalise(quest));
		}
	}

	/** The first thing this activity still needs, such as "85 Slayer", or null when it can be done. */
	public String missing(Activity activity)
	{
		if (levels != null)
		{
			for (Map.Entry<String, Integer> need : activity.getSkills().entrySet())
			{
				Integer level = levels.get(need.getKey());
				if (level != null && level < need.getValue())
				{
					String skill = need.getKey().toLowerCase(Locale.ENGLISH);
					return need.getValue() + " " + Character.toUpperCase(skill.charAt(0)) + skill.substring(1);
				}
			}
		}
		for (String quest : activity.getQuests())
		{
			if (unfinished.contains(normalise(quest)))
			{
				return quest;
			}
		}
		return null;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Unlocks))
		{
			return false;
		}
		Unlocks other = (Unlocks) o;
		return java.util.Objects.equals(levels, other.levels) && unfinished.equals(other.unfinished);
	}

	@Override
	public int hashCode()
	{
		return java.util.Objects.hash(levels, unfinished);
	}

	/** Quest names compared on letters and digits only, so "Heroes' Quest" matches "HEROES_QUEST". */
	public static String normalise(String name)
	{
		return name == null ? "" : name.toUpperCase(Locale.ENGLISH).replaceAll("[^A-Z0-9]", "");
	}
}
