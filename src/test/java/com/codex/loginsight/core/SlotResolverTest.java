package com.codex.loginsight.core;

import com.codex.loginsight.data.ItemOdds;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SlotResolverTest
{
	private static final int PAGE_A = 1;
	private static final int PAGE_B = 2;
	private static final int PAGE_C = 3;

	private final Map<Integer, ItemOdds> odds = new HashMap<>();

	public SlotResolverTest()
	{
		odds.put(PAGE_A, odds("Hard clues completed"));
		odds.put(PAGE_B, odds("Elite clues completed"));
		odds.put(PAGE_C, odds("Elite clues completed"));
	}

	private static ItemOdds odds(String label)
	{
		return new ItemOdds(Collections.singletonList(new ItemOdds.Roll(label, 0.01, 1)), 0);
	}

	private SlotResolver.Result resolve(Set<Integer> owned, String recent)
	{
		return SlotResolver.resolve(Arrays.asList(PAGE_A, PAGE_B, PAGE_C), owned, odds::get, recent);
	}

	@Test
	public void theOnlyMissingSlotWithTheNameIsTheOne()
	{
		SlotResolver.Result result = resolve(new HashSet<>(Arrays.asList(PAGE_A, PAGE_C)), null);
		assertEquals(PAGE_B, result.owned);
		assertFalse(result.ambiguous());
	}

	@Test
	public void theCounterThatJustRoseSettlesIt()
	{
		SlotResolver.Result result = resolve(new HashSet<>(), "Hard clues completed");
		assertEquals(PAGE_A, result.owned);
	}

	@Test
	public void twoSlotsStillPossibleMarksNone()
	{
		// Both elite-clue slots are missing and an elite casket was just opened: either could be the one.
		SlotResolver.Result result = resolve(new HashSet<>(), "Elite clues completed");
		assertEquals(-1, result.owned);
		assertTrue(result.ambiguous());
		assertTrue(result.shown == PAGE_B || result.shown == PAGE_C);
	}

	@Test
	public void noRecentKillAndSeveralMissingMarksNone()
	{
		assertTrue(resolve(new HashSet<>(), null).ambiguous());
	}

	@Test
	public void anUnknownNameIsNothing()
	{
		SlotResolver.Result result = SlotResolver.resolve(Collections.emptyList(), new HashSet<>(), odds::get, null);
		assertEquals(-1, result.owned);
		assertFalse(result.ambiguous());
	}

	@Test
	public void aWholeLogReadReplacesASlotMarkedInError()
	{
		Progress progress = new Progress();
		progress.addOwned(PAGE_A);
		progress.addOwned(PAGE_B);
		progress.toggleWanted(PAGE_C);
		progress.replaceOwned(new HashSet<>(Arrays.asList(PAGE_B, PAGE_C)));
		assertEquals(new HashSet<>(Arrays.asList(PAGE_B, PAGE_C)), progress.getOwned());
		assertFalse(progress.isWanted(PAGE_C));
	}
}
