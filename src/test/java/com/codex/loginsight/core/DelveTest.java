package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** The Doom of Mokhaiotl's per-level unique table, held against the wiki's own cumulative columns. */
public class DelveTest
{
	private static final int CLOTH = 0;
	private static final int EYE = 1;
	private static final int TREADS = 2;
	private static final int DOM = 3;

	/** "1 in N" over a run to this depth, as the wiki rounds it ("~1/111"). */
	private static void cumulative(int unique, int depth, double wikiOneIn)
	{
		double oneIn = 1 / Delve.runChance(unique, depth);
		// The wiki shows the cumulative chance rounded (or cut) to whole numbers.
		assertEquals("unique " + unique + " to delve " + depth, wikiOneIn, oneIn, Math.max(1.2, wikiOneIn * 0.012));
	}

	@Test
	public void perLevelRatesAreTheWikiTable()
	{
		assertEquals(2500, Delve.oneIn(CLOTH, 2));
		assertEquals(0, Delve.oneIn(EYE, 2));
		assertEquals(2000, Delve.oneIn(EYE, 3));
		assertEquals(0, Delve.oneIn(TREADS, 3));
		assertEquals(1350, Delve.oneIn(TREADS, 4));
		assertEquals(0, Delve.oneIn(DOM, 5));
		assertEquals(1000, Delve.oneIn(DOM, 6));
		assertEquals(750, Delve.oneIn(DOM, 7));
		assertEquals(500, Delve.oneIn(DOM, 8));
		assertEquals(250, Delve.oneIn(DOM, 9));
		assertEquals(540, Delve.oneIn(CLOTH, 9));
		// Delve 1 rolls no unique.
		for (int u = 0; u < 4; u++)
		{
			assertEquals(0, Delve.chance(u, 1), 0);
		}
	}

	@Test
	public void levelsPastNineRollAtTheDelveNineRate()
	{
		for (int u = 0; u < 4; u++)
		{
			assertEquals(Delve.chance(u, 9), Delve.chance(u, 10), 0);
			assertEquals(Delve.chance(u, 9), Delve.chance(u, 50), 0);
		}
	}

	@Test
	public void overallColumnIsTheSumOfTheThreeItems()
	{
		assertEquals(1 / 2500.0, Delve.overall(2), 1e-12);
		assertEquals(1 / 1000.0, Delve.overall(3), 1e-12);
		assertEquals(1 / 450.0, Delve.overall(4), 1e-12);
		assertEquals(1 / 270.0, Delve.overall(5), 1e-12);
		assertEquals(1 / 255.0, Delve.overall(6), 1e-12);
		assertEquals(1 / 240.0, Delve.overall(7), 1e-12);
		assertEquals(1 / 210.0, Delve.overall(8), 1e-12);
		assertEquals(1 / 180.0, Delve.overall(9), 1e-12);
	}

	@Test
	public void cumulativeChanceMatchesTheWikisCumulativeColumn()
	{
		cumulative(CLOTH, 3, 1111);
		cumulative(CLOTH, 4, 610);
		cumulative(CLOTH, 5, 348);
		cumulative(CLOTH, 6, 239);
		cumulative(CLOTH, 7, 179);
		cumulative(CLOTH, 8, 140);
		cumulative(CLOTH, 9, 111);
		cumulative(EYE, 4, 806);
		cumulative(EYE, 5, 404);
		cumulative(EYE, 6, 264);
		cumulative(EYE, 7, 193);
		cumulative(EYE, 8, 148);
		cumulative(EYE, 9, 116);
		cumulative(TREADS, 5, 506);
		cumulative(TREADS, 6, 305);
		cumulative(TREADS, 7, 214);
		cumulative(TREADS, 8, 160);
		cumulative(TREADS, 9, 123);
		cumulative(DOM, 7, 429);
		cumulative(DOM, 8, 231);
		cumulative(DOM, 9, 120);
	}

	@Test
	public void bankedLevelsAddUpAcrossRuns()
	{
		// Ten runs to delve 8: the chance of a cloth is the run chance ten times over.
		int[] rows = DelveReport.rows(8, 8, 8, 8, 8, 8, 8, 8, 8, 8);
		double expected = -Math.expm1(10 * Delve.runMissLog(CLOTH, 8));
		assertEquals(expected, -Math.expm1(Delve.missLog(CLOTH, rows)), 1e-12);
		assertEquals(10 * (1 / 2500.0 + 1 / 2000.0 + 1 / 1350.0 + 1 / 810.0 + 1 / 765.0 + 1 / 720.0 + 1 / 630.0),
			Delve.expected(CLOTH, rows), 1e-12);
		// Levels past 9 pool with 9.
		int[] deep = DelveReport.rows(12);
		assertEquals(4, deep[9]);
	}

