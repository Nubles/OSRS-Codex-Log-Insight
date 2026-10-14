package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class LuckTest
{
	@Test
	public void verdictBands()
	{
		assertEquals(Luck.Verdict.PARCHED, Luck.verdict(0.05));
		assertEquals(Luck.Verdict.DRY, Luck.verdict(0.25));
		assertEquals(Luck.Verdict.EVEN, Luck.verdict(0.5));
		assertEquals(Luck.Verdict.FORTUNATE, Luck.verdict(0.7));
		assertEquals(Luck.Verdict.BLESSED, Luck.verdict(0.95));
	}

	@Test
	public void expectedSumsTheChances()
	{
		Luck luck = Luck.of(new double[]{0.25, 0.5, 0.75}, 3);
		assertEquals(1.5, luck.getExpected(), 1e-9);
		assertEquals(3, luck.getSlots());
		assertEquals(Luck.Verdict.BLESSED, luck.verdict());
	}
}
