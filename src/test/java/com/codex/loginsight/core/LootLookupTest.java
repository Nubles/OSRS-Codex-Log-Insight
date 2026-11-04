package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.loginsight.data.Activity;
import com.codex.loginsight.data.ItemOdds;
import com.codex.loginsight.data.LogData;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** A monster's drop table beside your own loot, worked by hand on a small made-up table. */
public class LootLookupTest
{
	private static final int SHIELD = 1;
	private static final int STAFF = 2;
	private static final int PET = 3;
	private static final int TOP = 4;
	private static final int TOKEN = 5;
	private static final int SCALE = 6;

	private final LogData data = data();
	private final Analyst analyst = new Analyst(data);
	private final LogStructure structure = new LogStructure(Collections.singletonList(new LogStructure.Tab("Bosses",
		Collections.singletonList(new LogStructure.Page("Bosses", "Beast", Arrays.asList(SHIELD, STAFF, PET, TOP, TOKEN, SCALE))))),
		names());

	private static LogData data()
	{
		// TOP has no odds on the counter, only the activity's 1 in 400; TOKEN is earned after 50 kills; SCALE is common.
		List<Activity.Drop> beast = Arrays.asList(
			new Activity.Drop(0, SHIELD, 100, 0),
			new Activity.Drop(0, STAFF, 400, 0),
			new Activity.Drop(0, TOP, 400, 0),
			new Activity.Drop(0, PET, 1000, 0),
			new Activity.Drop(0, TOKEN, 50, 1),
			new Activity.Drop(0, SCALE, 5, 0));
		Map<Integer, ItemOdds> odds = new HashMap<>();
		odds.put(SHIELD, odds(100));
		odds.put(STAFF, odds(400));
		odds.put(PET, odds(1000));
		odds.put(SCALE, odds(5));
		return new LogData(Collections.singletonList(new Activity(0, "Killing the beast", Activity.Category.COMBAT, 30, 25, 0, beast)),
			Collections.singletonList("Beast kills"), odds, Collections.singleton(PET));
	}

	private static ItemOdds odds(double in)
	{
		return new ItemOdds(Collections.singletonList(new ItemOdds.Roll("Beast kills", 1 / in, 1)), 0, true);
	}

	private static Map<Integer, String> names()
	{
		Map<Integer, String> names = new HashMap<>();
		names.put(SHIELD, "Beast shield");
		names.put(STAFF, "Beast staff");
		names.put(PET, "Beastling");
		names.put(TOP, "Beast top");
		names.put(TOKEN, "Beast token");
		names.put(SCALE, "Beast scale");
		return names;
	}

	private static Map<Integer, Long> prices()
	{
		Map<Integer, Long> prices = new HashMap<>();
		prices.put(SHIELD, 1_000L);
		prices.put(STAFF, 40_000L);
		prices.put(TOP, 8_000L);
		prices.put(SCALE, 500L);
		return prices;
	}

	private Bestiary.Target beast()
	{
		return analyst.bestiary().lookup("The Beast", 1);
	}

	private LootLookup lookup(Progress progress)
	{
		return analyst.monster(structure, progress, false, false, beast(), prices()).getLookup();
	}

	@Test
	public void uniquesAreRareSlotsThatAreNotPets()
	{
		// The pet never lands on the floor, the token is earned, the scale is commoner than 1 in 40.
		assertEquals(new HashSet<>(Arrays.asList(SHIELD, STAFF, TOP)), analyst.uniqueIds(beast()));
		assertEquals(new HashSet<>(Arrays.asList(SHIELD, STAFF, PET, TOP, TOKEN, SCALE)), analyst.slotIds(beast()));
	}

	@Test
	public void worthAKillByTheTableAndYourOwn()
	{
		Progress progress = new Progress();
		progress.setCounter("Beast kills", 200);
		progress.addOwned(SHIELD);
		progress.setObtained(SHIELD, 3, Collections.singletonList("Beast kills"));
		progress.setFullReadAt(1);
		progress.setCounter("Beast kills", 250);
		LootLookup lookup = lookup(progress);

		// 1/100 x 1,000 + 1/400 x 40,000 + 1/400 x 8,000.
		assertEquals(10 + 100 + 20, lookup.getTablePerKill(), 1e-9);
		assertEquals(3, lookup.getValued());
		// Three shields read at 200 kc, not the 250 the counter has moved on to since.
		assertEquals(3 * 1_000 / 200.0, lookup.getYoursPerKill(), 1e-9);
		assertEquals(15 / 130.0, lookup.ratio(), 1e-9);
		assertEquals(3, lookup.getUniques());
		assertEquals(2, lookup.getMissing());
		assertEquals(1 - 0.99 * Math.pow(1 - 1 / 400.0, 2), lookup.getUniqueChance(), 1e-12);
	}

