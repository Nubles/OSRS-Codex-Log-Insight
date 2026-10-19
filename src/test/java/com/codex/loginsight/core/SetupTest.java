package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class SetupTest
{
	private static Report.PageReport page(String name, int owned, int count, String label, boolean read)
	{
		return new Report.PageReport("Bosses", name, owned, 10, Collections.emptyList(), count, label, null,
			Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, null, 0, read, Collections.emptyList());
	}

	private static Report report(int officialOwned, int knownOwned, long fullReadAt, List<Report.PageReport> pages)
	{
		return new Report(true, true, officialOwned, 1500, knownOwned, 1500, fullReadAt, 0, pages.size(), 0, null,
			Double.POSITIVE_INFINITY, 0, false, pages, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	}

	@Test
	public void aFreshInstallHasEverythingLeft()
	{
		Setup setup = Setup.of(report(-1, 0, 0, Collections.singletonList(page("Vorkath", 0, -1, "Vorkath", false))), 3);
		assertFalse(setup.isLogRead());
		assertFalse(setup.isDone());
	}

	@Test
	public void pagesYouHaveFindsOnButNoCountAreNamedMostFindsFirst()
	{
		List<Report.PageReport> pages = Arrays.asList(
			page("Zulrah", 2, -1, "Zulrah", false),
			page("Vorkath", 5, -1, "Vorkath", false),
			page("Giant Mole", 0, -1, "Giant Mole", false), // never played: nothing to credit
			page("Barrows Chests", 9, 300, "Barrows Chests", false), // counted already
			page("Miscellaneous", 4, -1, null, false), // no kill count to read on this page
			page("Hespori", 1, -1, "Hespori", true), // opened, and still showed none
			page("Kraken", 1, -1, "Kraken", false),
			page("Cerberus", 3, -1, "Cerberus", false));
		Setup setup = Setup.of(report(30, 30, 1, pages), 1);
		assertTrue(setup.isLogRead());
		assertEquals(Arrays.asList("Vorkath", "Cerberus", "Zulrah"), names(setup.getToOpen()));
		assertEquals(4, setup.getToOpenTotal());
		assertEquals(1, setup.getPagesCounted());
		assertEquals(7, setup.getPagesCountable());
		assertFalse(setup.isDone());
	}

	@Test
	public void notificationsOffIsTheOnlyThingLeft()
	{
		Setup setup = Setup.of(report(30, 30, 1, Collections.singletonList(page("Vorkath", 3, 120, "Vorkath", true))), 0);
		assertTrue(setup.noticesOff());
		assertFalse(setup.isDone());
		assertTrue(Setup.of(report(30, 30, 1, Collections.singletonList(page("Vorkath", 3, 120, "Vorkath", true))), 2).isDone());
	}

	@Test
	public void anUnreadSettingIsNotHeldAgainstYou()
	{
		Setup setup = Setup.of(report(30, 30, 1, Collections.emptyList()), Setup.NOTICES_UNKNOWN);
		assertFalse(setup.noticesKnown());
		assertFalse(setup.noticesOff());
		assertTrue(setup.isDone());
	}

	@Test
	public void slotsGainedElsewhereAskForAnotherRead()
	{
		// The game counts more slots than Log Insight knows: some came on mobile or with the plugin off.
		assertFalse(Setup.of(report(42, 30, 1, Collections.emptyList()), 3).isLogRead());
	}

	@Test
	public void theSettingTravelsWithTheReport()
	{
		Report r = report(30, 30, 1, Collections.emptyList()).withNotices(0);
		assertTrue(r.setup().noticesOff());
		assertTrue(r.withSession(null).setup().noticesOff());
	}

	private static List<String> names(List<Report.PageReport> pages)
	{
		String[] out = new String[pages.size()];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = pages.get(i).getName();
		}
		return Arrays.asList(out);
	}
}
