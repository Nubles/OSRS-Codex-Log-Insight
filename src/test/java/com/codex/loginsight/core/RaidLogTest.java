package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** The raid log: dryness in expected purples, purples put down to the right raid, and saves that keep. */
public class RaidLogTest
{
	private static final double EPS = 1e-9;
	private static final long MIN = 60_000L;

	@Test
	public void drynessAddsEachRaidsChance()
	{
		RaidLog log = new RaidLog().repair();
		long t = 0;
		for (int i = 1; i <= 10; i++)
		{
			log.completed(Raids.Kind.COX, Raids.NORMAL, i, 3, 30_000, 90_000, -1, t += 40 * MIN);
		}
		RaidReport report = RaidReport.of(log, Raids.Kind.COX);
		double each = 30_000 / 867_600.0;
		assertEquals(10, report.getDryRuns());
		assertEquals(10 * each, report.getDryExpected(), EPS);
		assertEquals(Math.pow(1 - each, 10), report.getDryWaiting(), EPS);
		assertEquals(10, report.getCounted());
		assertNotNull(report.getLuck());
		assertTrue(report.hasEstimates());
	}

	@Test
	public void aPurpleEndsTheSpellAndIsCountedOnce()
	{
		RaidLog log = new RaidLog().repair();
		long t = 0;
		for (int i = 1; i <= 4; i++)
		{
			log.completed(Raids.Kind.TOB, Raids.NORMAL, i, 1, -1, -1, -1, t += 30 * MIN);
		}
		// The chest, then the log's "new item" line for the same purple.
		RaidLog.Find find = log.found(ItemID.GHRAZI_RAPIER, t + MIN);
		assertNotNull(find);
		assertEquals(1 / 9.1, find.getChance(), EPS);
		assertEquals(4 / 9.1, find.getTook(), EPS);
		assertEquals(1 - Math.pow(1 - 1 / 9.1, 3), find.getSooner(), EPS);
		assertNull(log.found(ItemID.GHRAZI_RAPIER, t + 2 * MIN));
		RaidReport report = RaidReport.of(log, Raids.Kind.TOB);
		assertEquals(1, report.getPurples());
		assertEquals(0, report.getDryRuns());
		assertEquals(0, report.getDryExpected(), EPS);
		assertEquals(1, report.getDryWaiting(), EPS);
		assertEquals(4 / 9.1, report.getExpected(), EPS);
		assertEquals(ItemID.GHRAZI_RAPIER, report.latest().getPurple());
		log.completed(Raids.Kind.TOB, Raids.NORMAL, 5, 1, -1, -1, -1, t += 60 * MIN);
		assertEquals(1, RaidReport.of(log, Raids.Kind.TOB).getDryRuns());
	}

	@Test
	public void aPurpleLongAfterTheRaidStillEndsTheSpell()
	{
		RaidLog log = new RaidLog().repair();
		log.completed(Raids.Kind.TOB, Raids.HARD, 1, 1, -1, -1, -1, 0);
		RaidLog.Find find = log.found(ItemID.SCYTHE_OF_VITUR_UNCHARGED, 5 * 60 * MIN);
		assertNotNull(find);
		assertEquals(-1, find.getChance(), EPS);
		assertEquals(1 / 7.7, find.getTook(), EPS);
		RaidReport report = RaidReport.of(log, Raids.Kind.TOB);
		assertEquals(0, report.getDryRuns());
		assertEquals(0, report.latest().getPurple());
	}

	@Test
	public void otherItemsAreNotPurples()
	{
		RaidLog log = new RaidLog().repair();
		assertNull(log.found(ItemID.COINS, 0));
		assertTrue(log.isEmpty());
	}

