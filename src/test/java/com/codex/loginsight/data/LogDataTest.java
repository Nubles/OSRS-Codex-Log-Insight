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
}