	@Test
	public void runsAreReadFromLevelsAndEndings()
	{
		DelveLog log = new DelveLog().repair();
		long t = 1_000_000;
		for (int level = 1; level <= 8; level++)
		{
			assertTrue(log.levelCleared(level, t += 60_000));
			// The chat line and the end-of-level window both say it: counted once.
			assertFalse(log.levelCleared(level, t + 500));
		}
		assertTrue(log.claimed(t += 30_000));
		assertEquals(1, log.getBankedRuns());
		// A late line for the level just claimed is not a new run.
		assertFalse(log.levelCleared(8, t + 2_000));
		assertNull(log.getOpen());
		assertEquals(1, log.getBanked()[8]);
		assertEquals(0, log.getBanked()[9]);

		// Second run: dies on delve 5 after clearing 4.
		for (int level = 1; level <= 4; level++)
		{
			log.levelCleared(level, t += 60_000);
		}
		assertTrue(log.died(t += 40_000));
		// The second animation of the same death is not a second death.
		assertFalse(log.died(t + 1_000));
		assertEquals(1, log.getDiedRuns());
		assertEquals(1, log.getDeaths()[5]);
		assertEquals(1, log.getLost()[4]);
		// Lost levels never count towards the uniques.
		assertEquals(1, log.getBanked()[4]);

		// Third run is left at 3 by teleport; the next level 1 says so.
		for (int level = 1; level <= 3; level++)
		{
			log.levelCleared(level, t += 60_000);
		}
		log.levelCleared(1, t += 120_000);
		assertEquals(2, log.getBankedRuns());
		assertEquals(DelveLog.LEFT, log.getRecent().get(0).getOutcome());
		assertEquals(1, log.getOpen().getDeepest());
	}

	@Test
	public void aRunSeenOnlyFromItsMiddleCountsItsEarlierLevels()
	{
		DelveLog log = new DelveLog().repair();
		log.levelCleared(6, 10_000);
		log.claimed(20_000);
		int[] banked = log.getBanked();
		for (int level = 1; level <= 6; level++)
		{
			assertEquals(1, banked[level]);
		}
		// Untimed: no gap is known for levels before the one seen.
		assertEquals(0, log.getTimed()[6]);
	}

	@Test
	public void aDeathOnDelveOneIsADeathWithNothingCleared()
	{
		DelveLog log = new DelveLog().repair();
		log.died(5_000);
		assertEquals(1, log.getDeaths()[1]);
		assertEquals(1, log.getDiedRuns());
		assertEquals(0, log.getRecent().get(0).getDeepest());
	}

	@Test
	public void aFindIsReadAgainstTheBankedLevelsBeforeItsRun()
	{
		DelveLog log = new DelveLog().repair();
		long t = 0;
		for (int run = 0; run < 3; run++)
		{
			for (int level = 1; level <= 8; level++)
			{
				log.levelCleared(level, t += 60_000);
			}
			log.claimed(t += 10_000);
		}
		DelveLog.Find find = log.found(Delve.TREADS, t += 5_000);
		assertNotNull(find);
		assertEquals(8, find.getDepth());
		assertEquals(Delve.runChance(TREADS, 8), find.getChance(), 1e-12);
		// Two banked runs came before the one it dropped on.
		double sooner = -Math.expm1(2 * Delve.runMissLog(TREADS, 8));
		assertEquals(sooner, find.sooner(), 1e-12);
		double before = 1 - sooner;
		assertEquals(before * (1 - find.getChance() / 2), find.luckierThan(), 1e-12);
		// An item that is not a Doom unique is not a delve find.
		assertNull(log.found(4151, t));
	}

	@Test
	public void aFindLongAfterAnyRunHasNoLuck()
	{
		DelveLog log = new DelveLog().repair();
		log.levelCleared(4, 0);
		log.claimed(1_000);
		DelveLog.Find find = log.found(Delve.CLOTH, DelveLog.FIND_WINDOW_MS + 60_000);
		assertEquals(0, find.getDepth());
		assertEquals(-1, find.luckierThan(), 0);
	}

	@Test
	public void staleRunsAreLeft()
	{
		DelveLog log = new DelveLog().repair();
		log.levelCleared(1, 0);
		log.levelCleared(2, 60_000);
		assertFalse(log.settle(60_000 + DelveLog.STALE_MS - 1));
		assertTrue(log.settle(60_000 + DelveLog.STALE_MS + 1));
		assertEquals(1, log.getBankedRuns());
	}

	@Test
	public void oldOrDamagedSavesAreRepaired()
	{
		com.google.gson.Gson gson = new com.google.gson.Gson();
		DelveLog log = DelveLog.load(gson, "{\"since\":5,\"banked\":[0,3,3],\"bankedRuns\":3}").getValue();
		assertNotNull(log);
		assertEquals(DelveLog.ROWS, log.getBanked().length);
		assertEquals(3, log.getBanked()[2]);
		assertTrue(log.getRecent().isEmpty());
		// Round trip.
		log.levelCleared(1, 10);
		DelveLog back = DelveLog.load(gson, gson.toJson(log)).getValue();
		assertEquals(1, back.getOpen().getDeepest());
		assertNull(DelveLog.load(gson, "").getValue());
	}

