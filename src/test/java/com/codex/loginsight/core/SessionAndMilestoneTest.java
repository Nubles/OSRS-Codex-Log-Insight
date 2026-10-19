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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** The play session's tally, dry milestones, and a skilling pet's odds at a higher level. */
public class SessionAndMilestoneTest
{
	private static final int PET = 1;
	private static final int HEAD = 2;
	private static final int TOKEN = 3;
	private static final int OTHER = 9;

	private static ItemOdds odds(String label, double in, int guaranteedBy)
	{
		return new ItemOdds(Collections.singletonList(new ItemOdds.Roll(label, 1 / in, 1)), guaranteedBy);
	}

	private static LogData data()
	{
		Activity beast = new Activity(0, "Killing the beast", Activity.Category.COMBAT, 50, 40, 0, Arrays.asList(
			new Activity.Drop(0, PET, 1000, 0), new Activity.Drop(0, HEAD, 100, 0), new Activity.Drop(0, TOKEN, 50, 0)));
		Map<Integer, ItemOdds> odds = new HashMap<>();
		odds.put(PET, odds("Beast kills", 1000, 0));
		odds.put(HEAD, odds("Beast kills", 100, 0));
		odds.put(TOKEN, odds("Beast kills", 50, 60));
		odds.put(OTHER, odds("Imp kills", 10, 0));
		return new LogData(Collections.singletonList(beast), Arrays.asList("Beast kills", "Imp kills"), odds,
			Collections.singleton(PET));
	}

	private static Progress progress(int beastKills)
	{
		Progress p = new Progress();
		p.setCounter("Beast kills", beastKills);
		p.setCounter("Imp kills", 3);
		return p;
	}

	// ---- Session ----

	@Test
	public void aSessionCountsTheKillsAddedAndTheSlotsTheyGave()
	{
		LogData data = data();
		Progress p = progress(40);
		Session session = new Session(p.getOwned(), 1_000);
		// The first kill message sets where the counter started: one below it.
		session.observe("Beast kills", 41, true);
		p.setCounter("Beast kills", 41);
		for (int kc = 42; kc <= 140; kc++)
		{
			session.observe("Beast kills", kc, true);
			p.setCounter("Beast kills", kc);
		}
		p.addOwned(HEAD);
		p.addFind(new Progress.Find(HEAD, "Beast head", "Beast", "Beast kills", 120, 0.01, 0.5, 5_000));
		Session.Summary s = session.read(data, p, 1_000 + 80 * 60_000L);
		assertEquals(80 * 60_000L, s.getMillis());
		assertEquals(1, s.getGains().size());
		assertEquals(100, s.getGains().get(0).getAdded());
		assertEquals(100, s.addedTotal());
		assertEquals(1, s.getFinds().size());
		Luck luck = s.getLuck();
		assertNotNull(luck);
		// The pet, the head, and the token: guaranteed at 60, which this session passed.
		assertEquals(3, luck.getSlots());
		double expected = (1 - Math.pow(1 - 1 / 1000.0, 100)) + (1 - Math.pow(1 - 1 / 100.0, 100)) + 1;
		assertEquals(expected, luck.getExpected(), 1e-9);
		assertEquals(1, luck.getOwned());
	}

	@Test
	public void staleCountsAndPageReadsAreNotTonightsKills()
	{
		LogData data = data();
		Progress p = progress(100);
		Session session = new Session(p.getOwned(), 0);
		// Weeks since the count was saved: the first kill message tonight says 301, which is one kill.
		session.observe("Beast kills", 301, true);
		p.setCounter("Beast kills", 301);
		// A page opened for the first time this session only sets where that counter starts.
		session.observe("Imp kills", 50, false);
		p.setCounter("Imp kills", 50);
		Session.Summary s = session.read(data, p, 60_000);
		assertEquals(1, s.getGains().size());
		assertEquals("Beast kills", s.getGains().get(0).getLabel());
		assertEquals(1, s.getGains().get(0).getAdded());
	}

	@Test
	public void aLogReadMidSessionIsNotALuckyNight()
	{
		LogData data = data();
		Progress p = progress(10);
		Session session = new Session(p.getOwned(), 1_000);
		session.observe("Beast kills", 11, true);
		p.setCounter("Beast kills", 11);
		// Pressing Search reads in a head owned for years: owned now, but it did not drop tonight.
		p.addOwned(HEAD);
		Session.Summary s = session.read(data, p, 2_000);
		assertEquals(0, s.getLuck().getOwned());
		// Nor is it a slot tonight's kills could have given: it was owned all along.
		assertEquals(2, s.getLuck().getSlots());
		assertTrue(s.getFinds().isEmpty());
		// A find from before the session is not tonight's either.
		p.addFind(new Progress.Find(PET, "Beastling", "Beast", "Beast kills", 5, 0.001, 0.9, 500));
		assertTrue(session.read(data, p, 2_000).getFinds().isEmpty());
	}

