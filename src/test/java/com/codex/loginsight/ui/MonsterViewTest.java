package com.codex.loginsight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.codex.loginsight.core.Report;
import org.junit.Test;

/** The short lines on a monster's Log view. */
public class MonsterViewTest
{
	@Test
	public void killsReadAsAWait()
	{
		assertEquals("~1,125 kills", MonsterView.kills(1125.46));
		assertEquals("1 kill", MonsterView.kills(0.6));
		assertEquals("-", MonsterView.kills(Double.POSITIVE_INFINITY));
	}

	@Test
	public void aSlotSaysItsRateAndTheShareWhoHaveItByNow()
	{
		Report.ItemReport jar = new Report.ItemReport(12936, "Jar of swamp", "Zulrah", false, false, 1 / 3000.0,
			"Zulrah kills", 1240, Math.pow(1 - 1 / 3000.0, 1240), "Killing zulrah", 68);
		assertEquals("1/3,000 a kill  ·  34% by now", MonsterView.detail(jar));
		Report.ItemReport tooth = new Report.ItemReport(1, "Tooth", "Slayer", false, false, 1 / 200.0, null, -1, -1,
			"Killing grunts", 2);
		assertEquals("1/200 a kill", MonsterView.detail(tooth));
		Report.ItemReport scales = new Report.ItemReport(12934, "Zulrah's scales", "Zulrah", true, false, 0,
			"Zulrah kills", 1240, -1, null, Double.POSITIVE_INFINITY);
		assertEquals("Not a roll per kill", MonsterView.detail(scales));
		Report.ItemReport head = new Report.ItemReport(21907, "Vorkath's head", "Vorkath", false, false, 1 / 50.0,
			"Vorkath kills", 60, 0, null, 1);
		assertEquals("1/50 a kill  ·  due by now", MonsterView.detail(head));
	}

	@Test
	public void ratesLineOnlyForMonstersWithoutACounter()
	{
		assertNull(Format.detail("Killing gargoyles"));
		assertEquals("On task", Format.detail("Killing wyrms (on task)"));
	}

	@Test
	public void lootLinesReadPlainly()
	{
		assertEquals("0.77x the table", MonsterView.ratioLine(0.773));
		assertEquals("1.3x the table", MonsterView.ratioLine(1.31));
		assertEquals("12x the table", MonsterView.ratioLine(12.2));
		assertEquals("none yet", MonsterView.ratioLine(0));
		assertEquals("850", Format.gp(850));
		assertEquals("12.4k", Format.gp(12_400));
		assertEquals("13.8k", Format.gp(13_819.9));
		assertEquals("312k", Format.gp(312_000));
		assertEquals("3.25M", Format.gp(3_250_000));
		assertEquals("150M", Format.gp(150_000_000));
		assertEquals("1.2B", Format.gp(1_200_000_000));
		assertEquals("-", Format.gp(-1));
	}
}
