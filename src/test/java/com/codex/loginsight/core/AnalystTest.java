package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

public class AnalystTest
{
	private static final int PET = 1;
	private static final int HEAD = 2;
	private static final int SHIELD = 3;
	private static final int SPINE = 4;
	private static final int CLAW = 5;

	private final LogData data = data();
	private final LogStructure structure = new LogStructure(Collections.singletonList(new LogStructure.Tab("Bosses",
		Collections.singletonList(new LogStructure.Page("Bosses", "Beast", Arrays.asList(PET, HEAD, SHIELD, SPINE, CLAW))))),
		names());

	private static LogData data()
	{
		List<Activity.Drop> drops = Arrays.asList(
			new Activity.Drop(0, PET, 1000, 0),
			new Activity.Drop(0, HEAD, 100, 0),
			new Activity.Drop(0, SHIELD, 50, 0),
			new Activity.Drop(0, SPINE, 20, 0),
			new Activity.Drop(0, CLAW, 20, 4));
		Activity beast = new Activity(0, "Killing the beast", Activity.Category.COMBAT, 50, 40, 0, drops);
		Map<Integer, ItemOdds> odds = new HashMap<>();
		odds.put(PET, odds(1000));
		odds.put(HEAD, odds(100));
		odds.put(SHIELD, odds(50));
		return new LogData(Collections.singletonList(beast), Collections.singletonList("Beast kills"), odds,
			Collections.singleton(PET));
	}

	private static ItemOdds odds(double in)
	{
		return new ItemOdds(Collections.singletonList(new ItemOdds.Roll("Beast kills", 1 / in, 1)), 0);
	}

	private static Map<Integer, String> names()
	{
		Map<Integer, String> names = new HashMap<>();
		names.put(PET, "Beastling");
		names.put(HEAD, "Beast head");
		names.put(SHIELD, "Beast shield");
		names.put(SPINE, "Spine");
		names.put(CLAW, "Claw");
		return names;
	}

	@Test
	public void freshAccountHasNoLuckButHasEstimates()
	{
		Report report = new Analyst(data).analyse(structure, new Progress(), false, true, -1, -1);
		Report.PageReport page = report.getPages().get(0);
		assertEquals(0, page.getOwned());
		assertEquals(5, page.getTotal());
		assertNull(page.getLuck());
		assertTrue(Double.isFinite(page.getHoursToFinish()));
		// Claw only drops after the spine, so the first slot comes from the 1 in 20 spine or the others.
		Report.Hunt hunt = report.getHunts().get(0);
		assertEquals(SPINE, hunt.getNextItemId());
		assertEquals(5, hunt.getRemaining());
		double expected = 1 / (1 / 1000.0 + 1 / 100.0 + 1 / 50.0 + 1 / 20.0) / 50;
		assertEquals(expected, hunt.getHoursToNext(), expected * 0.02);
	}

	@Test
	public void luckComparesOwnedSlotsWithTheirOdds()
	{
		Progress lucky = new Progress();
		lucky.setCounter("Beast kills", 20);
		lucky.addOwned(PET);
		lucky.addOwned(HEAD);
		Report report = new Analyst(data).analyse(structure, lucky, false, true, -1, -1);
		Luck luck = report.getPages().get(0).getLuck();
		assertNotNull(luck);
		assertEquals(Luck.Verdict.BLESSED, luck.verdict());

		Progress dry = new Progress();
		dry.setCounter("Beast kills", 400);
		Luck parched = new Analyst(data).analyse(structure, dry, false, true, -1, -1).getPages().get(0).getLuck();
		assertEquals(Luck.Verdict.PARCHED, parched.verdict());
	}

	@Test
	public void driestSlotsAreListedMostOverdueFirst()
	{
		Progress dry = new Progress();
		dry.setCounter("Beast kills", 400);
		Report report = new Analyst(data).analyse(structure, dry, false, true, -1, -1);
		assertEquals(SHIELD, report.getDriest().get(0).getItemId());
		assertEquals(HEAD, report.getDriest().get(1).getItemId());
	}

