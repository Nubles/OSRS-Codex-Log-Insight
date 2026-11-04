package com.codex.loginsight.core;

import com.codex.loginsight.data.Activity;
import com.codex.loginsight.data.ItemOdds;
import com.codex.loginsight.data.LogData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * Which monster fills which collection log slots, for the "Log" option on a right-clicked monster.
 *
 * <p>A boss is matched to the collection log's own kill counter by its name: "Zulrah" counts "Zulrah kills", "The
 * Leviathan" counts "Leviathan kills". The per-kill odds on that counter then say which slots it drops. A few bosses go by
 * other names than their counter (Dusk and Dawn are the Grotesque Guardians), and a few counters carry no odds, so their
 * slots come from the activity Log Insight already times them by. Monsters the game keeps no kill count for (Slayer
 * creatures, dragons, giants) are matched to that activity alone.
 *
 * <p>Every NPC name here is the one RuneLite's gameval {@code NpcID} gives it, and the few NPC ids are checked against
 * the same file and the OSRS Wiki (see BestiaryTest).
 */
public final class Bestiary
{
	/** Bosses whose counter is not their own name, by lower-case NPC name. */
	static final Map<String, String> COUNTERS;

	/** Monsters matched to an activity's rates, by lower-case NPC name. */
	static final Map<String, Rule> ACTIVITIES;

	static
	{
		Map<String, String> counters = new LinkedHashMap<>();
		counters.put("dusk", "Grotesque Guardian kills");
		counters.put("dawn", "Grotesque Guardian kills");
		counters.put("branda the fire queen", "Royal Titan kills");
		counters.put("eldric the ice king", "Royal Titan kills");
		COUNTERS = Collections.unmodifiableMap(counters);

		Map<String, Rule> rules = new LinkedHashMap<>();
		// Bosses whose counter carries few or no odds: the activity fills in the rest.
		rule(rules, "Scurrius", "Killing scurrius");
		rule(rules, "The Nightmare", "Killing the nightmare of ashihama (5 person team)");
		rule(rules, "Phosani's Nightmare", "Killing phosani's nightmare");
		rule(rules, "Yama", "Defeating yama (duo)");
		// Slayer creatures and other monsters the game keeps no kill count for.
		rule(rules, "Crawling Hand", "Killing crawling hands");
		rule(rules, "Cockatrice", "Killing cockatrice");
		rule(rules, "Basilisk", "Killing basilisks");
		rule(rules, "Basilisk Knight", "Killing basilisk knights (on task)");
		rule(rules, "Kurask", "Killing kurask");
		// Only the Catacombs of Kourend versions roll the ancient shard and dark totem pieces the activity counts.
		rule(rules, "Abyssal demon", "Killing abyssal demons (on task)", 7241);
		rule(rules, "Dust devil", "Killing dust devils (on task)", 7249);
		rule(rules, "Waterfiend", "Killing waterfiends");
		rule(rules, "Gargoyle", "Killing gargoyles");
		rule(rules, "Mogre", "Killing mogres");
		rule(rules, "Brine rat", "Killing brine rats");
		rule(rules, "Turoth", "Killing turoth");
		rule(rules, "Cave horror", "Killing cave horrors");
		rule(rules, "Spitting Wyvern", "Killing fossil island wyverns");
		rule(rules, "Taloned Wyvern", "Killing fossil island wyverns");
		rule(rules, "Long-tailed Wyvern", "Killing fossil island wyverns");
		rule(rules, "Ancient Wyvern", "Killing ancient wyverns");
		rule(rules, "Skeletal Wyvern", "Killing skeletal wyverns");
		rule(rules, "Terror dog", "Killing terror dogs");
		rule(rules, "Cave crawler", "Killing cave crawlers");
		rule(rules, "Cave slime", "Killing cave slimes");
		rule(rules, "Pyrefiend", "Killing pyrefiends");
		rule(rules, "Harpie Bug Swarm", "Killing harpie bug swarms");
		rule(rules, "Bloodveld", "Killing bloodvelds");
		rule(rules, "Cave kraken", "Killing cave kraken (non-boss, on task)");
		rule(rules, "Dark beast", "Killing dark beasts");
		rule(rules, "Smoke devil", "Killing smoke devils (on task)");
		rule(rules, "Wyrm", "Killing wyrms (on task)");
		rule(rules, "Drake", "Killing drakes (on task)");
		rule(rules, "Sulphur Lizard", "Killing sulphur lizards");
		rule(rules, "Infernal Mage", "Killing infernal mages");
		rule(rules, "Banshee", "Killing banshees");
		rule(rules, "Aberrant spectre", "Killing aberrant spectres");
		rule(rules, "Aquanite", "Killing aquanites (on task)");
		rule(rules, "Vyrewatch Sentinel", "Killing vyrewatch sentinels");
		rule(rules, "Warped terrorbird", "Killing warped terrorbirds");
		rule(rules, "Sulphur Nagua", "Killing sulphur naguas");
		rule(rules, "Earthen Nagua", "Killing earthen naguas");
		rule(rules, "Elder Custodian Stalker", "Killing elder custodian stalkers");
		rule(rules, "Gryphon", "Killing gryphons (on task)");
		rule(rules, "Lizardman", "Killing lizardmen");
		// The shamans in the Chambers of Xeric (7573, 7574) drop from the raid's own table, not this one.
		rule(rules, "Lizardman shaman", "Killing lizardman shamans", 6766, 6767, 7744, 7745, 8565);
		rule(rules, "Mithril dragon", "Killing mithril dragons");
		rule(rules, "Adamant dragon", "Killing adamant dragons");
		rule(rules, "Rune dragon", "Killing rune dragons");
		rule(rules, "Frost dragon", "Killing frost dragons (on bounty task)");
		rule(rules, "Ogress Shaman", "Killing ogress shamans");
		rule(rules, "Moss giant", "Killing moss giants");
		rule(rules, "Hill Giant", "Killing hill giants");
		rule(rules, "Undead Druid", "Killing undead druids");
		rule(rules, "Elder Chaos druid", "Killing elder chaos druids");
		rule(rules, "Venator", "Killing Venators (on task)");
		rule(rules, "Molanisk", "Killing molanisks");
		rule(rules, "Killerwatt", "Killing killerwatts");
		ACTIVITIES = Collections.unmodifiableMap(rules);
	}