	@Test
	public void aQuietSessionIsEmpty()
	{
		Session session = new Session(new HashSet<>(), 0);
		Session.Summary s = session.read(data(), progress(5), 1_000);
		assertTrue(s.isEmpty());
		assertNull(s.getLuck());
	}

	// ---- Dry milestones ----

	@Test
	public void theKillThatPassesAMilestoneIsNoticedOnce()
	{
		LogData data = data();
		Progress p = progress(0);
		// The head is 1 in 100: half of players have it by 69 kills, nine in ten by 230.
		assertEquals(69, DryWatch.countFor(1 / 100.0, 0.5));
		assertEquals(230, DryWatch.countFor(1 / 100.0, 0.9));
		// Half is only the average: nothing is said there.
		assertTrue(DryWatch.passed(data, p.getOwned(), p.getCounters(), "Beast kills", 68, 69).isEmpty());
		List<DryWatch.Milestone> at230 = DryWatch.passed(data, p.getOwned(), p.getCounters(), "Beast kills", 229, 230);
		assertEquals(1, at230.size());
		assertEquals(HEAD, at230.get(0).getItemId());
		assertEquals("9 in 10 players", at230.get(0).players());
		assertTrue(DryWatch.passed(data, p.getOwned(), p.getCounters(), "Beast kills", 230, 231).isEmpty());
	}

	@Test
	public void milestonesSkipOwnedGuaranteedAndFirstReadSlots()
	{
		LogData data = data();
		Progress p = progress(0);
		p.addOwned(HEAD);
		// Owned: nothing to say. The token is guaranteed by 60, so "dry" means nothing for it.
		assertTrue(DryWatch.passed(data, p.getOwned(), p.getCounters(), "Beast kills", 229, 230).isEmpty());
		assertTrue(DryWatch.passed(data, p.getOwned(), p.getCounters(), "Beast kills", 30, 140).isEmpty());
		// A count read for the first time, or going back, is not a kill.
		assertTrue(DryWatch.passed(data, new HashSet<>(), p.getCounters(), "Beast kills", 0, 5000).isEmpty());
		assertTrue(DryWatch.passed(data, new HashSet<>(), p.getCounters(), "Beast kills", 300, 200).isEmpty());
	}

	@Test
	public void aJumpPastSeveralMilestonesSaysTheFurthestFirst()
	{
		LogData data = data();
		List<DryWatch.Milestone> passed = DryWatch.passed(data, new HashSet<>(), progress(0).getCounters(), "Beast kills",
			60, 2400);
		// The head passes 9 in 10 (230) and 99 in 100 (459); the pet passes 9 in 10 (2,302).
		assertEquals(2, passed.size());
		assertEquals(HEAD, passed.get(0).getItemId());
		assertEquals("99 in 100 players", passed.get(0).players());
		assertEquals(PET, passed.get(1).getItemId());
		assertEquals("9 in 10 players", passed.get(1).players());
	}

	@Test
	public void theKillThatDropsTheSlotIsNotCalledDryOnIt()
	{
		LogData data = data();
		Progress p = progress(0);
		DryWatch.Queue queue = new DryWatch.Queue();
		// Kill 230 drops the head. The kill count line comes first, the "new item" line just after.
		queue.add("Beast kills", 229, 230, 1_000);
		// Checked at once (the old way) the head would still look missing.
		assertEquals(1, DryWatch.passed(data, p.getOwned(), p.getCounters(), "Beast kills", 229, 230).size());
		assertTrue(queue.due(1_000).isEmpty());
		p.addOwned(HEAD);
		assertTrue(queue.due(1_000 + DryWatch.SETTLE_TICKS - 1).isEmpty());
		List<DryWatch.Pending> due = queue.due(1_000 + DryWatch.SETTLE_TICKS);
		assertEquals(1, due.size());
		assertTrue(queue.isEmpty());
		DryWatch.Pending kill = due.get(0);
		// By the time it is checked the slot is owned, so nothing is said about it.
		assertTrue(DryWatch.passed(data, p.getOwned(), p.getCounters(), kill.getLabel(), kill.getBefore(), kill.getAfter()).isEmpty());

		// Without a drop, the same kill still gets its line, just a moment later.
		Progress dry = progress(0);
		queue.add("Beast kills", 229, 230, 2_000);
		DryWatch.Pending late = queue.due(2_000 + DryWatch.SETTLE_TICKS).get(0);
		assertEquals(1, DryWatch.passed(data, dry.getOwned(), dry.getCounters(), late.getLabel(), late.getBefore(), late.getAfter()).size());
	}

