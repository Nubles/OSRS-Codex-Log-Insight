package com.codex.loginsight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codex.loginsight.core.Progress;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** Figures that read the same way everywhere, and a card that never shows an empty plinth. */
public class CohesionTest
{
	@Test
	public void aDropThatIsAlmostCertainNeverReadsAsCertain()
	{
		// 1/1.7 still missing after 213 chests: one minus the share still waiting is exactly 1.0 in a double.
		double stillWaiting = Math.pow(1 - 1 / 1.7, 213);
		assertEquals(1.0, 1 - stillWaiting, 0);
		assertEquals(">99% dry", Format.dry(stillWaiting));
		assertEquals(">99%", Format.have(stillWaiting));
		// A guaranteed drop past its guarantee is certain.
		assertEquals("100% dry", Format.dry(0));
	}

	@Test
	public void expectedSlotsDropTrailingZeros()
	{
		assertEquals("9", Format.slots(9.0));
		assertEquals("9", Format.slots(9.04));
		assertEquals("2.4", Format.slots(2.4));
		assertEquals("0.5", Format.slots(0.5));
		assertEquals("0.48", Format.slots(0.48));
		assertEquals("2", Format.slots(1.999));
		assertEquals("74", Format.slots(74.2));
		assertEquals("10", Format.slots(9.96));
	}

	@Test
	public void theDialsFirstDayLineFitsItsCard()
	{
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		int width = g.getFontMetrics(Theme.small()).stringWidth(LuckDial.NOT_READ_LINE);
		g.dispose();
		// The dial's card is 209 px wide at sidebar width, less its padding.
		assertTrue(width + " px", width <= 190);
	}

	private static Progress.Find find(int id, String name)
	{
		return new Progress.Find(id, name, "Vorkath", "Vorkath kills", 300, 1 / 5000.0, 0.9, 0);
	}

	@Test
	public void theFindsCardHeadsWithTheNameAndCountsTheLogOnce()
	{
		List<Progress.Find> finds = Arrays.asList(find(22006, "Skeletal visage"), find(21992, "Vorki"));
		FindsShare.Spec named = FindsShare.Spec.of(finds, " Gilded Quill ", 872, 1690, 0, ZoneOffset.UTC);
		assertEquals("Gilded Quill", named.heading());
		assertEquals("Finest finds  ·  872 of 1,690 slots", named.subheading());
		FindsShare.Spec anonymous = FindsShare.Spec.of(finds, null, 872, 1690, 0, ZoneOffset.UTC);
		assertEquals("Finest finds", anonymous.heading());
		assertEquals("872 of 1,690 slots", anonymous.subheading());
		// Before the log is read its size is unknown: no "0 of 0".
		assertEquals("Finest finds", FindsShare.Spec.of(finds, "Gilded Quill", 0, 0, 0, ZoneOffset.UTC).subheading());
		assertNull(FindsShare.Spec.of(Collections.emptyList(), "Gilded Quill", 1, 2, 0, ZoneOffset.UTC));
		assertEquals(Arrays.asList(22006, 21992), named.itemIds());
		assertEquals("codex-finds-1970-01-01.png", FindsShare.fileName(0, ZoneOffset.UTC));
		BufferedImage card = FindsShare.render(named, Collections.emptyMap());
		assertEquals(FindsShare.WIDTH * FindsShare.SCALE, card.getWidth());
	}

	/** Pictures that arrive when told to, like the item manager's. */
	private static final class SlowIcons implements ItemIcons
	{
		final Map<Integer, BufferedImage> images = new java.util.HashMap<>();
		final Set<BufferedImage> arrived = Collections.newSetFromMap(new IdentityHashMap<>());
		final List<Runnable> waiting = new ArrayList<>();
		int asked;

		@Override
		public BufferedImage icon(int itemId, Runnable onLoaded)
		{
			asked++;
			waiting.add(onLoaded);
			return images.computeIfAbsent(itemId, id -> new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB));
		}