	@Test
	public void ironRatesAreSlower()
	{
		Analyst analyst = new Analyst(data);
		double main = analyst.analyse(structure, new Progress(), false, true, -1, -1).getHoursLeft();
		double iron = analyst.analyse(structure, new Progress(), true, true, -1, -1).getHoursLeft();
		assertEquals(main * 50 / 40, iron, main * 0.01);
	}

	@Test
	public void ownPaceReplacesTheTypicalRate()
	{
		Progress progress = new Progress();
		Progress.Pace pace = progress.paceFor("Beast kills");
		long t = 1_000_000;
		for (int kc = 1; kc <= Analyst.PACE_SAMPLES + 2; kc++)
		{
			pace.record(kc, t);
			t += 36_000; // 100 kills an hour
		}
		Report report = new Analyst(data).analyse(structure, progress, false, true, -1, -1);
		Report.Hunt hunt = report.getHunts().get(0);
		assertTrue(hunt.isOwnPace());
		assertEquals(100, hunt.getPerHour(), 1);
		Report typical = new Analyst(data).analyse(structure, progress, false, false, -1, -1);
		assertEquals(50, typical.getHunts().get(0).getPerHour(), 1e-9);
	}

	@Test
	public void finishedPageNeedsNoTime()
	{
		Progress all = new Progress();
		for (int id = PET; id <= CLAW; id++)
		{
			all.addOwned(id);
		}
		Report report = new Analyst(data).analyse(structure, all, false, true, -1, -1);
		assertTrue(report.getPages().get(0).isComplete());
		assertEquals(0, report.getHoursLeft(), 1e-9);
		assertTrue(report.getHunts().isEmpty());
	}

	@Test
	public void rewardsBoughtWithPointsAddUp()
	{
		// Two shop rewards costing 100 and 200 points: the first comes at 100, both only at 300.
		List<Activity.Drop> drops = Arrays.asList(new Activity.Drop(0, 10, 100, 3), new Activity.Drop(0, 11, 200, 3));
		Activity shop = new Activity(0, "Earning points", Activity.Category.MINIGAME, 100, 100, 0, drops);
		LogData shopData = new LogData(Collections.singletonList(shop), Collections.emptyList(), new HashMap<>(),
			Collections.emptySet());
		LogStructure shopLog = new LogStructure(Collections.singletonList(new LogStructure.Tab("Minigames",
			Collections.singletonList(new LogStructure.Page("Minigames", "Shop", Arrays.asList(10, 11))))), new HashMap<>());
		Report.Hunt hunt = new Analyst(shopData).analyse(shopLog, new Progress(), false, true, -1, -1).getHunts().get(0);
		assertTrue(hunt.isNextEarned());
		assertEquals(1.0, hunt.getHoursToNext(), 1e-9);
		assertEquals(3.0, hunt.getHoursToFinish(), 1e-9);
	}

	@Test
	public void activitiesYouCannotDoYetComeLast()
	{
		List<Activity.Drop> easy = Collections.singletonList(new Activity.Drop(0, 20, 1000, 0));
		List<Activity.Drop> hard = Collections.singletonList(new Activity.Drop(1, 21, 10, 0));
		Activity cows = new Activity(0, "Killing cows", Activity.Category.COMBAT, 100, 100, 0, easy);
		Activity hydra = new Activity(1, "Killing hydra", Activity.Category.COMBAT, 100, 100, 0, hard,
			Collections.singletonMap("SLAYER", 95), Collections.singletonList("Heroes' Quest"));
		LogData both = new LogData(Arrays.asList(cows, hydra), Collections.emptyList(), new HashMap<>(), Collections.singleton(21));
		LogStructure log = new LogStructure(Collections.singletonList(new LogStructure.Tab("Bosses",
			Collections.singletonList(new LogStructure.Page("Bosses", "Mixed", Arrays.asList(20, 21))))), new HashMap<>());
		Analyst analyst = new Analyst(both);

		List<Report.Hunt> open = analyst.analyse(log, new Progress(), false, true, -1, -1).getHunts();
		assertEquals("Killing hydra", open.get(0).getName());
		assertEquals(0.1, open.get(0).getHoursToPet(), 1e-9);

		Unlocks low = new Unlocks(Collections.singletonMap("SLAYER", 80), Collections.emptySet());
		List<Report.Hunt> ranked = analyst.analyse(log, new Progress(), false, true, -1, -1, low).getHunts();
		assertEquals("Killing cows", ranked.get(0).getName());
		assertEquals("95 Slayer", ranked.get(1).getLockedBy());
		// The page's projection hunts the hydra pet where it drops, so it says what the account still needs.
		Report lowReport = analyst.analyse(log, new Progress(), false, true, -1, -1, low);
		assertEquals("95 Slayer", lowReport.getPages().get(0).getProjection().getLockedBy());
		assertNull(analyst.analyse(log, new Progress(), false, true, -1, -1).getPages().get(0).getProjection().getLockedBy());

		Unlocks noQuest = new Unlocks(Collections.singletonMap("SLAYER", 99), Collections.singleton("HEROES_QUEST"));
		assertEquals("Heroes' Quest", noQuest.missing(hydra));
		assertNull(new Unlocks(Collections.singletonMap("SLAYER", 99), Collections.emptySet()).missing(hydra));
		assertNull(Unlocks.UNKNOWN.missing(hydra));
	}

