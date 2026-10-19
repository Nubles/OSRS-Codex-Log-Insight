package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.loginsight.data.Activity;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class ProspectTest
{
	private static Prospect prospect(double perHour, double[] means, int[] stages, double[] exact)
	{
		return new Prospect("Killing the beast", Activity.Category.COMBAT, perHour, 0, false, null, -1, null, means, stages,
			exact, Collections.emptyList());
	}

	@Test
	public void oneRolledSlotFollowsTheGeometricOdds()
	{
		Prospect p = prospect(50, new double[]{100}, new int[]{1}, new double[0]);
		// Two hours is 100 kills at 1 in 100.
		double owned = 1 - Math.pow(0.99, 100);
		assertEquals(owned, p.expectedSlots(2), 1e-9);
		assertEquals(owned, p.chanceOfAny(2), 1e-9);
		assertEquals(owned, p.chanceOfAll(2), 1e-9);
		// Half of all players have it by ln 2 / -ln 0.99 kills.
		assertEquals(Math.log(0.5) / Math.log(0.99) / 50, p.hoursToAll(0.5), 1e-6);
		assertEquals(Math.log(0.1) / Math.log(0.99) / 50, p.hoursToAll(0.9), 1e-6);
		assertEquals(0, p.expectedSlots(0), 0);
	}

	@Test
	public void earnedSlotsArePaidForCheapestFirst()
	{
		Prospect p = prospect(10, new double[0], new int[0], new double[]{50, 30});
		assertEquals(0, p.expectedSlots(2.9), 0);
		assertEquals(1, p.expectedSlots(3), 0);
		assertEquals(1, p.expectedSlots(7.9), 0);
		assertEquals(2, p.expectedSlots(8), 0);
		assertEquals(0, p.chanceOfAny(2), 0);
		assertEquals(1, p.chanceOfAny(3), 0);
		assertEquals(0, p.chanceOfAll(7.9), 0);
		assertEquals(1, p.chanceOfAll(8), 0);
		assertEquals(8, p.hoursToAll(0.5), 1e-6);
	}

	@Test
	public void piecesThatDropInOrderWaitOneAfterAnother()
	{
		// Two pieces sharing a 40-kill run: 20 kills each on average.
		Prospect p = prospect(20, new double[]{40}, new int[]{2}, new double[0]);
		assertEquals(2, p.getRemaining());
		double x = 1;
		double first = 1 - Math.exp(-x);
		double second = 1 - Math.exp(-x) * (1 + x);
		assertEquals(first + second, p.expectedSlots(1), 1e-9);
		// Only the first piece can be the first new slot.
		assertEquals(first, p.chanceOfAny(1), 1e-9);
		assertEquals(second, p.chanceOfAll(1), 1e-9);
		assertEquals(2, p.expectedSlots(1000), 1e-6);
	}

	@Test
	public void setupHoursComeBeforeAnyAttempt()
	{
		Prospect p = new Prospect("Diving", Activity.Category.OTHER, 10, 2, false, null, -1, null, new double[]{10},
			new int[]{1}, new double[0], Collections.emptyList());
		assertEquals(0, p.expectedSlots(2), 0);
		assertEquals(1 - Math.pow(0.9, 10), p.expectedSlots(3), 1e-9);
		assertEquals(2 + Math.log(0.5) / Math.log(0.9) / 10, p.hoursToAll(0.5), 1e-6);
	}

	@Test
	public void projectionOfOnePartMatchesItsOwnQuantiles()
	{
		Prospect p = prospect(50, new double[]{100, 400}, new int[]{1, 1}, new double[0]);
		Projection projection = Projection.of(Collections.singletonList(p), 0);
		assertNotNull(projection);
		assertEquals(p.hoursToAll(0.5), projection.getMedian(), 1e-9);
		assertEquals(p.hoursToAll(0.9), projection.getLikely(), 1e-9);
		assertEquals(0.5, projection.chanceBy(projection.getMedian()), 0.02);
		double previous = -1;
		for (double c : projection.getChance())
		{
			assertTrue(c >= previous);
			previous = c;
		}
	}

	@Test
	public void projectionAddsUpActivitiesHuntedInTurn()
	{
		// Two one-hour exponential waits in a row are an Erlang wait of two: half done by 1.678 hours.
		Prospect a = prospect(1000, new double[]{1000}, new int[]{1}, new double[0]);
		Prospect b = prospect(500, new double[]{500}, new int[]{1}, new double[0]);
		Projection projection = Projection.of(Arrays.asList(a, b), 0);
		assertNotNull(projection);
		assertEquals(1.678, projection.getMedian(), 1.678 * 0.02);
		assertEquals(3.890, projection.getLikely(), 3.890 * 0.02);
		assertEquals(1 - Math.exp(-2) * 3, projection.chanceBy(2), 0.02);
	}

	@Test
	public void nothingToProjectGivesNoProjection()
	{
		assertNull(Projection.of(Collections.emptyList(), 2));
		assertNull(Projection.of(Collections.singletonList(prospect(0, new double[]{10}, new int[]{1}, new double[0])), 0));
	}
}
