package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The wiki's purple chances, checked against the wiki's own figures. */
public class RaidsTest
{
	private static final double EPS = 1e-9;

	@Test
	public void chambersGiveOnePercentPer8676Points()
	{
		double[] rolls = Raids.coxRolls(8676);
		assertEquals(1, rolls.length);
		assertEquals(0.01, rolls[0], EPS);
		assertEquals(0.5, Raids.coxRolls(433_800)[0], EPS);
	}

	@Test
	public void chambersCapARollAt570000PointsAndRollAgain()
	{
		// Wiki: "a team who possesses 855,000 points in total has a 65.7% chance to receive a unique loot"; the
		// 285,000 points past the cap roll for a second.
		double[] rolls = Raids.coxRolls(855_000);
		assertEquals(2, rolls.length);
		assertEquals(0.657, rolls[0], 0.0005);
		assertEquals(285_000 / 867_600.0, rolls[1], EPS);
		// Never more than six rolls ("Up to six unique rewards").
		assertEquals(6, Raids.coxRolls(10_000_000).length);
	}

	@Test
	public void aChambersShareFollowsYourPoints()
	{
		Raids.Chance solo = Raids.cox(30_000, 30_000);
		assertEquals(30_000 / 867_600.0, solo.getYours(), EPS);
		assertEquals(solo.getTeam(), solo.getYours(), EPS);
		assertFalse(solo.isEstimate());
		Raids.Chance trio = Raids.cox(30_000, 90_000);
		assertEquals(90_000 / 867_600.0, trio.getTeam(), EPS);
		assertEquals(30_000 / 867_600.0, trio.getYours(), EPS);
		assertTrue(trio.isEstimate());
		assertFalse(Raids.cox(-1, 90_000).isKnown());
		assertFalse(Raids.cox(100, 0).isKnown());
	}

	@Test
	public void theatreIsTheSameChanceForEveryTeamSize()
	{
		assertEquals(1 / 9.1, Raids.tob(Raids.NORMAL, 1).getYours(), EPS);
		assertEquals(1 / 9.1, Raids.tob(Raids.NORMAL, 4).getTeam(), EPS);
		assertEquals(1 / 9.1 / 4, Raids.tob(Raids.NORMAL, 4).getYours(), EPS);
		assertEquals(1 / 7.7, Raids.tob(Raids.HARD, 1).getYours(), EPS);
		assertFalse(Raids.tob(Raids.ENTRY, 3).isKnown());
		assertFalse(Raids.tob(Raids.NORMAL, 0).isKnown());
	}

	@Test
	public void tombsScaleTheRaidLevelAsTheWikiDoes()
	{
		// Wiki: "at raid level 400, players will have a 1% chance for every 10,500−20(310+90/3)=3,700 points earned."
		assertEquals(3_700, Raids.toaPointsPerPercent(400), EPS);
		assertEquals(10_500 - 20 * 300, Raids.toaPointsPerPercent(300), EPS);
		assertEquals(310, Raids.toaScaledLevel(310), EPS);
		assertEquals(350, Raids.toaScaledLevel(430), EPS);
		assertEquals(350 + 70 / 6.0, Raids.toaScaledLevel(500), EPS);
	}

	@Test
	public void tombsLeaveOutTheStarting5000Once()
	{
		// Wiki: "the starting 5,000 points are subtracted when calculating loot and purple percentage", and at raid
		// level 400 "a 1% chance for every ... 3,700 points earned". A solo player ending on 27,200 total reward points
		// has 22,200 to count: 6%.
		long counted = Raids.toaChancePoints(27_200);
		assertEquals(22_200, counted);
		assertEquals(0.06, Raids.toa(counted, 400, 1).getYours(), EPS);
		assertEquals(0, Raids.toaChancePoints(3_000));
		// The game's personal contribution already leaves the 5,000 out, so it goes in as it is, not reduced again.
		assertEquals(22_200 / 370_000.0, Raids.toa(22_200, 400, 1).getYours(), EPS);
		assertEquals(0, Raids.toa(0, 400, 1).getYours(), EPS);
	}

	@Test
	public void tombsChanceIsYourPointsPercentCappedAt55()
	{
		Raids.Chance solo = Raids.toa(37_000, 400, 1);
		assertEquals(0.10, solo.getYours(), EPS);
		assertEquals(0.10, solo.getTeam(), EPS);
		assertFalse(solo.isEstimate());
		assertEquals(0.55, Raids.toa(10_000_000, 400, 1).getYours(), EPS);
		Raids.Chance team = Raids.toa(18_500, 400, 3);
		assertEquals(0.05, team.getYours(), EPS);
		assertEquals(-1, team.getTeam(), EPS);
		assertTrue(team.isEstimate());
		assertFalse(Raids.toa(-1, 300, 1).isKnown());
	}

	@Test
	public void uniquesAreFoundByRaid()
	{
		assertSame(Raids.Kind.COX, Raids.uniqueOf(net.runelite.api.gameval.ItemID.TWISTED_BOW));
		assertSame(Raids.Kind.TOB, Raids.uniqueOf(net.runelite.api.gameval.ItemID.INFERNAL_DEFENDER_HILT));
		assertSame(Raids.Kind.TOA, Raids.uniqueOf(net.runelite.api.gameval.ItemID.TUMEKENS_SHADOW_UNCHARGED));
		assertNull(Raids.uniqueOf(net.runelite.api.gameval.ItemID.COINS));
		assertEquals(12, Raids.uniques(Raids.Kind.COX).length);
		assertEquals(7, Raids.uniques(Raids.Kind.TOB).length);
		assertEquals(7, Raids.uniques(Raids.Kind.TOA).length);
	}

	@Test
	public void pagesAreFoundByName()
	{
		assertSame(Raids.Kind.COX, Raids.Kind.ofPage("Chambers of Xeric"));
		assertSame(Raids.Kind.TOB, Raids.Kind.ofPage("Theatre of Blood"));
		assertSame(Raids.Kind.TOA, Raids.Kind.ofPage("Tombs of Amascut"));
		assertNull(Raids.Kind.ofPage("Zulrah"));
		assertNull(Raids.Kind.ofPage(null));
	}
}