	@Test
	public void tombsWaitForTheirPoints()
	{
		RaidLog log = new RaidLog().repair();
		RaidLog.Run run = log.completed(Raids.Kind.TOA, Raids.NORMAL, 10, 1, -1, -1, 400, 0);
		assertFalse(run.isCounted());
		assertEquals(0, RaidReport.of(log, Raids.Kind.TOA).getDryRuns());
		assertTrue(log.points(Raids.Kind.TOA, 37_000, MIN));
		assertTrue(run.isCounted());
		assertEquals(0.10, run.getChance(), EPS);
		assertEquals(37_000, run.getTotal());
		assertFalse(run.isEstimate());
		assertEquals(0.10, RaidReport.of(log, Raids.Kind.TOA).getDryExpected(), EPS);
		// Points too late, or twice, change nothing.
		assertFalse(log.points(Raids.Kind.TOA, 50_000, 2 * MIN));
	}

	@Test
	public void tombsWithoutPointsAreCountedUnknownOnceTheWindowPasses()
	{
		RaidLog log = new RaidLog().repair();
		log.completed(Raids.Kind.TOA, Raids.EXPERT, 10, 3, -1, -1, 500, 0);
		assertFalse(log.settle(10 * MIN));
		assertTrue(log.settle(31 * MIN));
		RaidReport report = RaidReport.of(log, Raids.Kind.TOA);
		assertEquals(1, report.getDryRuns());
		assertEquals(1, report.getDryUnknown());
		assertEquals(0, report.getCounted());
	}

	@Test
	public void theNextRaidCountsAnOpenOne()
	{
		RaidLog log = new RaidLog().repair();
		log.completed(Raids.Kind.TOA, Raids.NORMAL, 10, 2, -1, -1, 300, 0);
		log.completed(Raids.Kind.TOA, Raids.NORMAL, 11, 2, 9_000, -1, 300, 25 * MIN);
		RaidReport report = RaidReport.of(log, Raids.Kind.TOA);
		assertEquals(2, report.getDryRuns());
		assertEquals(1, report.getDryUnknown());
		assertEquals(9_000 / 450_000.0, report.getDryExpected(), EPS);
	}

	@Test
	public void theSameLineTwiceIsOneRaid()
	{
		RaidLog log = new RaidLog().repair();
		log.completed(Raids.Kind.TOA, Raids.NORMAL, 10, 2, -1, -1, 300, 0);
		log.completed(Raids.Kind.TOA, Raids.NORMAL, 10, 2, -1, -1, 300, 1000);
		assertEquals(1, RaidReport.of(log, Raids.Kind.TOA).getRuns());
	}

	@Test
	public void theLogSavesAndReadsBack()
	{
		Gson gson = new Gson();
		RaidLog log = new RaidLog().repair();
		log.completed(Raids.Kind.COX, Raids.CHALLENGE, 7, 1, 40_000, 40_000, -1, 0);
		log.found(ItemID.TWISTED_BOW, MIN);
		log.completed(Raids.Kind.COX, Raids.NORMAL, 8, 1, 35_000, 35_000, -1, 60 * MIN);
		SavedJson.Read<RaidLog> read = RaidLog.load(gson, gson.toJson(log));
		assertFalse(read.isDamaged());
		RaidReport report = RaidReport.of(read.getValue(), Raids.Kind.COX);
		assertEquals(2, report.getRuns());
		assertEquals(1, report.getPurples());
		assertEquals(ItemID.TWISTED_BOW, report.getLastPurple());
		assertEquals(35_000 / 867_600.0, report.getDryExpected(), EPS);
		assertEquals(Raids.CHALLENGE, report.getRecent().get(1).getMode());
	}

	@Test
	public void damagedSavesKeepWhatReads()
	{
		Gson gson = new Gson();
		SavedJson.Read<RaidLog> read = RaidLog.load(gson, "{\"tracks\":[{\"kind\":\"tob\",\"runs\":3,\"dryExpected\":\"x\"},{\"kind\":\"nope\"}]}");
		assertTrue(read.isDamaged());
		RaidLog log = read.getValue();
		assertNotNull(log);
		assertEquals(3, RaidReport.of(log, Raids.Kind.TOB).getRuns());
		assertTrue(RaidLog.load(gson, null).getValue() == null);
	}
}
