package com.codex.loginsight.data;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The bundled tables: every activity that fills collection log slots, how often it
 * can be done in an hour and what it drops, plus each slot's odds per kill count.
 * Built by tools/build_data.py; see THIRD_PARTY_NOTICES.md for where it comes from.
 */
public final class LogData
{
	private static final String RESOURCE = "/com/codex/loginsight/codex-log.json";

	private final List<Activity> activities;
	private final Map<Integer, List<Activity.Drop>> dropsByItem;
	private final Map<Integer, ItemOdds> odds;
	private final List<String> labels;
	private final Set<Integer> pets;

	public LogData(List<Activity> activities, List<String> labels, Map<Integer, ItemOdds> odds, Set<Integer> pets)
	{
		this.activities = Collections.unmodifiableList(new ArrayList<>(activities));
		this.labels = Collections.unmodifiableList(new ArrayList<>(labels));
		this.odds = Collections.unmodifiableMap(new HashMap<>(odds));
		this.pets = Collections.unmodifiableSet(new HashSet<>(pets));
		Map<Integer, List<Activity.Drop>> byItem = new HashMap<>();
		for (Activity activity : this.activities)
		{
			for (Activity.Drop drop : activity.getDrops())
			{
				byItem.computeIfAbsent(drop.getItemId(), k -> new ArrayList<>()).add(drop);
			}
		}
		this.dropsByItem = byItem;
	}

	public static LogData load(Gson gson) throws IOException
	{
		try (InputStream in = LogData.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				throw new IOException("Missing " + RESOURCE);
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return fromRaw(gson.fromJson(reader, Raw.class));
			}
		}
	}

	static LogData fromRaw(Raw raw)
	{
		List<Activity> activities = new ArrayList<>();
		for (RawActivity a : raw.activities)
		{
			List<Activity.Drop> drops = new ArrayList<>();
			Map<Integer, Integer> positions = new HashMap<>();
			int index = activities.size();
			Map<Integer, RawPet> scaled = new HashMap<>();
			if (a.p != null)
			{
				for (RawPet pet : a.p)
				{
					if (pet != null && pet.s != null && pet.b > 0)
					{
						scaled.put(pet.i, pet);
					}
				}
			}
			for (double[] d : a.d)
			{
				RawPet pet = scaled.get((int) d[0]);
				Activity.Drop drop = pet == null ? new Activity.Drop(index, (int) d[0], d[1], (int) d[2])
					: new Activity.Drop(index, (int) d[0], d[1], (int) d[2], pet.s, pet.b);
				Integer at = positions.putIfAbsent(drop.getItemId(), drops.size());
				if (at == null)
				{
					drops.add(drop);
				}
				else if (better(drop, drops.get(at)))
				{
					// A second row for the same slot is another way to earn it on the same activity (destroying
					// Araxxor instead of looting, buying with points instead of rolling). They are alternatives,
					// not extra rolls, so the slot takes whichever is quicker, in the first row's place.
					drops.set(at, drop);
				}
			}
			Map<String, Integer> skills = a.r == null || a.r.s == null ? Collections.emptyMap() : a.r.s;
			List<String> quests = a.r == null || a.r.q == null ? Collections.emptyList() : a.r.q;
			activities.add(new Activity(index, a.n, Activity.Category.of(a.c), a.m, a.i, a.f, drops, skills, quests));
		}
		Map<Integer, ItemOdds> odds = new HashMap<>();
		for (Map.Entry<String, RawOdds> e : raw.odds.entrySet())
		{
			List<ItemOdds.Roll> rolls = new ArrayList<>();
			for (double[] r : e.getValue().r)
			{
				rolls.add(new ItemOdds.Roll(raw.labels.get((int) r[0]), r[1], (int) r[2]));
			}
			odds.put(Integer.parseInt(e.getKey()), new ItemOdds(rolls, e.getValue().g));
		}
		Set<Integer> pets = new HashSet<>();
		for (int id : raw.pets)
		{
			pets.add(id);
		}
		return new LogData(activities, raw.labels, odds, pets);
	}

	/**
	 * These tables at an account's real levels, so a skilling pet's odds are the ones this account rolls: a
	 * level 40 thief waits longer for Rocky than a level 99 one. Itself when no rate moves.
	 *
	 * @param levels real level by RuneLite skill name, or null when unknown
	 * @param maxed  skills with 200M experience, whose pets are 15 times likelier
	 */
	public LogData atLevels(Map<String, Integer> levels, Set<String> maxed)
	{
		if (levels == null)
		{
			return this;
		}
		List<Activity> scaled = new ArrayList<>(activities.size());
		boolean changed = false;
		for (Activity activity : activities)
		{
			Activity at = activity.atLevels(levels, maxed);
			changed |= at != activity;
			scaled.add(at);
		}
		return changed ? new LogData(scaled, labels, odds, pets) : this;
	}

	/** The skills any rate depends on, by RuneLite name. */
	public Set<String> scaledSkills()
	{
		Set<String> skills = new java.util.TreeSet<>();
		for (Activity activity : activities)
		{
			for (Activity.Drop drop : activity.getDrops())
			{
				if (drop.isLevelScaled())
				{
					skills.add(drop.getScaleSkill());
				}
			}
		}
		return skills;
	}

	/** True when this row earns its slot sooner than the other, a fixed count winning ties. */
	private static boolean better(Activity.Drop drop, Activity.Drop other)
	{
		if (drop.getAttempts() != other.getAttempts())
		{
			return drop.getAttempts() < other.getAttempts();
		}
		return drop.isExact() && !other.isExact();
	}

	public List<Activity> getActivities()
	{
		return activities;
	}

	/** Every activity row that can drop this item, possibly none. */
	public List<Activity.Drop> dropsOf(int itemId)
	{
		List<Activity.Drop> drops = dropsByItem.get(itemId);
		return drops == null ? Collections.emptyList() : drops;
	}

	public Activity activity(int index)
	{
		return activities.get(index);
	}

	/** Odds per kill count, or null for slots that are bought, guaranteed or not modelled. */
	public ItemOdds odds(int itemId)
	{
		return odds.get(itemId);
	}

	/** Every slot with odds per kill count, by item id. */
	public Map<Integer, ItemOdds> allOdds()
	{
		return odds;
	}

	/** Every quest any activity needs, by name. */
	public Set<String> questsNeeded()
	{
		Set<String> quests = new java.util.TreeSet<>();
		for (Activity activity : activities)
		{
			quests.addAll(activity.getQuests());
		}
		return quests;
	}

	/** The collection log counter names the odds refer to, such as "Vorkath kills". */
	public List<String> getLabels()
	{
		return labels;
	}

	public boolean isPet(int itemId)
	{
		return pets.contains(itemId);
	}

	static final class Raw
	{
		List<RawActivity> activities = new ArrayList<>();
		int[] pets = new int[0];
		List<String> labels = new ArrayList<>();
		Map<String, RawOdds> odds = new HashMap<>();
	}

	static final class RawActivity
	{
		String n;
		String c;
		double m;
		double i;
		double f;
		List<double[]> d = new ArrayList<>();
		RawNeeds r;
		/** Skilling pets on this table whose odds follow a level. */
		List<RawPet> p;
	}

	static final class RawPet
	{
		int i;
		String s;
		double b;
	}

	static final class RawNeeds
	{
		Map<String, Integer> s;
		List<String> q;
	}

	static final class RawOdds
	{
		List<double[]> r = new ArrayList<>();
		int g;
	}
}
