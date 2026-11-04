package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.loginsight.data.LogData;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The right-click "Log" option's monster to log mapping, against the bundled tables. NPC names and ids are the ones
 * RuneLite's gameval NpcID gives them; the Catacombs of Kourend ids are also on the OSRS Wiki's Abyssal demon and Dust
 * devil pages.
 */
public class BestiaryTest
{
	private static LogData data;
	private static Bestiary bestiary;
	private static Analyst analyst;

	@BeforeClass
	public static void load() throws IOException
	{
		data = LogData.load(new Gson());
		analyst = new Analyst(data);
		bestiary = analyst.bestiary();
	}

	private static String activity(Bestiary.Target target, int i)
	{
		return data.activity(target.getActivities().get(i)).getName();
	}

	@Test
	public void everyNamedActivityAndCounterIsBundled()
	{
		assertTrue(bestiary.knowsEveryActivity());
		for (String label : Bestiary.COUNTERS.values())
		{
			assertTrue(label, data.getLabels().contains(label));
		}
	}

	@Test
	public void bossesAreFoundByTheirOwnKillCounter()
	{
		assertEquals("Zulrah kills", bestiary.lookup("Zulrah", 2042).getLabel());
		assertEquals("Callisto kills", bestiary.lookup("Callisto", 6609).getLabel());
		assertEquals("Artio kills", bestiary.lookup("Artio", 11992).getLabel());
		assertEquals("Vet'ion kills", bestiary.lookup("Vet'ion", 6611).getLabel());
		assertEquals("Dagannoth Rex kills", bestiary.lookup("Dagannoth Rex", 2267).getLabel());
		assertEquals("Thermonuclear Smoke Devil kills", bestiary.lookup("Thermonuclear smoke devil", 499).getLabel());
		// "The" in front of the name is not part of the counter.
		assertEquals("Leviathan kills", bestiary.lookup("The Leviathan", 12214).getLabel());
		assertEquals("Whisperer kills", bestiary.lookup("The Whisperer", 12204).getLabel());
		assertEquals("Hueycoatl kills", bestiary.lookup("The Hueycoatl", 14009).getLabel());
		// Names come with colour tags in menus.
		assertEquals("Vorkath kills", bestiary.lookup("<col=ffff00>Vorkath</col>", 8061).getLabel());
		assertEquals("Vorkath", bestiary.lookup("<col=ffff00>Vorkath</col>", 8061).getNpc());
	}

	@Test
	public void bossesNamedOtherThanTheirCounter()
	{
		Bestiary.Target dusk = bestiary.lookup("Dusk", 7851);
		Bestiary.Target dawn = bestiary.lookup("Dawn", 7852);
		assertEquals("Grotesque Guardian kills", dusk.getLabel());
		assertEquals(dusk.key(), dawn.key());
		assertEquals("Royal Titan kills", bestiary.lookup("Branda the Fire Queen", 12596).getLabel());
		assertEquals("Royal Titan kills", bestiary.lookup("Eldric the Ice King", 14147).getLabel());
	}

	@Test
	public void countersWithoutOddsBorrowTheActivity()
	{
		Bestiary.Target scurrius = bestiary.lookup("Scurrius", 7221);
		assertEquals("Scurrius kills", scurrius.getLabel());
		assertEquals("Killing scurrius", activity(scurrius, 0));
		Bestiary.Target nightmare = bestiary.lookup("The Nightmare", 9425);
		assertEquals("Nightmare kills", nightmare.getLabel());
		assertEquals("Killing the nightmare of ashihama (5 person team)", activity(nightmare, 0));
		assertEquals("Yama kills", bestiary.lookup("Yama", 14176).getLabel());
		// A counter that says nothing about slots, with no activity to time it, gets no option.
		assertNull(bestiary.lookup("Tempoross", 10572));
	}

	@Test
	public void monstersWithoutAKillCountUseTheirActivity()
	{
		Bestiary.Target wyrm = bestiary.lookup("Wyrm", 8610);
		assertNull(wyrm.getLabel());
		assertEquals(Collections.singletonList("Killing wyrms (on task)"), Arrays.asList(activity(wyrm, 0)));
		assertEquals("Killing gargoyles", activity(bestiary.lookup("Gargoyle", 412), 0));
		assertEquals("Killing fossil island wyverns", activity(bestiary.lookup("Spitting Wyvern", 7794), 0));
	}