	@Test
	public void quickKillsOfOneCounterAreCheckedTogether()
	{
		DryWatch.Queue queue = new DryWatch.Queue();
		queue.add("Beast kills", 228, 229, 10);
		queue.add("Beast kills", 229, 230, 11);
		queue.add("Other kills", 5, 6, 11);
		assertTrue(queue.due(10 + DryWatch.SETTLE_TICKS).isEmpty());
		List<DryWatch.Pending> due = queue.due(11 + DryWatch.SETTLE_TICKS);
		assertEquals(2, due.size());
		// One check across both kills: 228 to 230 still passes 230 once.
		assertEquals(228, due.get(0).getBefore());
		assertEquals(230, due.get(0).getAfter());
		// A tick count that starts again (a new login) settles what waited rather than holding it for ever.
		queue.add("Beast kills", 300, 301, 50_000);
		assertEquals(1, queue.due(5).size());
	}

	@Test
	public void aCounterThatNeverRestsIsStillCheckedInGoodTime()
	{
		// A count every other tick for a minute: waiting for a quiet moment would wait for ever.
		DryWatch.Queue queue = new DryWatch.Queue();
		int checked = 0;
		for (int tick = 0; tick < 100; tick++)
		{
			if (tick % 2 == 0)
			{
				queue.add("Fast kills", tick / 2, tick / 2 + 1, tick);
			}
			for (DryWatch.Pending p : queue.due(tick))
			{
				checked++;
				assertTrue("checked by " + tick, tick <= 3 * DryWatch.SETTLE_TICKS + (p.getBefore() * 2));
			}
		}
		assertTrue("checked " + checked + " times", checked >= 100 / (3 * DryWatch.SETTLE_TICKS) - 1);
	}

	@Test
	public void milestonesWaitUntilTheLogHasBeenRead()
	{
		// Never searched: every slot would look missing.
		assertTrue(!DryWatch.ownedKnown(0, -1, 0));
		// Read, but the game counts more slots than were read in since.
		assertTrue(!DryWatch.ownedKnown(5, 800, 790));
		assertTrue(DryWatch.ownedKnown(5, 800, 800));
		// The game's count not sent yet (-1) is no reason to stay quiet once the log was read.
		assertTrue(DryWatch.ownedKnown(5, -1, 800));
	}

	// ---- Skilling pets at a higher level ----

	@Test
	public void aSkillingPetSaysWhatLevelsWouldDo()
	{
		Activity.Drop rocky = new Activity.Drop(0, 20663, 35_490, 0, "THIEVING", 36_490);
		PetPlan.LevelUp up = Analyst.levelUp(rocky, new Unlocks(Collections.singletonMap("THIEVING", 40), Collections.emptySet()));
		assertNotNull(up);
		assertEquals(40, up.getLevel());
		assertEquals("Thieving", up.skillName());
		assertEquals(1 / 34_015.0, up.getChance(), 1e-12);
		// A 99 is told what 200M experience does; one with 200M has nothing left to gain.
		Activity.Drop at99 = new Activity.Drop(0, 20663, 34_015, 0, "THIEVING", 36_490);
		PetPlan.LevelUp maxed = Analyst.levelUp(at99, new Unlocks(Collections.singletonMap("THIEVING", 99), Collections.emptySet()));
		assertTrue(maxed.isMaxedExperience());
		assertEquals(15 / 34_015.0, maxed.getChance(), 1e-12);
		Activity.Drop at200m = new Activity.Drop(0, 20663, 34_015 / 15.0, 0, "THIEVING", 36_490);
		assertNull(Analyst.levelUp(at200m, new Unlocks(Collections.singletonMap("THIEVING", 99), Collections.emptySet())));
		// Unknown levels and flat-rate pets say nothing.
		assertNull(Analyst.levelUp(rocky, Unlocks.UNKNOWN));
		assertNull(Analyst.levelUp(new Activity.Drop(0, PET, 1000, 0),
			new Unlocks(Collections.singletonMap("THIEVING", 40), Collections.emptySet())));
	}
}
