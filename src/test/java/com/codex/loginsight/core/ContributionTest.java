package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;

/** The shared bosses' rules, the fight's damage and the log built from finished kills. */
public class ContributionTest
{
	private static final long MIN = 60_000L;
	private static final double E = 1e-9;

	@Test
	public void ratesMatchTheWiki()
	{
		// Nex: the six uniques add up to the wiki's effective 1 in 43 a kill.
		double nex = 0;
		for (Contribution.Unique u : Contribution.Boss.NEX.getUniques())
		{
			nex += u.getRate();
		}
		assertEquals(1 / 43.0, nex, E);
		// Corp: the sigil table is 1/585, then 1/7 elysian and 3/7 each spectral and arcane.
		assertEquals(1 / 585.0 / 7, rate(Contribution.Boss.CORP, ItemID.ELYSIAN_SIGIL), E);
		assertEquals(1 / 585.0 * 3 / 7, rate(Contribution.Boss.CORP, ItemID.SPECTRAL_SIGIL), E);
		assertEquals(8 / 512.0, rate(Contribution.Boss.CORP, ItemID.SPIRIT_SHIELD), E);
		assertEquals(3 / 512.0, rate(Contribution.Boss.CORP, ItemID.HOLY_ELIXIR), E);
		assertEquals(1 / 75.0, rate(Contribution.Boss.TITANS, ItemID.TWINFLAME_PIECE_1), E);
		assertEquals(1 / 75.0, rate(Contribution.Boss.TITANS, ItemID.TWINFLAME_PIECE_2), E);
		assertEquals(1 / 16.0, rate(Contribution.Boss.TITANS, ItemID.GIANTSOUL_AMULET_UNCHARGED), E);
		assertEquals(5400, Contribution.Boss.NEX.getHitpoints());
		assertEquals(2000, Contribution.Boss.CORP.getHitpoints());
		assertEquals(1200, Contribution.Boss.TITANS.getHitpoints());
	}

	private static double rate(Contribution.Boss boss, int item)
	{
		return boss.getUniques()[boss.uniqueIndex(item)].getRate();
	}

	@Test
	public void titansScaleByWholePercent()
	{
		// The wiki's example: half the damage halves the 1/75 crown to 1/150.
		Contribution.Chance half = Contribution.chance(Contribution.Boss.TITANS, 600, 600, true);
		assertEquals(0.5, half.getShare(), E);
		assertEquals(1 / 150.0, half.getEach()[1], E);
		assertTrue(half.isEstimate());
		// 11 of 1,200 is under 1%: no unique chance (the wiki's "1-11 damage" line).
		assertEquals(0, Contribution.share(Contribution.Boss.TITANS, 11, 1189).getShare(), E);
		assertEquals(0.01, Contribution.share(Contribution.Boss.TITANS, 12, 1188).getShare(), E);
		// 659 of 1,200 is 54.9%, cut to 54%.
		assertEquals(0.54, Contribution.share(Contribution.Boss.TITANS, 659, 541).getShare(), E);
		// Alone, the kill is all yours.
		Contribution.Chance solo = Contribution.chance(Contribution.Boss.TITANS, 1200, 0, true);
		assertEquals(1, solo.getShare(), E);
		assertFalse(solo.isEstimate());
		assertEquals(1 / 16.0 + 1 / 75.0, solo.getExpected(), E);
	}

	@Test
	public void corpGivesTheWholeDropOrNothing()
	{
		Contribution.Chance top = Contribution.chance(Contribution.Boss.CORP, 900, 1100, true);
		assertEquals(0.45, top.getShare(), E);
		assertEquals(8 / 512.0, top.getEach()[0], E);
		assertFalse(top.isEstimate());
		Contribution.Chance not = Contribution.chance(Contribution.Boss.CORP, 900, 1100, false);
		assertEquals(0, not.getAny(), E);
		assertNotNull(not.getWhy());
	}

	@Test
	public void nexSharesThePerKillRateAsAnEstimate()
	{
		Contribution.Chance trio = Contribution.chance(Contribution.Boss.NEX, 1800, 3600, true);
		assertEquals(1800 / 5400.0, trio.getShare(), E);
		assertEquals(1 / 43.0 / 3, trio.getExpected(), E);
		assertTrue(trio.isEstimate());
		// More damage seen than the hitpoints (healing, reavers): the whole is what was seen.
		Contribution.Share healed = Contribution.share(Contribution.Boss.NEX, 3000, 3000);
		assertEquals(6000, healed.getTotal());
		assertEquals(0.5, healed.getShare(), E);
		Contribution.Chance solo = Contribution.chance(Contribution.Boss.NEX, 5600, 0, true);
		assertEquals(1 / 43.0, solo.getExpected(), E);
		assertFalse(solo.isEstimate());
		assertEquals(0, Contribution.chance(Contribution.Boss.NEX, 100, 5300, false).getAny(), E);
	}

