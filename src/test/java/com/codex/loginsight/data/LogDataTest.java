package com.codex.loginsight.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Collections;
import org.junit.Test;

public class LogDataTest
{
	private static final int VORKI = 21992;
	private static final int TANZANITE_FANG = 12922;
	private static final int ABYSSAL_ORPHAN = 13262;

	@Test
	public void bundledTablesLoad() throws Exception
	{
		LogData data = LogData.load(new Gson());
		assertTrue(data.getActivities().size() > 200);
		assertTrue(data.getLabels().contains("Vorkath kills"));
		assertTrue(data.isPet(VORKI));
		assertFalse(data.dropsOf(VORKI).isEmpty());

		// Zulrah rolls its uniques twice a kill, so the fang is about 1 in 512.
		ItemOdds fang = data.odds(TANZANITE_FANG);
		assertNotNull(fang);
		assertEquals("Zulrah kills", fang.mainLabel());
		assertEquals(1 / 512.0, fang.mainChance(), 1 / 512.0 * 0.01);

		ItemOdds orphan = data.odds(ABYSSAL_ORPHAN);
		assertEquals(Math.pow(1 - 1 / 2560.0, 2560), orphan.dryChance(Collections.singletonMap("Abyssal Sire kills", 2560)), 1e-6);
		assertEquals(-1, orphan.dryChance(Collections.emptyMap()), 1e-9);
	}

	@Test
	public void splitCountersKeepTheirCurrentOdds() throws Exception
	{
		// Collection Log Luck splits these counters into stretches (first kill off task, before a buff);
		// every kill must not roll all of them at once.
		LogData data = LogData.load(new Gson());
		assertEquals(1 / 100.0, data.odds(13225).mainChance(), 1e-6); // TzRek-Jad on task
		assertEquals(1 / 75.0, data.odds(21291).mainChance(), 1e-6); // Jal-nib-rek on task
		assertEquals(1 / 200.0, data.odds(19701).mainChance(), 1e-6); // Jar of darkness after its buff
		assertEquals(1 / 75.0, data.odds(31245).mainChance(), 1e-6); // Belle's folly after its buffs
		assertEquals(1, data.odds(11920).getRolls().stream().filter(r -> r.getLabel().equals("King Black Dragon kills")).count());
	}

	@Test
	public void secondRowsForOneSlotAreAlternatives()
	{
		LogData.Raw raw = new LogData.Raw();
		LogData.RawActivity a = new LogData.RawActivity();
		a.n = "Killing araxxor";
		a.c = "C";
		a.m = 45;
		a.i = 40;
		a.d.add(new double[]{1, 3000, 0});
		a.d.add(new double[]{2, 200, 0});
		a.d.add(new double[]{1, 1500, 2});
		raw.activities.add(a);
		LogData data = LogData.fromRaw(raw);
		Activity araxxor = data.getActivities().get(0);
		assertEquals(2, araxxor.getDrops().size());
		assertEquals(1, araxxor.getDrops().get(0).getItemId());
		assertEquals(1500, araxxor.getDrops().get(0).getAttempts(), 1e-9);
		assertEquals(1, data.dropsOf(1).size());
	}

	private static final int ROCKY = 20663;
	private static final int TANGLEROOT = 20661;

	private static Activity.Drop only(LogData data, int itemId)
	{
		assertEquals(1, data.dropsOf(itemId).size());
		return data.dropsOf(itemId).get(0);
	}

	@Test
	public void skillingPetsFollowTheAccountsLevel() throws Exception
	{
		LogData data = LogData.load(new Gson());
		// Bundled at level 99: Rocky from seed stalls is 1 in 36,490 - 25 x 99.
		assertEquals(34_015, only(data, ROCKY).getAttempts(), 1e-9);
		assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList("AGILITY", "FARMING", "FISHING", "HUNTER", "MINING",
			"RUNECRAFTING", "THIEVING", "WOODCUTTING")), data.scaledSkills());

		// A level 40 thief rolls 1 in 35,490; a level 99 one with 200M experience fifteen times as often.
		LogData forty = data.atLevels(Collections.singletonMap("THIEVING", 40), Collections.emptySet());
		assertEquals(35_490, only(forty, ROCKY).getAttempts(), 1e-9);
		LogData maxed = data.atLevels(Collections.singletonMap("THIEVING", 99), Collections.singleton("THIEVING"));
		assertEquals(34_015 / 15.0, only(maxed, ROCKY).getAttempts(), 1e-9);
		// Fruit trees use the wiki's base of 9,000, not Log Adviser's rounded 6,500.
		LogData farmer = data.atLevels(Collections.singletonMap("FARMING", 51), Collections.emptySet());
		assertEquals(9_000 - 25 * 51, only(farmer, TANGLEROOT).getAttempts(), 1e-9);

		// Everything else is untouched, and unknown levels change nothing.
		assertEquals(only(data, TANZANITE_FANG).getAttempts(), only(forty, TANZANITE_FANG).getAttempts(), 1e-9);
		assertEquals(data.getActivities().size(), forty.getActivities().size());
		assertTrue(data.atLevels(null, null) == data);
		assertTrue(data.atLevels(Collections.singletonMap("THIEVING", 99), Collections.emptySet()) == data);
		assertTrue(data.atLevels(Collections.singletonMap("SLAYER", 50), Collections.emptySet()) == data);
	}

	@Test
	public void petAttemptsStayInBounds()
	{
		assertEquals(36_465, Activity.Drop.petAttempts(36_490, 0, false), 1e-9);
		assertEquals(34_015, Activity.Drop.petAttempts(36_490, 126, false), 1e-9);
		assertEquals(1, Activity.Drop.petAttempts(100, 99, false), 1e-9);
	}

	@Test
	public void oldTablesWithoutPetBasesStillLoad()
	{
		LogData.Raw raw = new LogData.Raw();
		LogData.RawActivity a = new LogData.RawActivity();
		a.n = "Thieving";
		a.c = "O";
		a.m = 1000;
		a.i = 1000;
		a.d.add(new double[]{ROCKY, 34_015, 0});
		raw.activities.add(a);
		LogData data = LogData.fromRaw(raw);
		assertFalse(only(data, ROCKY).isLevelScaled());
		assertTrue(data.atLevels(Collections.singletonMap("THIEVING", 30), Collections.emptySet()) == data);
	}
}