	@Test
	public void pageProjectionHuntsAnOpenSourceBeforeALockedFasterOne()
	{
		// The hydra slot also comes from cows, slowly: an account that cannot fight hydra still gets a curve it can play.
		List<Activity.Drop> easy = Arrays.asList(new Activity.Drop(0, 20, 1000, 0), new Activity.Drop(0, 21, 5000, 0));
		List<Activity.Drop> hard = Collections.singletonList(new Activity.Drop(1, 21, 10, 0));
		Activity cows = new Activity(0, "Killing cows", Activity.Category.COMBAT, 100, 100, 0, easy);
		Activity hydra = new Activity(1, "Killing hydra", Activity.Category.COMBAT, 100, 100, 0, hard,
			Collections.singletonMap("SLAYER", 95), Collections.emptyList());
		LogData both = new LogData(Arrays.asList(cows, hydra), Collections.emptyList(), new HashMap<>(), Collections.emptySet());
		LogStructure log = new LogStructure(Collections.singletonList(new LogStructure.Tab("Bosses",
			Collections.singletonList(new LogStructure.Page("Bosses", "Mixed", Arrays.asList(20, 21))))), new HashMap<>());
		Analyst analyst = new Analyst(both);
		Unlocks low = new Unlocks(Collections.singletonMap("SLAYER", 80), Collections.emptySet());
		Projection open = analyst.analyse(log, new Progress(), false, true, -1, -1, low).getPages().get(0).getProjection();
		assertNull(open.getLockedBy());
		assertTrue(open.getMedian() > 10);
		Projection any = analyst.analyse(log, new Progress(), false, true, -1, -1).getPages().get(0).getProjection();
		assertTrue(any.getMedian() < open.getMedian());
	}

	@Test
	public void pageNextSlotNamesASourceTheAccountCanPlay()
	{
		List<Activity.Drop> easy = Collections.singletonList(new Activity.Drop(0, 21, 5000, 0));
		List<Activity.Drop> hard = Collections.singletonList(new Activity.Drop(1, 21, 10, 0));
		Activity cows = new Activity(0, "Killing cows", Activity.Category.COMBAT, 100, 100, 0, easy);
		Activity hydra = new Activity(1, "Killing hydra", Activity.Category.COMBAT, 100, 100, 0, hard,
			Collections.singletonMap("SLAYER", 95), Collections.emptyList());
		LogData both = new LogData(Arrays.asList(cows, hydra), Collections.emptyList(), new HashMap<>(), Collections.emptySet());
		LogStructure log = new LogStructure(Collections.singletonList(new LogStructure.Tab("Bosses",
			Collections.singletonList(new LogStructure.Page("Bosses", "Mixed", Collections.singletonList(21))))), new HashMap<>());
		Analyst analyst = new Analyst(both);
		Unlocks low = new Unlocks(Collections.singletonMap("SLAYER", 80), Collections.emptySet());
		Report.PageReport locked = analyst.analyse(log, new Progress(), false, true, -1, -1, low).getPages().get(0);
		assertEquals("Killing cows", locked.getNextSource());
		assertEquals(50, locked.getHoursToNext(), 0.01);
		assertEquals(50, locked.getHoursToFinish(), 0.01);
		Report.PageReport any = analyst.analyse(log, new Progress(), false, true, -1, -1).getPages().get(0);
		assertEquals("Killing hydra", any.getNextSource());
	}