	@Test
	public void withoutPricesOrAReadLogTheWorthIsLeftOut()
	{
		Progress progress = new Progress();
		progress.setCounter("Beast kills", 200);
		LootLookup unread = lookup(progress);
		assertTrue(unread.getTablePerKill() > 0);
		assertEquals(-1, unread.getYoursPerKill(), 0);
		LootLookup unpriced = analyst.monster(structure, progress, false, false, beast()).getLookup();
		assertEquals(-1, unpriced.getTablePerKill(), 0);
		assertEquals(3, unpriced.getUnpriced());
	}

	@Test
	public void theDryStreakCountsFromTheLastUniqueSeenInLoot()
	{
		Progress progress = new Progress();
		progress.setCounter("Beast kills", 200);
		progress.addOwned(SHIELD);
		progress.setFullReadAt(1);
		// Owning a unique from before Log Insight watched says nothing about when it came.
		assertEquals(LootLookup.DrySince.UNKNOWN, lookup(progress).getDrySince());
		assertEquals(-1, lookup(progress).getDryStreak());

		for (int i = 0; i < 7; i++)
		{
			progress.recordLoot("Beast kills", 5_000, 0, 1_000);
		}
		LootLookup watched = lookup(progress);
		assertEquals(LootLookup.DrySince.WATCHED, watched.getDrySince());
		assertEquals(7, watched.getDryStreak());
		assertEquals(7, watched.getWatchedKills());
		assertEquals(5_000, watched.getWatchedPerKill(), 1e-9);

		progress.recordLoot("Beast kills", 45_000, STAFF, 2_000);
		for (int i = 0; i < 4; i++)
		{
			progress.recordLoot("Beast kills", 5_000, 0, 3_000);
		}
		LootLookup seen = lookup(progress);
		assertEquals(LootLookup.DrySince.SEEN, seen.getDrySince());
		assertEquals(4, seen.getDryStreak());
		assertEquals("Beast staff", seen.getLastUnique());
		assertEquals(12, seen.getWatchedKills());
		assertEquals((11 * 5_000 + 45_000) / 12.0, seen.getWatchedPerKill(), 1e-9);
		double p = seen.getUniqueChance();
		assertEquals(Math.pow(1 - p, 4), seen.stillWithout(), 1e-12);
		assertEquals(1 - Math.pow(1 - p, 4), seen.haveOneByNow(), 1e-12);
	}

	@Test
	public void theDryStreakFromAnAnnouncedFindOrTheWholeCount()
	{
		Progress fresh = new Progress();
		fresh.setCounter("Beast kills", 50);
		fresh.setFullReadAt(1);
		LootLookup none = lookup(fresh);
		assertEquals(LootLookup.DrySince.NONE_OWNED, none.getDrySince());
		assertEquals(50, none.getDryStreak());

		Progress found = new Progress();
		found.setCounter("Beast kills", 200);
		found.addOwned(STAFF);
		found.setFullReadAt(1);
		found.addFind(new Progress.Find(STAFF, "Beast staff", "Beast", "Beast kills", 150, 1 / 400.0, 0.5, 1_000));
		// A pet's find is not a unique, so it never ends a streak.
		found.addFind(new Progress.Find(PET, "Beastling", "Beast", "Beast kills", 190, 1 / 1000.0, 0.5, 2_000));
		LootLookup find = lookup(found);
		assertEquals(LootLookup.DrySince.FIND, find.getDrySince());
		assertEquals(50, find.getDryStreak());
		assertEquals("Beast staff", find.getLastUnique());
	}

	@Test
	public void talliesSurviveASaveAndDamagedOnesAreDropped()
	{
		Gson gson = new Gson();
		Progress progress = new Progress();
		progress.recordLoot("Beast kills", 12_000, 0, 1_000);
		progress.recordLoot("Beast kills", 50_000, SHIELD, 2_000);
		progress.recordLoot(Progress.lootKey(null, "Kurask"), 3_000, 0, 3_000);
		Progress back = Progress.load(gson, gson.toJson(progress)).getValue();
		assertNotNull(back);
		LootTally tally = back.loot("Beast kills");
		assertEquals(2, tally.getKills());
		assertEquals(62_000, tally.getValue());
		assertEquals(SHIELD, tally.getLastUnique());
		assertEquals(0, tally.getSinceUnique());
		assertEquals(1_000, tally.getSince());
		assertEquals(1, back.loot("npc:kurask").getKills());
		// A copy is its own: loot added to it never reaches the original.
		Progress copy = back.copy();
		copy.recordLoot("Beast kills", 1, 0, 4_000);
		assertEquals(2, back.loot("Beast kills").getKills());

		String damaged = "{\"loot\":{\"Beast kills\":{\"k\":0,\"v\":5},\"Ok kills\":{\"k\":3,\"v\":9,\"s\":1}}}";
		SavedJson.Read<Progress> read = Progress.load(gson, damaged);
		assertNull(read.getValue().loot("Beast kills"));
		assertEquals(3, read.getValue().loot("Ok kills").getKills());
		// Older saves have no tallies at all.
		assertNull(Progress.load(gson, "{\"counters\":{\"Beast kills\":3}}").getValue().loot("Beast kills"));
		assertFalse(Progress.load(gson, "{}").isDamaged());
	}
}
