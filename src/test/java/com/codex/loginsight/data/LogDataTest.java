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
}