	@Test
	public void officialCountWinsWhenHigher()
	{
		Report report = new Analyst(data).analyse(structure, new Progress(), false, true, 300, 1700);
		assertEquals(300, report.owned());
		assertEquals(1700, report.total());
		assertTrue(report.needsFullRead());
	}

	@Test
	public void eachActivityBecomesAProspectForPlanning()
	{
		Report report = new Analyst(data).analyse(structure, new Progress(), false, true, -1, -1);
		assertEquals(1, report.getProspects().size());
		Prospect beast = report.getProspects().get(0);
		assertEquals(5, beast.getRemaining());
		assertEquals(50, beast.getPerHour(), 1e-9);
		// The next slot's wait the hunt shows and the prospect's chance of any slot agree.
		Report.Hunt hunt = report.getHunts().get(0);
		assertEquals(1 - Math.exp(-1), beast.chanceOfAny(hunt.getHoursToNext()), 0.03);
		// In a very long stretch every slot turns up.
		assertEquals(5, beast.expectedSlots(10_000), 1e-3);
	}

	@Test
	public void missingPetsArePlannedAtTheirSource()
	{
		Progress progress = new Progress();
		progress.addOwned(HEAD);
		Report report = new Analyst(data).analyse(structure, progress, false, true, -1, -1);
		assertEquals(1, report.getPets().size());
		PetPlan pet = report.getPets().get(0);
		assertEquals(PET, pet.getItemId());
		assertEquals("Beast", pet.getPage());
		assertEquals(Math.log(0.5) / Math.log(1 - 1 / 1000.0) / 50, pet.getMedian(), 1e-6);
		assertEquals(Math.log(0.1) / Math.log(1 - 1 / 1000.0) / 50, pet.getLikely(), 1e-6);
		// Half of all players see the pet after about 693 kills, by which the shield, spine and claw are near certain.
		assertEquals(3, pet.slotsAlongside(), 0.05);
		progress.addOwned(PET);
		assertTrue(new Analyst(data).analyse(structure, progress, false, true, -1, -1).getPets().isEmpty());
	}

	@Test
	public void aPetIsPlannedWhereTheAccountCanHuntIt()
	{
		// The pet drops quicker at hydra, but with 80 Slayer the plan points at cows.
		List<Activity.Drop> easy = Collections.singletonList(new Activity.Drop(0, 21, 1000, 0));
		List<Activity.Drop> hard = Collections.singletonList(new Activity.Drop(1, 21, 100, 0));
		Activity cows = new Activity(0, "Killing cows", Activity.Category.COMBAT, 100, 100, 0, easy);
		Activity hydra = new Activity(1, "Killing hydra", Activity.Category.COMBAT, 100, 100, 0, hard,
			Collections.singletonMap("SLAYER", 95), Collections.emptyList());
		LogData both = new LogData(Arrays.asList(cows, hydra), Collections.emptyList(), new HashMap<>(), Collections.singleton(21));
		LogStructure log = new LogStructure(Collections.singletonList(new LogStructure.Tab("Bosses",
			Collections.singletonList(new LogStructure.Page("Bosses", "Mixed", Collections.singletonList(21))))), new HashMap<>());
		Analyst analyst = new Analyst(both);
		assertEquals("Killing hydra", analyst.analyse(log, new Progress(), false, true, -1, -1).getPets().get(0).getSource());
		Unlocks low = new Unlocks(Collections.singletonMap("SLAYER", 80), Collections.emptySet());
		PetPlan pet = analyst.analyse(log, new Progress(), false, true, -1, -1, low).getPets().get(0);
		assertEquals("Killing cows", pet.getSource());
		assertNull(pet.getLockedBy());
	}

