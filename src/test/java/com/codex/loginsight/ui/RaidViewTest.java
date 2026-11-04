package com.codex.loginsight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.loginsight.core.RaidLog;
import com.codex.loginsight.core.RaidReport;
import com.codex.loginsight.core.Raids;
import java.util.List;
import org.junit.Test;

/** The raid log's sentences and labels. */
public class RaidViewTest
{
	private static final long MIN = 60_000L;

	@Test
	public void theDrySpellIsToldInExpectedPurples()
	{
		RaidLog log = new RaidLog().repair();
		long t = 0;
		for (int i = 1; i <= 9; i++)
		{
			log.completed(Raids.Kind.TOB, Raids.NORMAL, i, 1, -1, -1, -1, t += 30 * MIN);
		}
		log.completed(Raids.Kind.TOB, Raids.ENTRY, 10, 1, -1, -1, -1, t += 30 * MIN);
		String text = RaidView.drySentence(RaidReport.of(log, Raids.Kind.TOB), t);
		assertTrue(text, text.startsWith("10 raids logged, no purple yet, worth 0.99 purples on average: "));
		assertTrue(text, text.endsWith("1 raid without a chance is left out."));
	}

	@Test
	public void aPurpleSaysHowLongItTook()
	{
		RaidLog log = new RaidLog().repair();
		log.completed(Raids.Kind.TOA, Raids.NORMAL, 1, 1, 37_000, 37_000, 400, 0);
		log.found(net.runelite.api.gameval.ItemID.LIGHTBEARER, MIN);
		String text = RaidView.drySentence(RaidReport.of(log, Raids.Kind.TOA), 3 * 3_600_000L);
		assertEquals("A purple on your latest raid. Your last came 2h ago after 0.1 expected purples.", text);
	}

	@Test
	public void theLatestRaidSaysItsPointsAndLevel()
	{
		RaidLog log = new RaidLog().repair();
		RaidLog.Run run = log.completed(Raids.Kind.TOA, Raids.EXPERT, 5, 1, 37_000, 37_000, 400, 0);
		List<String> facts = RaidView.facts(Raids.Kind.TOA, run);
		assertEquals("Raid level 400 (Expert Mode): 1% per 3,700 points.", facts.get(0));
		assertEquals("Your points: 37,000.", facts.get(1));
		assertNull(RaidView.why(Raids.Kind.TOA, run));
		RaidLog.Run trio = log.completed(Raids.Kind.COX, Raids.NORMAL, 9, 3, 30_000, 90_000, -1, 0);
		assertEquals("Your points: 30,000 of 90,000 (33%).", RaidView.facts(Raids.Kind.COX, trio).get(0));
		assertTrue(RaidView.why(Raids.Kind.COX, trio).startsWith("Estimate:"));
		RaidLog.Run entry = log.completed(Raids.Kind.TOB, Raids.ENTRY, 3, 2, -1, -1, -1, 0);
		assertTrue(RaidView.why(Raids.Kind.TOB, entry).contains("no Entry Mode"));
	}

	@Test
	public void labelsStayShort()
	{
		assertEquals("870", RaidView.shortPoints(870));
		assertEquals("31.2k", RaidView.shortPoints(31_240));
		assertEquals("30k", RaidView.shortPoints(30_000));
		assertEquals("1.2m", RaidView.shortPoints(1_240_000));
		assertEquals(0.05, RaidView.niceCeiling(0.047), 1e-9);
		assertEquals(0.6, RaidView.niceCeiling(0.55), 1e-9);
	}
}
