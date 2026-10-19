package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.codex.loginsight.data.Activity;
import com.codex.loginsight.data.ItemOdds;
import com.codex.loginsight.data.LogData;
import com.codex.loginsight.ui.Format;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * The saved progress is read back from empty, cut-off, wrong-type, huge, negative, future-version and garbage values
 * without throwing, keeps whatever good part there is, and says when anything was left out so the saved text is kept
 * aside rather than written over. A whole log, owned or not, at absurd kill counts is analysed quickly and sensibly.
 */
public class DamagedDataTest
{
	private static final Gson GSON = new Gson();

	static List<String> garbage()
	{
		StringBuilder deep = new StringBuilder();
		for (int i = 0; i < 200; i++)
		{
			deep.append("{\"a\":");
		}
		StringBuilder longText = new StringBuilder("\"");
		for (int i = 0; i < 100_000; i++)
		{
			longText.append('x');
		}
		longText.append('"');
		return Arrays.asList(null, "", " ", "null", "{", "[", "{\"owned\":", "\"", "garbage", "{}", "[]", "0", "-1", "true",
			"NaN", "1e999999", "[null]", "{\"owned\":5}", "{\"owned\":[null,\"x\",1e100000000,-3]}", "{\"counters\":{\"Vorkath kills\":-7}}",
			"{\"finds\":[null,5,{}]}", "{\"trophies\":[null,{\"chance\":\"rare\"}]}", "{\"wanted\":[null,null,1,1,2,3,4,5,6,7,8]}",
			"{\"pace\":{\"Vorkath kills\":null,\"Zulrah kills\":{\"secondsPerCount\":-5,\"samples\":3}}}",
			"{\"pageLabels\":{\"Vorkath\":[null]}}", "{\"version\":9,\"slots\":{\"owned\":[1]}}", "\u0000\u0001", "<html>",
			deep.toString(), longText.toString());
	}

	private static Progress progress()
	{
		Progress p = new Progress();
		p.addOwned(22006);
		p.addOwned(21992);
		p.setCounter("Vorkath kills", 215);
		p.setCounter("Zulrah kills", 480);
		p.toggleWanted(11286);
		p.addFind(new Progress.Find(21992, "Vorki", "Vorkath", "Vorkath kills", 120, 1 / 3000.0, 0.96, 1_760_000_000_000L));
		p.paceFor("Vorkath kills").record(10, 1_000_000);
		p.paceFor("Vorkath kills").record(11, 1_150_000);
		return p;
	}

	@Test
	public void progressSurvivesAnySavedValue()
	{
		for (String value : garbage())
		{
			SavedJson.Read<Progress> read = Progress.load(GSON, value);
			Progress p = read.getValue() == null ? new Progress() : read.getValue();
			// Every reader the panel and the analysis use must work on what was loaded.
			p.copy();
			for (Integer id : p.getOwned())
			{
				assertNotNull(value, id);
			}
			for (Map.Entry<String, Integer> e : p.getCounters().entrySet())
			{
				assertTrue(value, e.getKey() != null && e.getValue() != null && e.getValue() >= 0);
			}
			for (Progress.Find f : p.getFinds())
			{
				assertNotNull(value, f);
			}
			p.getTrophies();
			assertTrue(value, p.getWanted().size() <= Progress.MAX_WANTED && !p.getWanted().contains(null));
			new Session(p.getOwned(), 0);
			boolean saved = value != null && !value.trim().isEmpty();
			if (saved && read.getValue() == null)
			{
				assertTrue(value, read.isDamaged());
			}
		}
	}

	@Test
	public void oneBadFieldNeverLosesTheLog()
	{
		// One field of the wrong type used to fail the whole save, and the next change saved an empty log over it.
		JsonObject saved = GSON.toJsonTree(progress()).getAsJsonObject();
		saved.addProperty("fullReadAt", "yesterday");
		saved.getAsJsonArray("owned").add("not an item");
		saved.getAsJsonObject("counters").addProperty("Zulrah kills", "lots");
		JsonArray finds = saved.getAsJsonArray("finds");
		finds.add(5);
		SavedJson.Read<Progress> read = Progress.load(GSON, GSON.toJson(saved));
		assertTrue(read.isDamaged());
		Progress p = read.getValue();
		assertNotNull(p);
		assertEquals(new LinkedHashSet<>(Arrays.asList(22006, 21992)), new LinkedHashSet<>(p.getOwned()));
		assertEquals(Integer.valueOf(215), p.counter("Vorkath kills"));
		assertEquals(null, p.counter("Zulrah kills"));
		assertEquals(Collections.singletonList(11286), p.getWanted());
		assertEquals(1, p.getFinds().size());
		assertEquals(1, p.pace("Vorkath kills").getSamples());
		assertFalse(Progress.load(GSON, GSON.toJson(progress())).isDamaged());
	}