	@Test
	public void reportReadsDrynessAndLuckFromBankedLevelsOnly()
	{
		DelveLog log = new DelveLog().repair();
		long t = 0;
		for (int run = 0; run < 20; run++)
		{
			for (int level = 1; level <= 8; level++)
			{
				log.levelCleared(level, t += 70_000);
			}
			log.claimed(t += 20_000);
		}
		// Deaths: the lost levels change nothing below.
		for (int level = 1; level <= 8; level++)
		{
			log.levelCleared(level, t += 70_000);
		}
		log.died(t += 40_000);
		Set<Integer> owned = new HashSet<>(Collections.singletonList(Delve.DOM));
		DelveReport report = DelveReport.of(log, owned, true);
		assertEquals(20, report.getBankedRuns());
		assertEquals(1, report.getDiedRuns());
		assertEquals(8, report.getUsual());
		DelveReport.Unique cloth = report.getUniques().get(0);
		assertFalse(cloth.isOwned());
		assertEquals(-Math.expm1(20 * Delve.runMissLog(CLOTH, 8)), cloth.getByNow(), 1e-12);
		// Dom is owned but never seen to drop: owned from before, left out of the luck.
		DelveReport.Unique dom = report.getUniques().get(3);
		assertTrue(dom.isOwnedBefore());
		assertNotNull(report.getLuck());
		assertEquals(3, report.getLuck().getSlots());
		assertEquals(0, report.getLuck().getOwned());
		assertTrue(report.isForNewSlots());
		assertTrue(report.isOwnPace());
	}

	@Test
	public void depthComparisonWeighsTimeAndDeaths()
	{
		// No history: typical pace, nobody dies, so deeper always pays until the rates stop rising at 9.
		DelveReport fresh = DelveReport.rates(Collections.emptySet());
		assertFalse(fresh.isOwnPace());
		DelveReport.Depth eight = fresh.depth(8);
		DelveReport.Depth nine = fresh.depth(9);
		assertEquals(8 * DelveReport.TYPICAL_LEVEL_SECONDS / 3600, eight.getHoursPerRun(), 1e-9);
		assertTrue(nine.getHoursToNext() < eight.getHoursToNext());
		assertEquals(1, eight.getReach(), 0);
		// The chance per run is any of the four missing uniques.
		double miss = 0;
		for (int u = 0; u < 4; u++)
		{
			miss += Delve.runMissLog(u, 8);
		}
		assertEquals(-Math.expm1(miss), eight.getPerRun(), 1e-12);

		// A player who dies on half their delve 9 fights: going to 9 costs more than it gives.
		DelveLog log = new DelveLog().repair();
		long t = 0;
		for (int run = 0; run < 20; run++)
		{
			for (int level = 1; level <= 8; level++)
			{
				log.levelCleared(level, t += 75_000);
			}
			if (run % 2 == 0)
			{
				log.levelCleared(9, t += 75_000);
				log.claimed(t += 1_000);
			}
			else
			{
				log.died(t += 75_000);
			}
		}
		DelveReport risky = DelveReport.of(log, Collections.emptySet(), true);
		assertTrue(risky.depth(9).getReach() < 0.6);
		assertTrue(risky.depth(9).getHoursToNext() > risky.depth(8).getHoursToNext());
		assertEquals(8, risky.getBest());
	}

	@Test
	public void onceEveryUniqueIsOwnedAnyCopyCounts()
	{
		Set<Integer> all = new HashSet<>();
		for (int id : Delve.UNIQUES)
		{
			all.add(id);
		}
		DelveReport report = DelveReport.of(new DelveLog().repair(), all, true);
		assertFalse(report.isForNewSlots());
		assertTrue(report.depth(9).getPerRun() > 0);
	}

	@Test
	public void chatAndWindowNameTheLevel()
	{
		assertEquals(4, DelveReader.levelMessage("Delve level: 4 duration: <col=ff0000>1:12.00</col>"));
		assertEquals(8, DelveReader.levelMessage("Delve level: 8 duration: 1:05.40"));
		assertEquals(12, DelveReader.levelMessage("Delve level: 8+ (12) duration: 0:58.20"));
		assertEquals(-1, DelveReader.levelMessage("Your Vorkath kill count is: 5"));
		assertEquals(2, DelveReader.levelTitle("Level 2 Complete!"));
		assertEquals(15, DelveReader.levelTitle("<col=ffffff>Level 15 Complete!</col>"));
		assertEquals(-1, DelveReader.levelTitle("Doom of Mokhaiotl"));
	}
}
