package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.loginsight.data.Activity;
import com.codex.loginsight.data.ItemOdds;
import com.codex.loginsight.data.LogData;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The luck oracle's math against brute force, and its readings on a Zulrah-shaped page. Zulrah's rates are the OSRS
 * Wiki's: each unique is 2 x 1/1,024 (two rolls a kill), Jar of swamp 1/3,000 and Pet snakeling 1/4,000 on one roll.
 */
public class OracleTest
{
	private static final String KILLS = "Zulrah kills";
	private static final int FANG = 12922;
	private static final int VISAGE = 12927;
	private static final int JAR = 12936;
	private static final int PET = 12921;
	private static final int SCALES = 12934;
	private static final int HEAD = 21907;

	// ---- Math ----

	@Test
	public void aFindOnTheFirstKillBeatsAlmostEveryone()
	{
		// 1 in 100: 99% need more kills, and the 1% who get it on kill one too share the tie.
		assertEquals(0.99 + 0.01 / 2, Oracle.luckAtKill(0.01, 1, 0), 1e-12);
	}

	@Test
	public void luckAtTheKillIsHalfOnAverage()
	{
		// Over every player, a fair percentile averages one half: checks the tie rule and the geometric pmf at once.
		for (double p : new double[]{0.5, 0.1, 1 / 512.25})
		{
			double mean = 0;
			double miss = 1;
			for (int k = 1; k < 200_000 && miss > 1e-15; k++)
			{
				double atK = miss * p;
				mean += atK * Oracle.luckAtKill(p, k, 0);
				miss *= 1 - p;
			}
			assertEquals("p=" + p, 0.5, mean, 1e-9);
		}
	}

	@Test
	public void aGuaranteeEndsTheWait()
	{
		// Vorkath's head: 1/50, and always on the 50th kill. Everyone left at kill 50 gets it there.
		double p = 1 / 50.0;
		assertEquals(Math.pow(1 - p, 49) / 2, Oracle.luckAtKill(p, 50, 50), 1e-12);
		assertEquals(0, Oracle.luckAtKill(p, 60, 50), 1e-12);
		double mean = 0;
		double miss = 1;
		for (int k = 1; k <= 50; k++)
		{
			double atK = k == 50 ? miss : miss * p;
			mean += atK * Oracle.luckAtKill(p, k, 50);
			miss *= 1 - p;
		}
		assertEquals(0.5, mean, 1e-9);
	}

	@Test
	public void luckAtTheKillMatchesTheWorkedExample()
	{
		// A fang at kill 212: per kill 1 - (1 - 1/1024)^2, so (1 - q)^212 of players would still be waiting.
		double q = 1 - Math.pow(1 - 1 / 1024.0, 2);
		double expected = Math.pow(1 - q, 212) + q * Math.pow(1 - q, 211) / 2;
		assertEquals(expected, Oracle.luckAtKill(q, 212, 0), 1e-12);
		assertEquals(0.66, expected, 0.01);
	}

	@Test
	public void copiesMatchBruteForceBinomial()
	{
		int n = 30;
		double p = 0.07;
		for (int got = 0; got <= 8; got++)
		{
			double below = 0;
			for (int k = 0; k < got; k++)
			{
				below += choose(n, k) * Math.pow(p, k) * Math.pow(1 - p, n - k);
			}
			double at = choose(n, got) * Math.pow(p, got) * Math.pow(1 - p, n - got);
			assertEquals("got=" + got, below + at / 2, Oracle.copiesPercentile(n, p, got), 1e-12);
		}
	}

	@Test
	public void copiesStayAccurateOverManyRolls()
	{
		// Ten thousand Zulrah rolls at 1/1024 sit very close to a Poisson count with the same mean.
		long rolls = 10_000;
		double p = 1 / 1024.0;
		for (int got = 0; got <= 20; got += 4)
		{
			assertEquals(Odds.dropPercentile(rolls * p, got), Oracle.copiesPercentile(rolls, p, got), 0.003);
		}
		assertTrue(Oracle.copiesPercentile(rolls, p, 20) > 0.99);
		assertTrue(Oracle.copiesPercentile(rolls, p, 0) < 0.01);
	}

	@Test
	public void killsForAShare()
	{
		assertEquals(69, Oracle.killsFor(0.01, 0.5), 0);
		assertEquals(230, Oracle.killsFor(0.01, 0.9), 0);
		assertTrue(Double.isInfinite(Oracle.killsFor(0, 0.5)));
	}