	private static void rule(Map<String, Rule> rules, String npc, String activity, int... onlyIds)
	{
		rules.put(normalise(npc), new Rule(npc, activity, onlyIds));
	}

	/** The names the counters above are kept under, as the game shows them. */
	private static final String[] COUNTER_NAMES = {"Dusk", "Dawn", "Branda the Fire Queen", "Eldric the Ice King"};

	/** An activity a monster is timed by, optionally only for some of the NPC ids that share its name. */
	static final class Rule
	{
		final String npc;
		final String activity;
		final Set<Integer> onlyIds;
		/** An NPC id the rule covers, for looking it up by name alone, or -1 when every id is covered. */
		final int anyId;

		Rule(String npc, String activity, int[] onlyIds)
		{
			this.npc = npc;
			this.activity = activity;
			this.anyId = onlyIds.length == 0 ? -1 : onlyIds[0];
			Set<Integer> ids = new HashSet<>();
			for (int id : onlyIds)
			{
				ids.add(id);
			}
			this.onlyIds = Collections.unmodifiableSet(ids);
		}

		boolean covers(int npcId)
		{
			return onlyIds.isEmpty() || onlyIds.contains(npcId);
		}
	}

	/** What a monster is looked up by: its counter, the activities that time it, or both. */
	@Getter
	public static final class Target
	{
		/** The monster's name as shown in the game. */
		private final String npc;
		/** The collection log counter its kills add to, or null when the game keeps none. */
		private final String label;
		/** Activities whose drops and pace it is timed by, the named one first. */
		private final List<Integer> activities;

		Target(String npc, String label, List<Integer> activities)
		{
			this.npc = npc;
			this.label = label;
			this.activities = Collections.unmodifiableList(new ArrayList<>(activities));
		}

		/** One key per monster, so a stack of the same monster gets one menu option. */
		public String key()
		{
			return label != null ? label : String.valueOf(activities);
		}
	}

	private final Set<String> rolled = new HashSet<>();
	private final Map<String, String> labelsByName = new HashMap<>();
	private final Map<String, List<Integer>> activitiesByLabel = new HashMap<>();
	private final Map<String, Integer> activitiesByName = new HashMap<>();