	@Test
	public void absurdFiguresAreBroughtBackToSense()
	{
		Progress p = Progress.load(GSON, "{\"owned\":[1,null,2],\"counters\":{\"Vorkath kills\":-7,\"Zulrah kills\":2147483647},"
			+ "\"wanted\":[null,1,3,3,4,5,6,7,8,9],\"pace\":{\"Zulrah kills\":{\"secondsPerCount\":-5,\"samples\":3},\"Vorkath kills\":null},"
			+ "\"finds\":[null,{\"itemId\":4,\"chance\":0.001,\"luckier\":0.5}],\"trophies\":[null]}").getValue();
		assertFalse(p.getOwned().contains(null));
		assertEquals(null, p.counter("Vorkath kills"));
		assertEquals(Integer.valueOf(Integer.MAX_VALUE), p.counter("Zulrah kills"));
		// Owned slots can't be wanted; the list keeps its own limit.
		assertEquals(Arrays.asList(3, 4, 5, 6, 7, 8), p.getWanted());
		assertEquals(0, p.pace("Zulrah kills") == null ? 0 : p.pace("Zulrah kills").perHour(), 0);
		assertEquals(1, p.getFinds().size());
		assertEquals(1, p.getTrophies().size());
	}

	@Test
	public void aGuaranteedSlotStillMissingReadsAsOverdueNotAHundredPercentDry()
	{
		// Vorkath's head comes by the 50th kill; at 215 kills without it the row read "100% dry" beside ">99% dry".
		ItemOdds head = new ItemOdds(Collections.singletonList(new ItemOdds.Roll("Vorkath kills", 1 / 50.0, 1)), 50);
		double stillWaiting = head.dryChance(Collections.singletonMap("Vorkath kills", 215));
		assertEquals("Overdue", Format.dry(stillWaiting));
		// A slot merely astronomically dry is still ">99% dry", never "Overdue" or "100% dry".
		ItemOdds rare = new ItemOdds(Collections.singletonList(new ItemOdds.Roll("Zulrah kills", 1 / 50.0, 1)), 0);
		double dry = rare.dryChance(Collections.singletonMap("Zulrah kills", 200_000));
		assertTrue(dry > 0);
		assertEquals(">99% dry", Format.dry(dry));
		assertEquals(">99% dry", Format.dry(1e-9));
	}

	@Test
	public void aWholeLogAtAbsurdCountsIsAnalysedQuickly() throws Exception
	{
		LogData data = LogData.load(GSON);
		// Every slot the tables know, one page per activity.
		List<LogStructure.Page> pages = new ArrayList<>();
		Map<Integer, String> names = new HashMap<>();
		Set<Integer> all = new LinkedHashSet<>();
		for (Activity a : data.getActivities())
		{
			List<Integer> items = new ArrayList<>();
			for (Activity.Drop d : a.getDrops())
			{
				if (all.add(d.getItemId()))
				{
					items.add(d.getItemId());
					names.put(d.getItemId(), "Item " + d.getItemId());
				}
			}
			if (!items.isEmpty())
			{
				pages.add(new LogStructure.Page("Bosses", a.getName() + " " + a.getIndex(), items));
			}
		}
		LogStructure structure = new LogStructure(Collections.singletonList(new LogStructure.Tab("Bosses", pages)), names);
		assertTrue(all.size() > 1_000);
		Analyst analyst = new Analyst(data);

		Progress fresh = new Progress();
		Progress maxed = new Progress();
		Progress absurd = new Progress();
		for (int id : all)
		{
			maxed.addOwned(id);
		}
		for (String label : data.getLabels())
		{
			maxed.setCounter(label, 5_000);
			absurd.setCounter(label, Integer.MAX_VALUE);
		}
		for (Progress p : Arrays.asList(fresh, maxed, absurd))
		{
			analyst.analyse(structure, p, false, true, 0, all.size());
			long start = System.nanoTime();
			Progress copy = p.copy();
			long copyMs = (System.nanoTime() - start) / 1_000_000;
			start = System.nanoTime();
			Report report = analyst.analyse(structure, copy, false, true, 0, all.size());
			long ms = (System.nanoTime() - start) / 1_000_000;
			System.out.println(all.size() + " slots, " + p.getOwned().size() + " owned: copy " + copyMs + " ms, analysis " + ms + " ms");
			assertTrue(copyMs + " ms", copyMs < 10);
			assertTrue(ms + " ms", ms < 2_000);
			assertTrue(Double.isFinite(report.getHoursLeft()) && report.getHoursLeft() >= 0);
			for (Report.ItemReport item : report.getDriest())
			{
				assertFalse(Format.dry(item.getDryChance()), Format.dry(item.getDryChance()).startsWith("-"));
			}
		}
		// A long save round-trips quickly.
		String json = GSON.toJson(maxed);
		long start = System.nanoTime();
		assertEquals(all.size(), Progress.load(GSON, json).getValue().getOwned().size());
		assertTrue((System.nanoTime() - start) / 1_000_000 < 50);
	}
}