	private static double choose(int n, int k)
	{
		double c = 1;
		for (int i = 0; i < k; i++)
		{
			c = c * (n - i) / (i + 1);
		}
		return c;
	}

	// ---- Readings ----

	private final LogData data = data();
	private final LogStructure structure = new LogStructure(Arrays.asList(
		new LogStructure.Tab("Bosses", Arrays.asList(
			new LogStructure.Page("Bosses", "Zulrah", Arrays.asList(PET, FANG, VISAGE, JAR, SCALES)),
			new LogStructure.Page("Bosses", "Vorkath", Collections.singletonList(HEAD))))),
		names());

	private static LogData data()
	{
		List<Activity.Drop> drops = Arrays.asList(
			new Activity.Drop(0, PET, 4000, 0),
			new Activity.Drop(0, FANG, 512.25, 0),
			new Activity.Drop(0, VISAGE, 512.25, 0),
			new Activity.Drop(0, JAR, 3000, 0),
			new Activity.Drop(0, SCALES, 3, 0));
		Activity zulrah = new Activity(0, "Killing zulrah", Activity.Category.COMBAT, 30, 25, 0, drops);
		Activity vorkath = new Activity(1, "Killing vorkath", Activity.Category.COMBAT, 30, 25, 0,
			Collections.singletonList(new Activity.Drop(1, HEAD, 50, 0)));
		Map<Integer, ItemOdds> odds = new HashMap<>();
		odds.put(PET, roll(KILLS, 1 / 4000.0, 1, 0));
		odds.put(FANG, roll(KILLS, 1 / 1024.0, 2, 0));
		odds.put(VISAGE, roll(KILLS, 1 / 1024.0, 2, 0));
		odds.put(JAR, roll(KILLS, 1 / 3000.0, 1, 0));
		odds.put(SCALES, roll(KILLS, 1 / 3.0, 1, 0));
		odds.put(HEAD, roll("Vorkath kills", 1 / 50.0, 1, 50));
		return new LogData(Arrays.asList(zulrah, vorkath), Arrays.asList(KILLS, "Vorkath kills"), odds,
			Collections.singleton(PET));
	}

	private static ItemOdds roll(String label, double chance, int rolls, int guarantee)
	{
		return new ItemOdds(Collections.singletonList(new ItemOdds.Roll(label, chance, rolls)), guarantee, true);
	}

	private static Map<Integer, String> names()
	{
		Map<Integer, String> names = new HashMap<>();
		names.put(PET, "Pet snakeling");
		names.put(FANG, "Tanzanite fang");
		names.put(VISAGE, "Serpentine visage");
		names.put(JAR, "Jar of swamp");
		names.put(SCALES, "Zulrah's scales");
		names.put(HEAD, "Vorkath's head");
		return names;
	}

	private static Progress read(int zulrah)
	{
		Progress p = new Progress();
		p.setCounter(KILLS, zulrah);
		p.setFullReadAt(1);
		return p;
	}

	private static Progress.Find find(int id, String name, String page, int count)
	{
		return new Progress.Find(id, name, page, KILLS, count, 1 / 512.25, -1, 1);
	}

	private Oracle oracle(Progress p)
	{
		return new Analyst(data).analyse(structure, p, false, true, -1, -1).getOracle();
	}

	@Test
	public void aFindSeenDroppingIsReadAtItsKill()
	{
		Progress p = read(6900);
		p.addOwned(FANG);
		p.addFind(find(FANG, "Tanzanite fang", "Zulrah", 212));
		Oracle oracle = oracle(p);
		Oracle.Owned fang = oracle.luckiest();
		assertNotNull(fang);
		assertEquals(FANG, fang.getItemId());
		assertEquals(Oracle.Basis.AT_KILL, fang.getBasis());
		assertEquals(212, fang.getCount());
		double q = 1 - Math.pow(1 - 1 / 1024.0, 2);
		assertEquals(Oracle.luckAtKill(q, 212, 0), fang.getLuckier(), 1e-12);
		// Later kills do not change a find's reading: it stays at the kill it came on.
		p.setCounter(KILLS, 9000);
		assertEquals(fang.getLuckier(), oracle(p).luckiest().getLuckier(), 1e-12);
	}

