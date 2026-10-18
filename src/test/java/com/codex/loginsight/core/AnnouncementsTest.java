package com.codex.loginsight.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.codex.loginsight.core.Announcements.Channel;
import org.junit.Test;

public class AnnouncementsTest
{
	@Test
	public void casketWithTwoNewSlotsIsCountedOnceEach()
	{
		// Chat announces both at once; the pop-ups queue and arrive one after another.
		Announcements a = new Announcements();
		assertTrue(a.fresh("Ranger boots", Channel.CHAT, 0));
		assertTrue(a.fresh("Robin hood hat", Channel.CHAT, 0));
		assertFalse(a.fresh("Ranger boots", Channel.POPUP, 3_000));
		assertFalse(a.fresh("Robin hood hat", Channel.POPUP, 14_000));
	}

	@Test
	public void popupOnlyPlayersStillGetEverySlot()
	{
		Announcements a = new Announcements();
		assertTrue(a.fresh("Ranger boots", Channel.POPUP, 0));
		assertTrue(a.fresh("Robin hood hat", Channel.POPUP, 5_000));
	}

	@Test
	public void popupBeforeChatIsAlsoMatched()
	{
		Announcements a = new Announcements();
		assertTrue(a.fresh("Pet snakeling", Channel.POPUP, 0));
		assertFalse(a.fresh("pet snakeling", Channel.CHAT, 600));
		// A later, separate announcement of a same-named slot is new again.
		assertTrue(a.fresh("Pet snakeling", Channel.POPUP, 1_000));
	}

	@Test
	public void oldAnnouncementsExpire()
	{
		Announcements a = new Announcements();
		assertTrue(a.fresh("Dragon pickaxe", Channel.CHAT, 0));
		assertTrue(a.fresh("Dragon pickaxe", Channel.POPUP, Announcements.WINDOW_MS + 1));
	}
}
