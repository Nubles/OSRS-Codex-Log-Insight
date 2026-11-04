package com.codex.loginsight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.codex.loginsight.core.DelveLog;
import com.codex.loginsight.core.DelveReport;
import java.util.Collections;
import org.junit.Test;

/** The sentences under the delve comparison. */
public class DelveViewTest
{
	@Test
	public void theDoomsPageIsFoundByName()
	{
		assertTrue(DelveView.isDelvePage("Doom of Mokhaiotl"));
		assertFalse(DelveView.isDelvePage("Zulrah"));
		assertFalse(DelveView.isDelvePage(null));
	}

	@Test
	public void columnLabelsStayShort()
	{
		assertEquals("6.9h", DelveView.shortHours(6.88));
		assertEquals("14h", DelveView.shortHours(14.2));
		assertEquals("1,250h", DelveView.shortHours(1250));
		assertEquals("45m", DelveView.shortHours(0.75));
		assertEquals("-", DelveView.shortHours(Double.POSITIVE_INFINITY));
	}

	@Test
	public void withNoRunsTheComparisonStartsAtDelveEightAndSaysWhosePace()
	{
		DelveReport fresh = DelveReport.rates(Collections.emptySet());
		String verdict = DelveView.verdict(fresh);
		assertTrue(verdict, verdict.startsWith("Cashing out at delve 8: next new slot in ~"));
		assertTrue(verdict, verdict.contains("One deeper pays"));
		assertTrue(DelveView.basis(fresh).startsWith("A typical 6 runs of delve 1 to 8 an hour (wiki)"));
	}

	@Test
	public void deathsDeeperDownSayCashOut()
	{
		DelveLog log = new DelveLog().repair();
		long t = 0;
		for (int run = 0; run < 30; run++)
		{
			boolean dies = run % 3 != 0;
			for (int level = 1; level <= 9; level++)
			{
				log.levelCleared(level, t += 70_000);
			}
			if (dies)
			{
				log.died(t += 50_000);
			}
			else
			{
				log.claimed(t += 10_000);
			}
		}
		DelveReport report = DelveReport.of(log, Collections.emptySet(), true);
		assertEquals(9, report.getUsual());
		String verdict = DelveView.verdict(report);
		assertTrue(verdict, verdict.startsWith("You usually cash out at delve 9"));
		assertTrue(verdict, verdict.contains("Going on to 10 costs more than it adds"));
		assertTrue(DelveView.basis(report), DelveView.basis(report).contains("your 20 deaths in 290 fights"));
	}
}
