package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.loginsight.data.Activity;
import com.codex.loginsight.data.ItemOdds;
import com.codex.loginsight.data.LogData;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** The numbers on a monster's Log view, worked by hand on a small made-up table. */
public class MonsterReportTest
{
	private static final int SHIELD = 1;
	private static final int STAFF = 2;
	private static final int PET = 3;
	private static final int TOP = 4;
	private static final int TOKEN = 5;
	private static final int TOOTH = 6;
	private static final int ELSEWHERE = 7;

	private final LogData data = data();
	private final Analyst analyst = new Analyst(data);
	private final LogStructure structure = new LogStructure(Collections.singletonList(new LogStructure.Tab("Bosses", Arrays.asList(
		new LogStructure.Page("Bosses", "Beast", Arrays.asList(SHIELD, STAFF, PET, TOP, TOKEN)),
		new LogStructure.Page("Bosses", "Slayer", Arrays.asList(TOOTH, ELSEWHERE))))), names());

	private static LogData data()
	{
		// TOP only drops once STAFF is owned; TOKEN is earned outright after 50 kills.
		List<Activity.Drop> beast = Arrays.asList(
			new Activity.Drop(0, SHIELD, 100, 0),
			new Activity.Drop(0, STAFF, 400, 0),
			new Activity.Drop(0, TOP, 400, 4),
			new Activity.Drop(0, PET, 1000, 0),
			new Activity.Drop(0, TOKEN, 50, 1));
		List<Activity.Drop> grunts = Collections.singletonList(new Activity.Drop(1, TOOTH, 200, 0));
		Map<Integer, ItemOdds> odds = new HashMap<>();
		odds.put(SHIELD, odds(100));
		odds.put(STAFF, odds(400));
		odds.put(PET, odds(1000));
		return new LogData(Arrays.asList(
			new Activity(0, "Killing the beast", Activity.Category.COMBAT, 30, 25, 0, beast),
			new Activity(1, "Killing grunts", Activity.Category.COMBAT, 120, 120, 0, grunts)),
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
		names.put(TOOTH, "Grunt tooth");
		names.put(ELSEWHERE, "Something else");
		return names;
	}

	private static Report.ItemReport item(MonsterReport report, int id)
	{
		for (Report.ItemReport item : report.getItems())
		{
			if (item.getItemId() == id)
			{
				return item;
			}
		}
		throw new AssertionError("no slot " + id);
	}

	@Test
	public void aBossAtItsKillCount()
	{
		Progress progress = new Progress();
		progress.setCounter("Beast kills", 200);
		progress.addOwned(SHIELD);
		progress.setFullReadAt(1);
		Bestiary.Target target = analyst.bestiary().lookup("The Beast", 1);
		assertEquals("Beast kills", target.getLabel());
		MonsterReport report = analyst.monster(structure, progress, false, false, target);

		assertEquals("Beast", report.getPage());
		assertEquals(Arrays.asList(SHIELD, STAFF, PET, TOP, TOKEN), ids(report));
		assertEquals(1, report.owned());
		assertEquals(200, report.getKills());
		// Odds on the counter come first: 1/400 a kill, and 1 - (399/400)^200 of players have it by 200 kc.
		assertEquals(1 / 400.0, item(report, STAFF).getChance(), 1e-12);
		assertEquals(Math.pow(1 - 1 / 400.0, 200), item(report, STAFF).getDryChance(), 1e-9);
		assertEquals(1 - Math.pow(1 - 1 / 400.0, 200), MonsterReport.byNow(item(report, STAFF)), 1e-9);
		// Slots with no odds on the counter take the activity's rate.
		assertEquals(1 / 400.0, item(report, TOP).getChance(), 1e-12);
		assertEquals(0, item(report, TOKEN).getChance(), 0);
		assertEquals("Killing the beast", report.getBasis());

		// The next slot races the staff and pet (the top waits for the staff) against the token at 50 kills.
		double sum = 1 / 400.0 + 1 / 1000.0;
		assertEquals(Odds.attemptsToNext(sum, 50), report.getKillsToNext(), 1e-9);
		assertEquals(1, report.getLocked());
		assertEquals(TOKEN, report.getNextItem());
		assertEquals(report.getKillsToNext() / 30, report.getHoursToNext(), 1e-9);

		// Luck over every slot with a chance by now: the shield, staff, pet and top.
		double[] chances = {1 - Math.pow(0.99, 200), 1 - Math.pow(1 - 1 / 400.0, 200), 1 - Math.pow(0.999, 200),
			1 - Math.pow(1 - 1 / 400.0, 200)};
		assertEquals(Luck.of(chances, 1).getPercentile(), report.getLuck().getPercentile(), 1e-9);
		assertEquals(4, report.getLuck().getSlots());
	}

	@Test
	public void ownedSlotsUnlockTheOnesAfterThem()
	{
		Progress progress = new Progress();
		progress.setCounter("Beast kills", 10);
		progress.addOwned(STAFF);
		progress.addOwned(TOKEN);
		MonsterReport report = analyst.monster(structure, progress, false, false, analyst.bestiary().lookup("Beast", 1));
		assertEquals(0, report.getLocked());
		double sum = 1 / 100.0 + 1 / 400.0 + 1 / 1000.0;
		assertEquals(1 / sum, report.getKillsToNext(), 1e-9);
		assertEquals(SHIELD, report.getNextItem());
		// What you own is not known until the page or the log is read, so there is no luck to tell yet.
		assertTrue(!report.isRead());
		assertNull(report.getLuck());
	}

	@Test
	public void aMonsterWithoutAKillCount()
	{
		Progress progress = new Progress();
		progress.setFullReadAt(1);
		Bestiary.Target grunt = new Bestiary.Target("Grunt", null, Collections.singletonList(1));
		MonsterReport report = analyst.monster(structure, progress, false, false, grunt);
		assertEquals(Collections.singletonList(TOOTH), ids(report));
		assertEquals("Slayer", report.getPage());
		assertEquals(-1, report.getKills());
		assertEquals(-1, item(report, TOOTH).getDryChance(), 0);
		assertEquals(200, report.getKillsToNext(), 1e-9);
		assertEquals(200 / 120.0, report.getHoursToNext(), 1e-9);
		assertNull(report.getLuck());
	}

	@Test
	public void aFinishedMonsterHasNoNextSlot()
	{
		Progress progress = new Progress();
		progress.setCounter("Beast kills", 900);
		for (int id : new int[]{SHIELD, STAFF, PET, TOP, TOKEN})
		{
			progress.addOwned(id);
		}
		progress.setFullReadAt(1);
		MonsterReport report = analyst.monster(structure, progress, false, false, analyst.bestiary().lookup("Beast", 1));
		assertTrue(report.isComplete());
		assertTrue(Double.isInfinite(report.getKillsToNext()));
		assertEquals(-1, report.getNextItem());
	}

	@Test
	public void nothingBeforeTheLogIsKnown()
	{
		assertNull(analyst.monster(LogStructure.empty(), new Progress(), false, false, analyst.bestiary().lookup("Beast", 1)));
		assertNull(analyst.monster(structure, new Progress(), false, false, null));
	}

	private static List<Integer> ids(MonsterReport report)
	{
		Integer[] ids = new Integer[report.getItems().size()];
		for (int i = 0; i < ids.length; i++)
		{
			ids[i] = report.getItems().get(i).getItemId();
		}
		return Arrays.asList(ids);
	}
}