	@Test
	public void aMissingSlotSaysHowManyTimesTheRate()
	{
		Progress p = read(6900);
		Oracle oracle = oracle(p);
		Oracle.Chase jar = null;
		for (Oracle.Chase c : oracle.getChasing())
		{
			if (c.getItemId() == JAR)
			{
				jar = c;
			}
		}
		assertNotNull(jar);
		assertEquals(2.3, jar.getTimesRate(), 1e-9);
		assertEquals(1 - Math.pow(1 - 1 / 3000.0, 6900), jar.haveIt(), 1e-9);
		assertEquals(0.90, jar.haveIt(), 0.005);
		// Drops have no memory: the wait from here is the plain average at the pace, 3,000 kills at 30 an hour.
		assertEquals(100, jar.getHours(), 1e-6);
		assertFalse(jar.isOwnPace());
		// The driest slot leads: the uniques are 27x their rate dry, so far drier than the jar.
		assertEquals(VISAGE == oracle.driest().getItemId() || FANG == oracle.driest().getItemId(), true);
	}

	@Test
	public void commonDropsAreLeftOut()
	{
		for (Oracle.Chase c : oracle(read(500)).getChasing())
		{
			assertTrue(c.getItemId() != SCALES);
		}
	}

	@Test
	public void nothingIsCalledDryBeforeTheOwnedSlotsAreRead()
	{
		Progress p = new Progress();
		p.setCounter(KILLS, 6900);
		Oracle oracle = oracle(p);
		assertTrue(oracle.getChasing().isEmpty());
		assertTrue(oracle.getPages().isEmpty());
	}

	@Test
	public void aPassedGuaranteeIsOverdue()
	{
		Progress p = read(10);
		p.setCounter("Vorkath kills", 60);
		Oracle.Chase head = oracle(p).driest();
		assertEquals(HEAD, head.getItemId());
		assertEquals(0, head.getDry(), 0);
	}

	@Test
	public void copiesAreReadWhenTheDropWasNotSeen()
	{
		Progress p = read(2000);
		p.addOwned(FANG);
		p.setObtained(FANG, 6, Collections.singletonList(KILLS));
		Oracle.Owned fang = oracle(p).luckiest();
		assertEquals(Oracle.Basis.COPIES, fang.getBasis());
		assertEquals(6, fang.getCopies());
		assertEquals(2000, fang.getCount());
		assertEquals(Oracle.copiesPercentile(4000, 1 / 1024.0, 6), fang.getLuckier(), 1e-12);
		assertTrue(fang.getLuckier() > 0.8);
	}

	@Test
	public void pagesAreTimedAtYourOwnPace()
	{
		Progress p = read(100);
		Progress.Pace pace = p.paceFor(KILLS);
		long at = 1_000_000;
		// Twenty kills at two minutes each: 30 an hour of your own.
		for (int kc = 100; kc <= 120; kc++)
		{
			pace.record(kc, at);
			at += 120_000;
		}
		p.setCounter(KILLS, 120);
		Oracle oracle = oracle(p);
		Oracle.Forecast zulrah = null;
		for (Oracle.Forecast f : oracle.getPages())
		{
			if (f.getPage().equals("Zulrah"))
			{
				zulrah = f;
			}
		}
		assertNotNull(zulrah);
		assertTrue(zulrah.isOwnPace());
		assertEquals(30, zulrah.getPerHour(), 0.01);
		assertTrue(Double.isFinite(zulrah.getHours()));
		assertTrue(zulrah.getHalf() <= zulrah.getNineInTen());
	}

	@Test
	public void theFirstFindOfASlotIsKeptAndSaved()
	{
		Progress p = read(500);
		// A find whose name several slots share has no page: it is never kept as a first.
		p.addFind(find(VISAGE, "Serpentine visage", null, 100));
		assertNull(p.firstFind(VISAGE));
		p.addFind(find(FANG, "Tanzanite fang", "Zulrah", 212));
		p.addFind(find(FANG, "Tanzanite fang", "Zulrah", 480));
		assertEquals(212, p.firstFind(FANG).getCount());
		Gson gson = new Gson();
		Progress back = Progress.load(gson, gson.toJson(p)).getValue();
		assertEquals(212, back.firstFind(FANG).getCount());
		assertEquals(212, p.copy().firstFind(FANG).getCount());
	}

	@Test
	public void anOlderSaveStartsFirstsFromItsFinds()
	{
		Gson gson = new Gson();
		Progress p = read(500);
		p.addFind(find(FANG, "Tanzanite fang", "Zulrah", 212));
		String json = gson.toJson(p).replaceAll(",\"firstFinds\":\\{[^}]*\\{[^}]*\\}\\}", "");
		assertFalse(json.contains("firstFinds"));
		Progress back = Progress.load(gson, json).getValue();
		assertEquals(212, back.firstFind(FANG).getCount());
	}
}