	/**
	 * @param counterOf the counter each activity advances, by activity index, when its slots agree on one
	 */
	public Bestiary(LogData data, Map<Integer, String> counterOf)
	{
		for (ItemOdds odds : data.allOdds().values())
		{
			for (ItemOdds.Roll roll : odds.getRolls())
			{
				rolled.add(roll.getLabel());
			}
		}
		for (Map.Entry<Integer, String> e : counterOf.entrySet())
		{
			activitiesByLabel.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
		}
		for (List<Integer> list : activitiesByLabel.values())
		{
			Collections.sort(list);
		}
		for (String label : data.getLabels())
		{
			if (label.endsWith(" kills"))
			{
				labelsByName.put(normalise(label.substring(0, label.length() - " kills".length())), label);
			}
		}
		for (Activity activity : data.getActivities())
		{
			activitiesByName.put(activity.getName(), activity.getIndex());
		}
	}

	/** True when every activity this class names is one of the bundled ones; checked by a test. */
	boolean knowsEveryActivity()
	{
		for (Rule rule : ACTIVITIES.values())
		{
			if (!activitiesByName.containsKey(rule.activity))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * What this monster fills in the log, or null when Log Insight knows of nothing it drops.
	 *
	 * @param name  the NPC's name as the game shows it, colour tags allowed
	 * @param npcId the NPC's id, for the few names whose versions drop differently
	 */
	public Target lookup(String name, int npcId)
	{
		if (name == null)
		{
			return null;
		}
		String key = normalise(name);
		if (key.isEmpty())
		{
			return null;
		}
		String label = COUNTERS.get(key);
		if (label == null)
		{
			label = labelsByName.get(key);
		}
		List<Integer> activities = new ArrayList<>();
		Rule rule = ACTIVITIES.get(key);
		if (rule != null && rule.covers(npcId))
		{
			Integer index = activitiesByName.get(rule.activity);
			if (index != null)
			{
				activities.add(index);
			}
		}
		if (label != null)
		{
			for (int index : activitiesByLabel.getOrDefault(label, Collections.emptyList()))
			{
				if (!activities.contains(index))
				{
					activities.add(index);
				}
			}
		}
		// A counter with no odds on it (Tempoross, Wintertodt) says nothing about slots unless an activity times it.
		if (activities.isEmpty() && (label == null || !rolled.contains(label)))
		{
			return null;
		}
		return new Target(clean(name), label, activities);
	}

	/**
	 * Every monster whose name holds this text, for the panel's search: bosses by their counter's name and the monsters
	 * Log Insight times by an activity. Names that start with it come first, then the rest alphabetically.
	 *
	 * @param query at least two letters; fewer match nothing
	 */
	public List<String> search(String query, int limit)
	{
		String needle = query == null ? "" : normalise(query);
		List<String> found = new ArrayList<>();
		if (needle.length() < 2)
		{
			return found;
		}
		Map<String, String> names = new java.util.TreeMap<>();
		for (String label : labelsByName.values())
		{
			String name = label.substring(0, label.length() - " kills".length());
			names.putIfAbsent(normalise(name), name);
		}
		for (String name : COUNTER_NAMES)
		{
			names.putIfAbsent(normalise(name), name);
		}
		for (Rule rule : ACTIVITIES.values())
		{
			names.putIfAbsent(normalise(rule.npc), rule.npc);
		}
		List<String> later = new ArrayList<>();
		for (Map.Entry<String, String> e : names.entrySet())
		{
			if (!e.getKey().contains(needle) || find(e.getValue()) == null)
			{
				continue;
			}
			(e.getKey().startsWith(needle) ? found : later).add(e.getValue());
		}
		found.addAll(later);
		return found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
	}

	/** A monster by its name alone, as the panel's search finds it; null when Log Insight knows nothing it drops. */
	public Target find(String name)
	{
		Rule rule = name == null ? null : ACTIVITIES.get(normalise(name));
		return lookup(name, rule == null ? -1 : rule.anyId);
	}

	/** Lower case, without colour tags, extra spaces or a leading "The". */
	static String normalise(String name)
	{
		String plain = clean(name).toLowerCase(Locale.ENGLISH);
		return plain.startsWith("the ") ? plain.substring(4) : plain;
	}

	private static String clean(String name)
	{
		return name.replaceAll("<[^>]*>", "").replace(' ', ' ').trim().replaceAll("\\s+", " ");
	}
}
