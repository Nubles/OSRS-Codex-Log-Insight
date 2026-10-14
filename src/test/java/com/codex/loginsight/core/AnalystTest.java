package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.loginsight.data.Activity;
import com.codex.loginsight.data.ItemOdds;
import com.codex.loginsight.data.LogData;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class AnalystTest
{
	private static final int PET = 1;
	private static final int HEAD = 2;
	private static final int SHIELD = 3;
	private static final int SPINE = 4;
	private static final int CLAW = 5;

	private final LogData data = data();
	private final LogStructure structure = new LogStructure(Collections.singletonList(new LogStructure.Tab("Bosses",
		Collections.singletonList(new LogStructure.Page("Bosses", "Beast", Arrays.asList(PET, HEAD, SHIELD, SPINE, CLAW))))),
		names());

	private static LogData data()
	{
		List<Activity.Drop> drops = Arrays.asList(
			new Activity.Drop(0, PET, 1000, 0),
			new Activity.Drop(0, HEAD, 100, 0),
			new Activity.Drop(0, SHIELD, 50, 0),
			new Activity.Drop(0, SPINE, 20, 0),
			new Activity.Drop(0, CLAW, 20, 4));
		Activity beast = new Activity(0, "Killing the beast", Activity.Category.COMBAT, 50, 40, 0, drops);
		Map<Integer, ItemOdds> odds = new HashMap<>();
		odds.put(PET, odds(1000));
		odds.put(HEAD, odds(100));
		odds.put(SHIELD, odds(50));
		return new LogData(Collections.singletonList(beast), Collections.singletonList("Beast kills"), odds,
			Collections.singleton(PET));
	}

	private static ItemOdds odds(double in)
	{
		return new ItemOdds(Collections.singletonList(new ItemOdds.Roll("Beast kills", 1 / in, 1)), 0);
	}

	private static Map<Integer, String> names()
	{
		Map<Integer, String> names = new HashMap<>();
		names.put(PET, "Beastling");
		names.put(HEAD, "Beast head");
		names.put(SHIELD, "Beast shield");
		names.put(SPINE, "Spine");
		names.put(CLAW, "Claw");
		return names;
	}

	@Test
	public void freshAccountHasNoLuckButHasEstimates()
	{
		Report report = new Analyst(data).analyse(structure, new Progress(), false, true, -1, -1);
		Report.PageReport page = report.getPages().get(0);
		assertEquals(0, page.getOwned());
		assertEquals(5, page.getTotal());
		assertNull(page.getLuck());
		assertTrue(Double.isFinite(page.getHoursToFinish()));
		// Claw only drops after the spine, so the first slot comes from the 1 in 20 spine or the others.
		Report.Hunt hunt = report.getHunts().get(0);
		assertEquals(SPINE, hunt.getNextItemId());
		assertEquals(5, hunt.getRemaining());
		double expected = 1 / (1 / 1000.0 + 1 / 100.0 + 1 / 50.0 + 1 / 20.0) / 50;
		assertEquals(expected, hunt.getHoursToNext(), expected * 0.02);
	}

	@Test
	public void luckComparesOwnedSlotsWithTheirOdds()
	{
		Progress lucky = new Progress();
		lucky.setCounter("Beast kills", 20);
		lucky.addOwned(PET);
		lucky.addOwned(HEAD);
		Report report = new Analyst(data).analyse(structure, lucky, false, true, -1, -1);
		Luck luck = report.getPages().get(0).getLuck();
		assertNotNull(luck);
		assertEquals(Luck.Verdict.BLESSED, luck.verdict());

		Progress dry = new Progress();
		dry.setCounter("Beast kills", 400);
		Luck parched = new Analyst(data).analyse(structure, dry, false, true, -1, -1).getPages().get(0).getLuck();
		assertEquals(Luck.Verdict.PARCHED, parched.verdict());
	}

	@Test
	public void driestSlotsAreListedMostOverdueFirst()
	{
		Progress dry = new Progress();
		dry.setCounter("Beast kills", 400);
		Report report = new Analyst(data).analyse(structure, dry, false, true, -1, -1);
		assertEquals(SHIELD, report.getDriest().get(0).getItemId());
		assertEquals(HEAD, report.getDriest().get(1).getItemId());
	}

	@Test
	public void ironRatesAreSlower()
	{
		Analyst analyst = new Analyst(data);
		double main = analyst.analyse(structure, new Progress(), false, true, -1, -1).getHoursLeft();
		double iron = analyst.analyse(structure, new Progress(), true, true, -1, -1).getHoursLeft();
		assertEquals(main * 50 / 40, iron, main * 0.01);
	}

	@Test
	public void ownPaceReplacesTheTypicalRate()
	{
		Progress progress = new Progress();
		Progress.Pace pace = progress.paceFor("Beast kills");
		long t = 1_000_000;
		for (int kc = 1; kc <= Analyst.PACE_SAMPLES + 2; kc++)
		{
			pace.record(kc, t);
			t += 36_000; // 100 kills an hour
		}
		Report report = new Analyst(data).analyse(structure, progress, false, true, -1, -1);
		Report.Hunt hunt = report.getHunts().get(0);
		assertTrue(hunt.isOwnPace());
		assertEquals(100, hunt.getPerHour(), 1);
		Report typical = new Analyst(data).analyse(structure, progress, false, false, -1, -1);
		assertEquals(50, typical.getHunts().get(0).getPerHour(), 1e-9);
	}

	@Test
	public void finishedPageNeedsNoTime()
	{
		Progress all = new Progress();
		for (int id = PET; id <= CLAW; id++)
		{
			all.addOwned(id);
		}
		Report report = new Analyst(data).analyse(structure, all, false, true, -1, -1);
		assertTrue(report.getPages().get(0).isComplete());
		assertEquals(0, report.getHoursLeft(), 1e-9);
		assertTrue(report.getHunts().isEmpty());
	}

	@Test
	public void officialCountWinsWhenHigher()
	{
		Report report = new Analyst(data).analyse(structure, new Progress(), false, true, 300, 1700);
		assertEquals(300, report.owned());
		assertEquals(1700, report.total());
		assertTrue(report.needsFullRead());
	}
}
