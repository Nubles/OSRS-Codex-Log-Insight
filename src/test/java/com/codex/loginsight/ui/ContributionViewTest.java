package com.codex.loginsight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.loginsight.core.Contribution;
import com.codex.loginsight.core.ContributionLog;
import com.codex.loginsight.core.ContributionReport;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** The contribution log's sentences and labels. */
public class ContributionViewTest
{
	private static final long MIN = 60_000L;

	@Test
	public void theDrySpellIsToldInExpectedUniques()
	{
		ContributionLog log = new ContributionLog().repair();
		long t = 0;
		for (int i = 1; i <= 43; i++)
		{
			log.killed(Contribution.Boss.NEX, i, 6000, 0, true, t += 5 * MIN);
		}
		String text = ContributionView.drySentence(ContributionReport.of(log, Contribution.Boss.NEX), t);
		assertTrue(text, text.startsWith("43 kills logged, no unique yet, worth 1 unique for you on average: 63% of"));
	}

	@Test
	public void theLatestKillSaysWhereItsShareComesFrom()
	{
		ContributionLog log = new ContributionLog().repair();
		ContributionLog.Kill trio = log.killed(Contribution.Boss.NEX, 3, 1800, 3000, true, 0);
		assertEquals("You dealt 1,800 of at least 5,400 (Nex 3,400 and her four generals at 500).",
			ContributionView.damageSentence(Contribution.Boss.NEX, trio));
		assertTrue(ContributionView.why(Contribution.Boss.NEX, trio).startsWith("Estimate:"));
		ContributionLog.Kill solo = log.killed(Contribution.Boss.NEX, 4, 5900, 0, true, MIN);
		assertEquals("You dealt 5,900 damage alone; the kill was yours.",
			ContributionView.damageSentence(Contribution.Boss.NEX, solo));
		assertNull(ContributionView.why(Contribution.Boss.NEX, solo));
		ContributionLog.Kill lost = log.killed(Contribution.Boss.CORP, -1, 800, 1500, false, 2 * MIN);
		assertEquals("You dealt 800 of 2,300 seen.", ContributionView.damageSentence(Contribution.Boss.CORP, lost));
		assertTrue(ContributionView.why(Contribution.Boss.CORP, lost).contains("another player"));
	}

	@Test
	public void eachUniqueSaysDryOrLucky()
	{
		ContributionLog log = new ContributionLog().repair();
		long t = 0;
		for (int i = 1; i <= 20; i++)
		{
			log.killed(Contribution.Boss.TITANS, i, 1200, 0, true, t += 3 * MIN);
		}
		log.found(ItemID.GIANTSOUL_AMULET_UNCHARGED, t + 1000);
		ContributionReport r = ContributionReport.of(log, Contribution.Boss.TITANS);
		assertEquals("0.8x the expected: behind", ContributionView.verdict(r.getOdds().get(0)));
		assertEquals("24% would have one by now", ContributionView.verdict(r.getOdds().get(1)));
		assertEquals("Your kills add up to 1.5 uniques on average; you have 1.", ContributionView.averageSentence(r));
	}

	@Test
	public void damageStaysShort()
	{
		assertEquals("870", ContributionView.shortDamage(870));
		assertEquals("1.8k", ContributionView.shortDamage(1840));
		assertEquals("2k", ContributionView.shortDamage(2000));
	}
}