	@Test
	public void versionsThatDropDifferentlyAreLeftOut()
	{
		// Only the Catacombs of Kourend abyssal demon and dust devil roll the totem pieces and shards counted.
		assertNotNull(bestiary.lookup("Abyssal demon", 7241));
		assertNull(bestiary.lookup("Abyssal demon", 415));
		assertNull(bestiary.lookup("Abyssal demon", 11239));
		assertNotNull(bestiary.lookup("Dust devil", 7249));
		assertNull(bestiary.lookup("Dust devil", 423));
		// The raid's lizardman shamans drop from the Chambers of Xeric, not the shaman table.
		assertNotNull(bestiary.lookup("Lizardman shaman", 6766));
		assertNull(bestiary.lookup("Lizardman shaman", 7573));
	}

	@Test
	public void ordinaryNpcsGetNothing()
	{
		assertNull(bestiary.lookup("Man", 3106));
		assertNull(bestiary.lookup("Banker", 1613));
		assertNull(bestiary.lookup("", 0));
		assertNull(bestiary.lookup(null, 0));
	}

	@Test
	public void zulrahShowsItsOwnSlotsFromTheLog()
	{
		// The Zulrah page as the game lists it, beside a page Zulrah drops nothing on.
		List<Integer> zulrah = Arrays.asList(12921, 13200, 13201, 12936, 12932, 12927, 12922, 12938, 6571, 12934);
		LogStructure structure = new LogStructure(Collections.singletonList(new LogStructure.Tab("Bosses", Arrays.asList(
			new LogStructure.Page("Bosses", "Vorkath", Arrays.asList(21992, 21907, 22006, 22106, 22111)),
			new LogStructure.Page("Bosses", "Zulrah", zulrah)))), Collections.emptyMap());
		Progress progress = new Progress();
		progress.setCounter("Zulrah kills", 300);
		progress.addOwned(12922);
		progress.setFullReadAt(1);
		MonsterReport report = analyst.monster(structure, progress, false, false, bestiary.lookup("Zulrah", 2042));
		assertEquals("Zulrah", report.getPage());
		assertEquals(300, report.getKills());
		assertEquals(zulrah.size(), report.total());
		assertEquals(1, report.owned());
		Report.ItemReport fang = report.getItems().get(zulrah.indexOf(12922));
		assertTrue(fang.isOwned());
		// Two rolls a kill at 1/1,024 each (the OSRS Wiki gives 1/512 per kill).
		assertEquals(1 - Math.pow(1 - 1 / 1024.0, 2), fang.getChance(), 1e-9);
		Report.ItemReport jar = report.getItems().get(zulrah.indexOf(12936));
		assertEquals(1 / 3000.0, jar.getChance(), 1e-6);
		assertEquals(Math.pow(1 - 1 / 3000.0, 300), jar.getDryChance(), 1e-6);
		assertNotNull(report.getLuck());
	}

	@Test
	public void vorkathsHeadComesByTheFiftiethKill()
	{
		LogStructure structure = new LogStructure(Collections.singletonList(new LogStructure.Tab("Bosses",
			Collections.singletonList(new LogStructure.Page("Bosses", "Vorkath", Arrays.asList(21992, 21907, 22006, 22106, 22111))))),
			Collections.emptyMap());
		Progress progress = new Progress();
		progress.setCounter("Vorkath kills", 45);
		progress.addOwned(21992);
		progress.addOwned(22006);
		progress.addOwned(22106);
		progress.addOwned(22111);
		progress.setFullReadAt(1);
		MonsterReport report = analyst.monster(structure, progress, false, false, bestiary.lookup("Vorkath", 8061));
		// Only the head is missing, and it is guaranteed on the 50th kill: at most 5 kills to go.
		assertEquals(21907, report.getNextItem());
		assertTrue(report.getKillsToNext() <= 5 + 1e-9);
		assertTrue(report.getKillsToNext() > 4);
		assertFalse(report.isComplete());
	}
}
