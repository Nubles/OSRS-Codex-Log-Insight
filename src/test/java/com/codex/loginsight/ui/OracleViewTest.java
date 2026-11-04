package com.codex.loginsight.ui;

import static org.junit.Assert.assertEquals;
import com.codex.loginsight.core.Oracle;
import org.junit.Test;

/** The oracle's sentences read the way players say them, with the suite's numbers. */
public class OracleViewTest
{
	@Test
	public void aFindReadsAtItsKill()
	{
		Oracle.Owned fang = new Oracle.Owned(12922, "Tanzanite fang", "Zulrah", "Zulrah kills", 1 / 512.25, 212, 1, 0.78,
			Oracle.Basis.AT_KILL, false);
		assertEquals("Tanzanite fang at 212 Zulrah kc: luckier than 78% of players.", OracleView.foundSentence(fang));
		Oracle.Owned late = new Oracle.Owned(12922, "Tanzanite fang", "Zulrah", "Zulrah kills", 1 / 512.25, 1400, 1, 0.07,
			Oracle.Basis.AT_KILL, false);
		assertEquals("Tanzanite fang at 1,400 Zulrah kc: drier than 93% of players.", OracleView.foundSentence(late));
		Oracle.Owned copies = new Oracle.Owned(12922, "Tanzanite fang", "Zulrah", "Zulrah kills", 1 / 512.25, 12_480, 31,
			0.71, Oracle.Basis.COPIES, false);
		assertEquals("31 x Tanzanite fang by 12k Zulrah kc: luckier than 71% of players.", OracleView.foundSentence(copies));
	}

	@Test
	public void aChaseReadsInTimesTheRate()
	{
		double dry = Math.pow(1 - 1 / 3000.0, 6900);
		Oracle.Chase jar = new Oracle.Chase(12936, "Jar of swamp", "Zulrah", "Zulrah kills", 1 / 3000.0, 6900, dry, 2.3,
			100, "Killing zulrah", 30, true, false, false);
		assertEquals("Jar of swamp: dry at 2.3x the rate, 90% of players have it by now.", OracleView.drySentence(jar));
		Oracle.Chase head = new Oracle.Chase(21907, "Vorkath's head", "Vorkath", "Vorkath kills", 1 / 50.0, 60, 0, 1.2,
			1, "Killing vorkath", 30, false, false, false);
		assertEquals("Vorkath's head: overdue, guaranteed by a count you have passed.", OracleView.drySentence(head));
	}

	@Test
	public void multiplesAndCounts()
	{
		assertEquals("0.41x", OracleView.times(0.414));
		assertEquals("0.4x", OracleView.times(0.40));
		assertEquals("<0.01x", OracleView.times(0.001));
		assertEquals("2.3x", OracleView.times(2.3));
		assertEquals("3x", OracleView.times(3.0));
		assertEquals("14x", OracleView.times(14.2));
		assertEquals("9,999", OracleView.kc(9999));
		assertEquals("12k", OracleView.kc(12_480));
	}
}
