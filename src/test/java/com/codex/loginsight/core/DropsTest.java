package com.codex.loginsight.core;

import com.codex.loginsight.data.Activity;
import com.codex.loginsight.data.ItemOdds;
import com.codex.loginsight.data.LogData;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DropsTest
{
	private static final int PET = 1;
	private static final int HEAD = 2;
	private static final int SHIELD = 3;
	private static final int STACK = 4;

	private final LogData data = data();
	private final LogStructure structure = new LogStructure(Collections.singletonList(new LogStructure.Tab("Bosses",
		Collections.singletonList(new LogStructure.Page("Bosses", "Beast", Arrays.asList(PET, HEAD, SHIELD, STACK))))),
		names());

	private static LogData data()
	{
		List<Activity.Drop> drops = Arrays.asList(
			new Activity.Drop(0, PET, 1000, 0),
			new Activity.Drop(0, HEAD, 100, 0),
			new Activity.Drop(0, SHIELD, 50, 0),
			new Activity.Drop(0, STACK, 10, 0));
		Activity beast = new Activity(0, "Killing the beast", Activity.Category.COMBAT, 50, 40, 0, drops);
		Map<Integer, ItemOdds> odds = new HashMap<>();
		odds.put(PET, odds(1000, true));
		odds.put(HEAD, odds(100, true));
		odds.put(SHIELD, odds(50, true));
		// A stack: the log's count says nothing about how many rolls came good.
		odds.put(STACK, odds(10, false));
		return new LogData(Collections.singletonList(beast), Collections.singletonList("Beast kills"), odds,
			Collections.singleton(PET));
	}

	private static ItemOdds odds(double in, boolean countable)
	{
		return new ItemOdds(Collections.singletonList(new ItemOdds.Roll("Beast kills", 1 / in, 1)), 0, countable);
	}

	private static Map<Integer, String> names()
	{
		Map<Integer, String> names = new HashMap<>();
		names.put(PET, "Beastling");
		names.put(HEAD, "Beast head");
		names.put(SHIELD, "Beast shield");
		names.put(STACK, "Beast bones");
		return names;
	}

	private Progress greenPage(int heads, int shields)
	{
		Progress progress = new Progress();
		progress.setCounter("Beast kills", 1000);
		for (int id : new int[]{PET, HEAD, SHIELD, STACK})
		{
			progress.addOwned(id);
		}
		progress.setObtained(PET, 3);
		progress.setObtained(HEAD, heads);
		progress.setObtained(SHIELD, shields);
		progress.setObtained(STACK, 400);
		return progress;
	}

	@Test
	public void aGreenPageStillSaysHowLuckyItsDropsWere()
	{
		// 1,000 kills: 10 heads and 20 shields on average. Pets and stacks are not counted.
		Report.PageReport lucky = new Analyst(data).analyse(structure, greenPage(14, 26), false, true, -1, -1).getPages().get(0);
		Drops drops = lucky.getDrops();
		assertNotNull(drops);
		assertEquals(40, drops.getGot());
		assertEquals(30, drops.getExpected(), 1e-9);
		assertEquals(2, drops.getSlots());
		assertTrue(drops.getPercentile() > 0.9);

		Drops dry = new Analyst(data).analyse(structure, greenPage(4, 9), false, true, -1, -1).getPages().get(0).getDrops();
		assertTrue(dry.getPercentile() < 0.05);
	}

	@Test
	public void anOwnedSlotWithItsCountUnreadGivesNoReading()
	{
		Progress progress = greenPage(14, 26);
		Progress fresh = new Progress();
		fresh.setCounter("Beast kills", 1000);
		fresh.addOwned(HEAD);
		fresh.addOwned(SHIELD);
		fresh.setObtained(HEAD, 11);
		// The shield is owned but its count was never read: guessing one would read as dry.
		assertNull(new Analyst(data).analyse(structure, fresh, false, true, -1, -1).getPages().get(0).getDrops());
		assertNotNull(new Analyst(data).analyse(structure, progress, false, true, -1, -1).getDrops());
	}

	@Test
	public void theTileKnowsItsCopiesAndTheAverage()
	{
		Report.PageReport page = new Analyst(data).analyse(structure, greenPage(14, 26), false, true, -1, -1).getPages().get(0);
		for (Report.ItemReport item : page.getItems())
		{
			if (item.getItemId() == HEAD)
			{
				assertEquals(14, item.getObtained());
				assertEquals(10, item.getExpectedCount(), 1e-9);
			}
			if (item.getItemId() == PET || item.getItemId() == STACK)
			{
				assertEquals(-1, item.getExpectedCount(), 0);
			}
		}
	}

	@Test
	public void percentileMatchesThePoissonCount()
	{
		assertEquals(0.5, Odds.dropPercentile(0, 0), 0);
		// Mean 1: P(0) = 0.3679, P(1) = 0.3679; one drop sits at 0.3679 + 0.3679 / 2.
		assertEquals(0.3679 + 0.3679 / 2, Odds.dropPercentile(1, 1), 1e-3);
		// A huge mean never underflows to nonsense.
		double mid = Odds.dropPercentile(5000, 5000);
		assertTrue(mid > 0.45 && mid < 0.55);
	}

	@Test
	public void countsSurviveASaveAndAWholeLogRead()
	{
		Progress progress = greenPage(14, 26);
		Gson gson = new Gson();
		Progress back = Progress.load(gson, gson.toJson(progress)).getValue();
		assertEquals(14, back.obtained(HEAD));
		back.replaceOwned(new java.util.HashSet<>(Arrays.asList(HEAD, PET)));
		assertEquals(0, back.obtained(SHIELD));
		assertEquals(14, back.obtained(HEAD));
		assertEquals(14, back.copy().obtained(HEAD));
		// Nothing the game could not have saved is kept.
		assertTrue(!back.setObtained(HEAD, 0));
	}
}