	@Test
	public void npcsAndLinesAreRecognised()
	{
		assertEquals(Contribution.Boss.NEX, Contribution.Boss.ofDamaged(NpcID.NEX_ICEMAGE));
		assertEquals(Contribution.Boss.NEX, Contribution.Boss.ofDamaged(NpcID.NEX_PRISON_BLOOD_REAVER));
		assertNull(Contribution.Boss.ofDying(NpcID.NEX_ICEMAGE));
		assertEquals(Contribution.Boss.CORP, Contribution.Boss.ofDying(NpcID.CORP_BEAST));
		assertNull(Contribution.Boss.ofDamaged(NpcID.RT_SUMMON_ELEMENTAL_FIRE));
		assertEquals(Contribution.Boss.TITANS, Contribution.Boss.ofDamaged(NpcID.RT_ICE_KING));
		// RuneLite's chat commands test line for the Corporeal Beast.
		Contribution.KillLine corp = Contribution.KillLine.read("Your Corporeal Beast kill count is: <col=ff0000>4</col>.");
		assertEquals(Contribution.Boss.CORP, corp.getBoss());
		assertEquals(4, corp.getCount());
		assertEquals(Contribution.Boss.NEX, Contribution.KillLine.read("Your Nex kill count is: <col=ff0000>1,204</col>.").getBoss());
		assertEquals(1204, Contribution.KillLine.read("Your Nex kill count is: <col=ff0000>1,204</col>.").getCount());
		assertEquals(Contribution.Boss.TITANS, Contribution.KillLine.read("Your Royal Titans kill count is: <col=ff0000>9</col>.").getBoss());
		assertNull(Contribution.KillLine.read("Your Zulrah kill count is: <col=ff0000>9</col>."));
		assertEquals(Contribution.Boss.NEX, Contribution.Boss.ofPage("Nex"));
		assertNull(Contribution.Boss.ofPage("Nexling"));
		assertEquals(Contribution.Boss.TITANS, Contribution.Boss.ofPage("Royal Titans"));
		assertEquals(Contribution.Boss.CORP, Contribution.uniqueOf(ItemID.ELYSIAN_SIGIL));
	}

	@Test
	public void theFightIsReadOnlyWhenTheKillEnds()
	{
		Contribution.Fight fight = new Contribution.Fight();
		long t = 0;
		fight.hit(Contribution.Boss.CORP, true, 50, t);
		fight.hit(Contribution.Boss.CORP, false, 70, t);
		fight.hit(Contribution.Boss.CORP, true, 0, t);
		assertEquals(50, fight.mine(Contribution.Boss.CORP));
		assertTrue(fight.isOpen(Contribution.Boss.CORP));
		// The boss dies; no kill count line follows: a kill without credit, after the wait.
		fight.died(Contribution.Boss.CORP, t += 1000);
		assertTrue(fight.expired(t + 1000).isEmpty());
		List<Contribution.Fight.Done> done = fight.expired(t + Contribution.Fight.CREDIT_WAIT_MS + 1);
		assertEquals(1, done.size());
		assertEquals(50, done.get(0).getMine());
		assertEquals(70, done.get(0).getOthers());
		assertFalse(fight.isOpen(Contribution.Boss.CORP));
		// A kill count line takes the damage at once.
		fight.hit(Contribution.Boss.CORP, true, 1500, t += MIN);
		fight.died(Contribution.Boss.CORP, t);
		Contribution.Fight.Done credited = fight.credited(Contribution.Boss.CORP);
		assertEquals(1500, credited.getMine());
		assertTrue(fight.expired(t + MIN).isEmpty());
		// A line with no damage of yours is not a kill of yours.
		assertNull(fight.credited(Contribution.Boss.NEX));
	}

	@Test
	public void bothTitansMustFallBeforeTheWait()
	{
		Contribution.Fight fight = new Contribution.Fight();
		fight.hit(Contribution.Boss.TITANS, true, 600, 0);
		fight.died(Contribution.Boss.TITANS, 0);
		assertTrue(fight.expired(Contribution.Fight.CREDIT_WAIT_MS * 3).isEmpty());
		assertTrue(fight.isOpen(Contribution.Boss.TITANS));
		fight.died(Contribution.Boss.TITANS, Contribution.Fight.CREDIT_WAIT_MS * 3);
		assertEquals(1, fight.expired(Contribution.Fight.CREDIT_WAIT_MS * 5).size());
	}

