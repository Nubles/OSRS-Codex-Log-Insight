package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The raid lines, as RuneLite's chat commands tests give them. */
public class RaidReaderTest
{
	@Test
	public void killCountLinesNameTheRaidAndMode()
	{
		RaidReader.Completion cox = RaidReader.completion("Your completed Chambers of Xeric count is: <col=ff0000>51</col>.");
		assertSame(Raids.Kind.COX, cox.getKind());
		assertEquals(Raids.NORMAL, cox.getMode());
		assertEquals(51, cox.getCount());
		RaidReader.Completion cm = RaidReader.completion("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>13</col>.");
		assertEquals(Raids.CHALLENGE, cm.getMode());
		RaidReader.Completion tob = RaidReader.completion("Your completed Theatre of Blood count is: <col=ff0000>73</col>.");
		assertSame(Raids.Kind.TOB, tob.getKind());
		assertEquals(Raids.NORMAL, tob.getMode());
		assertEquals(Raids.ENTRY, RaidReader.completion("Your completed Theatre of Blood: Entry Mode count is: <col=ff0000>73</col>.").getMode());
		assertEquals(Raids.HARD, RaidReader.completion("Your completed Theatre of Blood: Hard Mode count is: <col=ff0000>2</col>.").getMode());
		RaidReader.Completion toa = RaidReader.completion("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>1,204</col>.");
		assertSame(Raids.Kind.TOA, toa.getKind());
		assertEquals(Raids.EXPERT, toa.getMode());
		assertEquals(1204, toa.getCount());
		assertEquals(Raids.ENTRY, RaidReader.completion("Your completed Tombs of Amascut: Entry Mode count is: <col=ff0000>9</col>.").getMode());
		assertEquals(Raids.NORMAL, RaidReader.completion("Your completed Tombs of Amascut count is: <col=ff0000>2</col>.").getMode());
	}

	@Test
	public void otherCountsAreNotRaids()
	{
		assertNull(RaidReader.completion("Your Zulrah kill count is: <col=ff0000>12</col>."));
		assertNull(RaidReader.completion("Your completed Fortis Colosseum count is: 3."));
		assertNull(RaidReader.completion(null));
	}

	@Test
	public void theChambersLineGivesAnExactTeamOnly()
	{
		String three = "<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>41:10</col> Personal best: </col><col=ff0000>40:03</col>";
		assertTrue(RaidReader.coxComplete(three));
		assertEquals(3, RaidReader.coxTeam(three));
		assertEquals(1, RaidReader.coxTeam("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>Solo</col> Duration:</col> <col=ff0000>20:10</col>"));
		assertEquals(-1, RaidReader.coxTeam("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>11-15 players</col> Duration:</col> <col=ff0000>23:25.40</col>"));
		assertEquals(-1, RaidReader.coxTeam("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>24+ players</col> Duration:</col> <col=ff0000>37:04.20</col>"));
		assertFalse(RaidReader.coxComplete("The raid has begun!"));
	}
}