		@Override
		public boolean ready(BufferedImage image)
		{
			return arrived.contains(image);
		}

		void arrive()
		{
			arrived.addAll(images.values());
			for (Runnable r : waiting)
			{
				r.run();
			}
		}
	}

	private static void flush() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> { });
		SwingUtilities.invokeAndWait(() -> { });
	}

	@Test
	public void theCardWaitsForEveryPictureAndIsMadeOnce() throws Exception
	{
		SlowIcons icons = new SlowIcons();
		List<Map<Integer, BufferedImage>> made = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> IconWait.forItems(icons, Arrays.asList(22006, 21992, 22006), 60_000, made::add));
		flush();
		assertEquals(0, made.size());
		// The same item twice is asked for once.
		assertEquals(2, icons.asked);
		icons.arrive();
		flush();
		assertEquals(1, made.size());
		assertEquals(2, made.get(0).size());
		// A late second callback does not make a second card.
		icons.arrive();
		flush();
		assertEquals(1, made.size());
	}

	@Test
	public void aPictureThatNeverComesIsGivenUpOn() throws Exception
	{
		SlowIcons icons = new SlowIcons();
		List<Map<Integer, BufferedImage>> made = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> IconWait.forItems(icons, Collections.singletonList(22006), 50, made::add));
		Thread.sleep(400);
		flush();
		assertEquals(1, made.size());
		assertTrue(made.get(0).isEmpty());
	}

	@Test
	public void aClosedPanelOnlyMarksItsTabsStale() throws Exception
	{
		com.codex.loginsight.core.Report report = com.codex.loginsight.core.Report.loggedOut();
		LogInsightPanel[] panel = new LogInsightPanel[1];
		SwingUtilities.invokeAndWait(() ->
		{
			panel[0] = new LogInsightPanel((id, cb) -> null);
			panel[0].update(report, null);
		});
		// Closed: the report waits rather than rebuilding a page nobody sees.
		assertTrue(panel[0].isStale(0));
		SwingUtilities.invokeAndWait(() -> panel[0].onActivate());
		assertTrue(!panel[0].isStale(0));
		SwingUtilities.invokeAndWait(() -> panel[0].update(report, null));
		// Open: the tab in view is rebuilt at once, the rest wait.
		assertTrue(!panel[0].isStale(0));
		assertTrue(panel[0].isStale(2));
	}

	@Test
	public void aFindWithNoPictureAtAllDoesNotHoldTheCardUp() throws Exception
	{
		List<Map<Integer, BufferedImage>> made = new ArrayList<>();
		ItemIcons none = (id, cb) -> null;
		SwingUtilities.invokeAndWait(() -> IconWait.forItems(none, Collections.singletonList(22006), 60_000, made::add));
		assertEquals(1, made.size());
	}

	@Test
	public void theFirstDayVsAverageNoteFitsItsTile()
	{
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		int width = g.getFontMetrics(Theme.small()).stringWidth(LedgerView.NO_COUNTS);
		g.dispose();
		// A tile is about 97 px at sidebar width and its note gets 16 px less: "needs kill counts" (83 px) was cut.
		assertTrue(width + " px", width <= 80);
	}

	@Test
	public void aCardStillWaitingWhenThePluginStopsIsNeverMade() throws Exception
	{
		SlowIcons icons = new SlowIcons();
		List<Map<Integer, BufferedImage>> made = new ArrayList<>();
		IconWait[] wait = new IconWait[1];
		SwingUtilities.invokeAndWait(() -> wait[0] = IconWait.forItems(icons, Collections.singletonList(22006), 50, made::add));
		// The plugin stops before the picture comes: the give-up timer must not go on to save or copy a card.
		SwingUtilities.invokeAndWait(() -> wait[0].cancel());
		Thread.sleep(300);
		icons.arrive();
		flush();
		assertEquals(0, made.size());
	}
}
