package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class OddsTest
{
	@Test
	public void oneSlotTakesItsDropRate()
	{
		// A single 1 in 100 slot takes 100 attempts on average.
		assertEquals(100, Odds.attemptsToAll(new double[]{100}, 0), 1.5);
		assertEquals(100, Odds.attemptsToNext(0.01, Double.POSITIVE_INFINITY), 1e-9);
	}

	@Test
	public void twoEqualSlotsTakeOneAndAHalfTimesAsLong()
	{
		// Waiting for both of two 1 in 100 slots: 100 * (1 + 1/2) on average.
		assertEquals(150, Odds.attemptsToAll(new double[]{100, 100}, 0), 2);
	}

	@Test
	public void fixedRewardSetsAFloor()
	{
		assertEquals(500, Odds.attemptsToAll(new double[]{10}, 500), 1);
		assertEquals(500, Odds.attemptsToAll(new double[0], 500), 1e-9);
		// A 1 in 10 roll racing a reward earned at 5 attempts usually ends a little sooner than 5.
		assertEquals(1 - Math.pow(0.9, 5), Odds.attemptsToNext(0.1, 5) * 0.1, 1e-9);
	}

	@Test
	public void finishingIsNeverQuickerThanTheNextSlot()
	{
		// One 1 in 3 slot: the next slot and the last are the same wait.
		assertEquals(Odds.attemptsToNext(1 / 3.0, Double.POSITIVE_INFINITY), Odds.attemptsToAll(new double[]{3}, 0), 0.05);
	}

	@Test
	public void piecesInOrderWaitOneAfterAnother()
	{
		// Three pieces that each take 100 attempts and only drop in order take 300 on average,
		// and run much tighter than one 300 attempt slot, so racing another slot ends sooner.
		assertEquals(300, Odds.attemptsToAll(0, new double[]{300}, new int[]{3}), 3);
		double chained = Odds.attemptsToAll(0, new double[]{300, 300}, new int[]{3, 1});
		double single = Odds.attemptsToAll(0, new double[]{300, 300}, new int[]{1, 1});
		assertEquals(450, single, 5);
		assertTrue(chained < single - 20 && chained > 300);
		// Erlang: three stages of mean 1 are all done by 3 with chance 1 - e^-3 (1 + 3 + 4.5).
		assertEquals(1 - Math.exp(-3) * 8.5, Odds.runDone(3, 3, 3), 1e-12);
	}

	@Test
	public void luckIsHalfWayWhenExactlyAverage()
	{
		assertEquals(0.5, Odds.luckPercentile(new double[]{0.5}, 0) + Odds.luckPercentile(new double[]{0.5}, 1) - 0.5, 1e-9);
		// Owning a slot nobody would expect yet is very lucky; missing a sure thing is very dry.
		assertTrue(Odds.luckPercentile(new double[]{0.02}, 1) > 0.98);
		assertTrue(Odds.luckPercentile(new double[]{0.98}, 0) < 0.02);
	}

	@Test
	public void luckPercentileOfSeveralSlots()
	{
		double[] chances = {0.5, 0.5};
		// Outcomes: 0 owned 25%, 1 owned 50%, 2 owned 25%.
		assertEquals(0.125, Odds.luckPercentile(chances, 0), 1e-9);
		assertEquals(0.5, Odds.luckPercentile(chances, 1), 1e-9);
		assertEquals(0.875, Odds.luckPercentile(chances, 2), 1e-9);
	}
}