	@Test
	public void pagesProjectWhenTheyAreLikelyFinished()
	{
		Report report = new Analyst(data).analyse(structure, new Progress(), false, true, -1, -1);
		Report.PageReport page = report.getPages().get(0);
		Projection projection = page.getProjection();
		assertNotNull(projection);
		// The pet at 1 in 1000 sets the pace: the page is done no sooner than the pet alone would be.
		double pet = Math.log(0.5) / Math.log(1 - 1 / 1000.0) / 50;
		assertTrue(projection.getMedian() >= pet);
		assertTrue(projection.getMedian() < pet * 1.3);
		assertTrue(projection.getLikely() > projection.getMedian());
		// The mean the page shows lies between the median and the nine-in-ten mark for a right-skewed wait.
		assertTrue(page.getHoursToFinish() > projection.getMedian());
		assertTrue(page.getHoursToFinish() < projection.getLikely());

		Progress all = new Progress();
		for (int id = PET; id <= CLAW; id++)
		{
			all.addOwned(id);
		}
		assertNull(new Analyst(data).analyse(structure, all, false, true, -1, -1).getPages().get(0).getProjection());
	}

	// ---- Wanted ----

	@Test
	public void wantedSlotsAreReportedWithTheirMarksAndLeaveTheListWhenFound()
	{
		Progress p = new Progress();
		p.setCounter("Beast kills", 40);
		assertTrue(p.toggleWanted(HEAD));
		assertTrue(p.toggleWanted(PET));
		Report report = new Analyst(data).analyse(structure, p, false, true, -1, -1);
		assertEquals(2, report.getWanted().size());
		Report.ItemReport head = report.getWanted().get(0);
		assertEquals(HEAD, head.getItemId());
		assertEquals("Beast", head.getPage());
		assertTrue(report.isWanted(PET));
		// 1 in 100: half of players by 69 kills, nine in ten by 230.
		assertEquals(69, Wanted.countFor(head, 0.5));
		assertEquals(29, Wanted.moreUntil(head, 0.5));
		assertEquals(190, Wanted.moreUntil(head, 0.9));
		// Alone at 50 kills an hour, 100 kills are 2 hours on average: even odds after 2 ln 2 hours.
		assertEquals(2.0, head.getHours(), 1e-9);
		assertEquals(2 * Math.log(2), Wanted.evenChanceHours(head), 1e-9);
		assertEquals(1 - Math.exp(-1), Wanted.chanceWithin(head, 2), 1e-9);

		// Found: off the list, and a slot already owned cannot be wanted.
		p.addOwned(HEAD);
		assertEquals(Collections.singletonList(PET), p.getWanted());
		assertTrue(!p.toggleWanted(HEAD));
		report = new Analyst(data).analyse(structure, p, false, true, -1, -1);
		assertEquals(1, report.getWanted().size());
		// Clicking again takes it off.
		assertTrue(p.toggleWanted(PET));
		assertTrue(p.getWanted().isEmpty());
		// Attaching the session keeps the wanted list.
		assertEquals(1, new Analyst(data).analyse(structure, copyWanting(PET), false, true, -1, -1).withSession(null).getWanted().size());
	}

	private static Progress copyWanting(int id)
	{
		Progress p = new Progress();
		p.toggleWanted(id);
		return p.copy();
	}

	@Test
	public void theWantedListIsShortAndOlderSavesReadWithoutIt()
	{
		Progress p = new Progress();
		for (int id = 100; id < 100 + Progress.MAX_WANTED; id++)
		{
			assertTrue(p.toggleWanted(id));
		}
		assertTrue(!p.toggleWanted(999));
		assertEquals(Progress.MAX_WANTED, p.getWanted().size());
		com.google.gson.Gson gson = new com.google.gson.Gson();
		Progress back = gson.fromJson(gson.toJson(p), Progress.class);
		assertEquals(p.getWanted(), back.getWanted());
		Progress old = gson.fromJson("{\"owned\":[1],\"counters\":{}}", Progress.class);
		assertTrue(old.getWanted().isEmpty());
		assertTrue(old.toggleWanted(2));
		// A slot nobody can time still reports what it can.
		Report.ItemReport unknown = new Report.ItemReport(7, "Mystery", null, false, false, 0, null, -1, -1, null,
			Double.POSITIVE_INFINITY);
		assertEquals(-1, Wanted.countFor(unknown, 0.5));
		assertEquals(-1, Wanted.moreUntil(unknown, 0.5));
		assertEquals(-1, Wanted.evenChanceHours(unknown), 0);
		assertEquals(-1, Wanted.chanceWithin(unknown, 1), 0);
	}
}
