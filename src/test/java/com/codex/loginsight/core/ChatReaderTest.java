package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import org.junit.Test;

public class ChatReaderTest
{
	private final ChatReader reader = new ChatReader(Arrays.asList(
		"Vorkath kills", "Chambers of Xeric completions", "Hard clues completed", "Barrows Chests opened",
		"Theatre of Blood (Hard) completions", "Corrupted Gauntlet completion count", "Kree'arra kills"));

	@Test
	public void readsBossKillCounts()
	{
		ChatReader.Count count = reader.count("Your Vorkath kill count is: <col=ff0000>1,203</col>.");
		assertEquals("Vorkath kills", count.label);
		assertEquals(1203, count.value);
		assertEquals("Kree'arra kills", reader.count("Your Kree'arra kill count is: 50.").label);
	}

	@Test
	public void readsRaidsAndSpecialNames()
	{
		ChatReader.Count cox = reader.count("Your completed Chambers of Xeric count is: <col=ff0000>87</col>.");
		assertEquals("Chambers of Xeric completions", cox.label);
		assertEquals(87, cox.value);
		assertEquals("Theatre of Blood (Hard) completions",
			reader.count("Your completed Theatre of Blood: Hard Mode count is: 12.").label);
		assertEquals("Barrows Chests opened", reader.count("Your Barrows chest count is: 310.").label);
		assertEquals("Corrupted Gauntlet completion count",
			reader.count("Your Corrupted Gauntlet completion count is: 44.").label);
	}

	@Test
	public void readsClueCounts()
	{
		ChatReader.Count clues = reader.count("You have completed 312 hard Treasure Trails.");
		assertEquals("Hard clues completed", clues.label);
		assertEquals(312, clues.value);
	}

	@Test
	public void ignoresOtherMessages()
	{
		assertNull(reader.count("Your Unknown Beast kill count is: 4."));
		assertNull(reader.count("Welcome to Old School RuneScape."));
	}

	@Test
	public void readsNewSlots()
	{
		assertEquals("Vorki", ChatReader.newSlot("New item added to your collection log: <col=ef1020>Vorki</col>"));
		assertNull(ChatReader.newSlot("You feel something weird sneaking into your backpack."));
	}

	@Test
	public void readsPageCounters()
	{
		ChatReader.Count line = ChatReader.pageCounter("Vorkath kills: <col=ff0000>312</col>");
		assertEquals("Vorkath kills", line.label);
		assertEquals(312, line.value);
		assertNull(ChatReader.pageCounter("Obtained: <col=ff0000>3/6</col>"));
	}

	@Test
	public void baseMatchesChatCommandKeys()
	{
		assertEquals("vorkath", ChatReader.base("Vorkath kills"));
		assertEquals("chambers of xeric", ChatReader.base("Chambers of Xeric completions"));
		assertEquals("corrupted gauntlet", ChatReader.base("Corrupted Gauntlet completion count"));
	}
}