	@Test
	public void idleDamageIsDropped()
	{
		Contribution.Fight fight = new Contribution.Fight();
		fight.hit(Contribution.Boss.NEX, true, 300, 0);
		assertTrue(fight.expired(Contribution.Fight.IDLE_MS + 1).isEmpty());
		assertFalse(fight.isOpen(Contribution.Boss.NEX));
	}

	@Test
	public void theLogAddsUpOddsPerUniqueAndDryness()
	{
		ContributionLog log = new ContributionLog().repair();
		long t = 0;
		for (int i = 1; i <= 10; i++)
		{
			log.killed(Contribution.Boss.TITANS, i, 600, 600, true, t += 3 * MIN);
		}
		ContributionReport r = ContributionReport.of(log, Contribution.Boss.TITANS);
		assertEquals(10, r.getKills());
		assertEquals(10 * (1 / 32.0 + 1 / 150.0), r.getDryExpected(), 1e-9);
		ContributionReport.Odds amulet = r.getOdds().get(0);
		assertEquals(10 / 32.0, amulet.getExpected(), 1e-9);
		assertEquals(Math.pow(1 - 1 / 32.0, 10), amulet.getWaiting(), 1e-9);
		assertEquals(0.5, r.getAverageShare(), E);
		assertNotNull(r.getLuck());
		// An amulet in the loot of the latest kill ends the spell; the log's new item line for it is not counted twice.
		ContributionLog.Find find = log.found(ItemID.GIANTSOUL_AMULET_UNCHARGED, t + 10_000);
		assertNotNull(find);
		assertEquals(10 * (1 / 32.0 + 1 / 150.0), find.getTook(), 1e-9);
		assertNull(log.found(ItemID.GIANTSOUL_AMULET_UNCHARGED, t + 20_000));
		r = ContributionReport.of(log, Contribution.Boss.TITANS);
		assertEquals(0, r.getDryKills());
		assertEquals(1, r.getUniques());
		assertEquals(1, r.getOdds().get(0).getGot());
		assertEquals(1 / (10 / 32.0), r.getOdds().get(0).getRatio(), 1e-9);
		assertEquals(ItemID.GIANTSOUL_AMULET_UNCHARGED, r.latest().getUnique());
	}

	@Test
	public void corpKillsWithoutCreditAddNothing()
	{
		ContributionLog log = new ContributionLog().repair();
		log.killed(Contribution.Boss.CORP, 5, 2000, 0, true, 0);
		log.killed(Contribution.Boss.CORP, -1, 700, 1300, false, MIN);
		ContributionReport r = ContributionReport.of(log, Contribution.Boss.CORP);
		assertEquals(2, r.getKills());
		assertEquals(1, r.getCredited());
		double table = 0;
		for (Contribution.Unique u : Contribution.Boss.CORP.getUniques())
		{
			table += u.getRate();
		}
		assertEquals(table, r.getExpected(), 1e-9);
		assertEquals(0.35, r.latest().getShare(), E);
	}

	@Test
	public void theLogSurvivesASaveAndADamagedOne()
	{
		Gson gson = new Gson();
		ContributionLog log = new ContributionLog().repair();
		log.killed(Contribution.Boss.NEX, 12, 2000, 4000, true, 0);
		log.found(ItemID.NIHIL_HORN, 1000);
		ContributionLog back = ContributionLog.load(gson, gson.toJson(log)).getValue();
		ContributionReport r = ContributionReport.of(back, Contribution.Boss.NEX);
		assertEquals(1, r.getKills());
		assertEquals(1, r.getOdds().get(1).getGot());
		assertEquals(ItemID.NIHIL_HORN, r.latest().getUnique());
		// A save from a version with fewer uniques, and a broken one, still read.
		String shorter = gson.toJson(log).replaceAll("\"uniqueGot\":\\[[^]]*]", "\"uniqueGot\":[1]");
		assertEquals(6, ContributionReport.of(ContributionLog.load(gson, shorter).getValue(), Contribution.Boss.NEX)
			.getOdds().size());
		assertTrue(ContributionLog.load(gson, "{\"tracks\":[{\"boss\":\"nex\",\"kills\":").isDamaged());
		assertTrue(ContributionLog.load(gson, "{\"tracks\":[{\"boss\":\"nope\"}]}").getValue().isEmpty());
	}
}
