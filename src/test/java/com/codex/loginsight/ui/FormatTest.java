package com.codex.loginsight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class FormatTest
{
	@Test
	public void dryReadsTheWayPlayersSayIt()
	{
		// 18% of players would have a 1/3,000 slot by 612 kc: "18% dry", not "82% dry".
		double stillWaiting = Math.pow(1 - 1 / 3000.0, 612);
		assertEquals("18% dry", Format.dry(stillWaiting));
		assertEquals(">99% dry", Format.dry(0.001));
	}

	@Test
	public void chancesDropNeedlessDecimals()
	{
		assertEquals("1/3", Format.chance(1 / 3.0));
		assertEquals("1/2.5", Format.chance(1 / 2.5));
		assertEquals("1/5,000", Format.chance(1 / 5000.0));
		assertEquals("Always", Format.chance(1));
	}

	@Test
	public void sourcesReadAsNames()
	{
		assertEquals("King Black Dragon", Format.source("Killing king black dragon"));
		assertEquals("Hard Caskets", Format.source("Opening hard caskets"));
		assertEquals("Tombs of Amascut Reward Chests", Format.source("Tombs of amascut reward chests (300)"));
		assertEquals("Leviathan", Format.source("Killing the leviathan"));
		assertEquals("300", Format.detail("Tombs of amascut reward chests (300)"));
		assertNull(Format.detail("Killing vorkath"));
	}

	@Test
	public void hoursRoundSensibly()
	{
		assertEquals("<1m", Format.hours(0.001));
		assertEquals("45m", Format.hours(0.75));
		assertEquals("3h 12m", Format.hours(3.2));
		assertEquals("2,616h", Format.hours(2616.08));
		assertEquals("-", Format.hours(Double.POSITIVE_INFINITY));
	}
}
