package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * Pins {@code codexloginsight/storyV1} (contract N1), which Annals and Home read to tell the account's story.
 * {@code storyV1.json} beside this test is the writer's exact bytes, for readers to keep a byte copy of.
 */
public class StoryShapeTest
{
	private static final long T = 1_790_000_000_000L;
	private static final double FANG = 1 - Math.pow(1 - 1 / 1024.0, 2);

	private static Story written()
	{
		Story story = new Story();
		story.add(Story.slot(T, 12922, "Tanzanite fang", "Zulrah kills", 612, FANG, 0.3, false), T);
		story.add(Story.slot(T + 1000, 12936, "Jar of swamp", "Zulrah kills", 1830, 1 / 3000.0, 0.54, true), T + 1000);
		story.add(Story.pageDone(T + 1000, "Zulrah", 8), T + 1000);
		story.add(Story.slots(T + 2000, 500), T + 2000);
		return story;
	}

	@Test
	public void theStoryIsWrittenByteForByte() throws IOException
	{
		assertEquals(fixture("storyV1.json"), new Gson().toJson(written()));
	}

	@Test
	public void itReadsBackAsWritten() throws IOException
	{
		Story read = Story.load(new Gson(), fixture("storyV1.json"));
		assertNotNull(read);
		assertEquals(4, read.getMoments().size());
		assertEquals("goal", read.getMoments().get(1).getKind());
		assertEquals("Wanted, and found: Jar of swamp at 1,830 Zulrah kc, luckier than 54% of players",
			read.getMoments().get(1).getText());
		assertEquals("1/3000", read.getMoments().get(1).getFigure());
		assertEquals("item:12936", read.getMoments().get(1).getRef());
		assertEquals("page:Zulrah", read.getMoments().get(2).getRef());
	}

	@Test
	public void newestLastAndAtMostTwoHundred()
	{
		Story story = new Story();
		for (int i = Story.MAX + 30; i > 0; i--)
		{
			story.add(Story.slots(T + i, i * 100), T);
		}
		assertEquals(Story.MAX, story.getMoments().size());
		assertEquals("the oldest went", T + 31, story.getMoments().get(0).getT());
		assertEquals(T + Story.MAX + 30, story.getMoments().get(Story.MAX - 1).getT());
		assertFalse("the same moment twice is kept once", story.add(Story.slots(T + 31, 3100), T));
	}

	@Test
	public void textIsHeldToOneHundredTwentyCharacters()
	{
		Story story = new Story();
		String name = "A slot with a name long enough to run past the contract's limit of one hundred and twenty characters";
		story.add(Story.slot(T, 1, name, "Zulrah kills", 1_234_567, 0.001, 0.5, true), T);
		String text = story.getMoments().get(0).getText();
		assertEquals(Story.MAX_TEXT, text.length());
		assertTrue(text.endsWith("…"));
	}

	@Test
	public void aSlotWithoutOddsOrCountSaysWhatItCan()
	{
		Story.Moment m = Story.slot(T, 0, "Mysterious page", null, 0, 0, -1, false);
		assertEquals("New slot: Mysterious page", m.getText());
		assertNull(m.getFigure());
		assertNull("a name several slots share names no item", m.getRef());
	}

	@Test
	public void onlyASlotOrTwoAtATimeCountsAsAHundredth()
	{
		assertEquals(500, Story.hundredPassed(499, 500));
		assertEquals(0, Story.hundredPassed(500, 501));
		assertEquals(0, Story.hundredPassed(-1, 500));
		assertEquals(0, Story.hundredPassed(500, 499));
	}

	@Test
	public void damagedOrNewerSavesNeverThrow()
	{
		Gson gson = new Gson();
		for (String bad : new String[]{null, "", "null", "[]", "{", "{\"moments\":7}", "{\"moments\":[null,{\"t\":-5}]}",
			"{\"version\":\"x\"}", "{\"moments\":[{\"t\":1,\"kind\":\"drop\"}]}"})
		{
			Story story = Story.load(gson, bad);
			assertNotNull(bad, story);
			assertTrue(bad, story.getMoments().isEmpty());
		}
		assertNull("a newer version under this key is never written over", Story.load(gson, "{\"version\":2,\"moments\":[]}"));
		Story kept = Story.load(gson, "{\"version\":1,\"moments\":[{\"t\":5,\"kind\":\"drop\",\"text\":\"ok\"},{\"t\":\"x\"}]}");
		assertEquals(1, kept.getMoments().size());
	}

	private static String fixture(String name) throws IOException
	{
		try (InputStream in = StoryShapeTest.class.getResourceAsStream(name))
		{
			if (in == null)
			{
				throw new IOException("No fixture " + name);
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int n;
			while ((n = in.read(buffer)) > 0)
			{
				out.write(buffer, 0, n);
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
		}
	}
}
