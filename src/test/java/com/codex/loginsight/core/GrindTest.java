package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.codex.loginsight.data.Activity;
import com.codex.loginsight.data.ItemOdds;
import com.codex.loginsight.data.LogData;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;

/** The in-game card for the page you are grinding. */
public class GrindTest
{
	private static final int PET = 1;
	private static final int HEAD = 2;
	private static final int SHIELD = 3;

	private final LogData data = data();
	private final LogStructure structure = new LogStructure(Collections.singletonList(new LogStructure.Tab("Bosses",
		Collections.singletonList(new LogStructure.Page("Bosses", "Beast", Arrays.asList(PET, HEAD, SHIELD))))), names());

	private static LogData data()
	{
		Activity beast = new Activity(0, "Killing the beast", Activity.Category.COMBAT, 50, 40, 0, Arrays.asList(
			new Activity.Drop(0, PET, 1000, 0), new Activity.Drop(0, HEAD, 100, 0), new Activity.Drop(0, SHIELD, 50, 0)));
		Map<Integer, ItemOdds> odds = new HashMap<>();
		odds.put(PET, new ItemOdds(Collections.singletonList(new ItemOdds.Roll("Beast kills", 1 / 1000.0, 1)), 0));
		odds.put(HEAD, new ItemOdds(Collections.singletonList(new ItemOdds.Roll("Beast kills", 1 / 100.0, 1)), 0));
		odds.put(SHIELD, new ItemOdds(Collections.singletonList(new ItemOdds.Roll("Beast kills", 1 / 50.0, 1)), 0));
		return new LogData(Collections.singletonList(beast), Collections.singletonList("Beast kills"), odds,
			Collections.singleton(PET));
	}

	private static Map<Integer, String> names()
	{
		Map<Integer, String> names = new HashMap<>();
		names.put(PET, "Beastling");
		names.put(HEAD, "Beast head");
		names.put(SHIELD, "Beast shield");
		return names;
	}

	@Test
	public void theCardWatchesTheDriestSlotUnlessOneIsWanted()
	{
		Progress p = new Progress();
		p.setCounter("Beast kills", 120);
		p.addOwned(SHIELD);
		p.markPageRead("Beast", 1);
		Session session = new Session(new HashSet<>(p.getOwned()), 0);
		session.observe("Beast kills", 100, true);
		session.observe("Beast kills", 120, true);
		Report report = new Analyst(data).analyse(structure, p, false, true, -1, -1).withSession(session.read(data, p, 60_000));
		Grind grind = Grind.of(report, "Beast kills", 1_000);
		assertEquals("Beast", grind.getPage());
		assertEquals(120, grind.getCount());
		// The first kill message of a session counts its own kill too.
		assertEquals(21, grind.getAdded());
		assertEquals(2, grind.getMissing());
		// At 120 kills the 1 in 100 head is far drier than the 1 in 1,000 pet.
		assertEquals(HEAD, grind.getFocus().getItemId());
		assertFalse(grind.isFocusWanted());
		assertTrue(Double.isFinite(grind.getHoursToNext()));

		// A wanted slot is the one watched, however dry the rest.
		p.toggleWanted(PET);
		Report wanting = new Analyst(data).analyse(structure, p, false, true, -1, -1);
		Grind hunt = Grind.of(wanting, "Beast kills", 1_000);
		assertEquals(PET, hunt.getFocus().getItemId());
		assertTrue(hunt.isFocusWanted());
		assertEquals(0, hunt.getAdded());
	}

	@Test
	public void theCardStepsAsideAfterAWhileAndOnlyForAPageItKnows()
	{
		Progress p = new Progress();
		p.setCounter("Beast kills", 10);
		p.markPageRead("Beast", 1);
		Report report = new Analyst(data).analyse(structure, p, false, true, -1, -1);
		Grind grind = Grind.of(report, "Beast kills", 1_000);
		assertTrue(grind.isShowing(1_000 + Grind.SHOW_MS - 1));
		assertFalse(grind.isShowing(1_000 + Grind.SHOW_MS));
		assertNull(Grind.of(report, "Imp kills", 1_000));
		assertNull(Grind.of(report, null, 1_000));
		assertNull(Grind.of(null, "Beast kills", 1_000));
		// A finished page has nothing left to grind.
		p.addOwned(PET);
		p.addOwned(HEAD);
		p.addOwned(SHIELD);
		assertNull(Grind.of(new Analyst(data).analyse(structure, p, false, true, -1, -1), "Beast kills", 1_000));
		assertSame(null, Grind.of(Report.loggedOut(), "Beast kills", 0));
	}

	@Test
	public void noCardUntilWhatYouOwnOnThePageIsKnown()
	{
		// A first kill before the log was ever opened: every slot reads as missing, so the card would claim
		// "3 slots left" and a dry slot the account may have owned for years.
		Progress p = new Progress();
		p.setCounter("Beast kills", 300);
		assertNull(Grind.of(new Analyst(data).analyse(structure, p, false, true, -1, -1), "Beast kills", 1_000));
		// A full read of the log is as good as opening the page.
		p.setFullReadAt(5);
		assertEquals("Beast", Grind.of(new Analyst(data).analyse(structure, p, false, true, -1, -1), "Beast kills", 1_000).getPage());
	}
}
