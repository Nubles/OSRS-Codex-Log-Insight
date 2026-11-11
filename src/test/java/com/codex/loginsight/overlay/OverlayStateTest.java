package com.codex.loginsight.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import com.codex.loginsight.core.Analyst;
import com.codex.loginsight.core.Grind;
import com.codex.loginsight.core.LogStructure;
import com.codex.loginsight.core.Progress;
import com.codex.loginsight.core.Report;
import com.codex.loginsight.data.Activity;
import com.codex.loginsight.data.ItemOdds;
import com.codex.loginsight.data.LogData;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * The overlays leave the graphics they are handed exactly as they found them: RuneLite draws every overlay with the
 * same graphics, so a clip cleared or a font, stroke, composite or hint left changed would spoil the overlays drawn
 * after them (a cleared clip can break the renderer outright).
 */
public class OverlayStateTest
{
	static
	{
		System.setProperty("java.awt.headless", "true");
	}

	@Test
	public void theFindPlaqueLeavesTheGraphicsAsItFoundThem()
	{
		FindPlaque plaque = new FindPlaque();
		plaque.show(new FindPlaque.View("Tanzanite fang", "Zulrah", "1/512 at 612 Zulrah kc", "Luckier than 30% of players",
			Color.ORANGE, false, null, System.currentTimeMillis() - 500, 8_000, true));
		assertUnchanged(g -> assertNotNull("drawn", plaque.render(g)));
	}

	@Test
	public void theGrindCardLeavesTheGraphicsAsItFoundThem()
	{
		GrindCard card = new GrindCard();
		Grind grind = grind();
		assertNotNull(grind);
		card.setGrind(grind);
		assertUnchanged(g -> assertNotNull("drawn", card.render(g)));
	}

	private interface Draw
	{
		void on(Graphics2D g);
	}

	private static void assertUnchanged(Draw draw)
	{
		BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			g.setClip(new Rectangle(5, 6, 300, 200));
			g.translate(3, 4);
			Font font = new Font(Font.SERIF, Font.ITALIC, 17);
			g.setFont(font);
			Stroke stroke = new BasicStroke(2.5f);
			g.setStroke(stroke);
			Composite composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f);
			g.setComposite(composite);
			g.setColor(Color.MAGENTA);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			Shape clip = g.getClip();
			AffineTransform transform = g.getTransform();
			RenderingHints hints = (RenderingHints) g.getRenderingHints().clone();

			draw.on(g);

			assertEquals("clip", clip.getBounds2D(), g.getClip().getBounds2D());
			assertEquals("transform", transform, g.getTransform());
			assertSame("font", font, g.getFont());
			assertSame("stroke", stroke, g.getStroke());
			assertSame("composite", composite, g.getComposite());
			assertEquals("colour", Color.MAGENTA, g.getColor());
			assertEquals("hints", hints, g.getRenderingHints());
		}
		finally
		{
			g.dispose();
		}
	}

	private static Grind grind()
	{
		Activity beast = new Activity(0, "Killing the beast", Activity.Category.COMBAT, 50, 40, 0, Arrays.asList(
			new Activity.Drop(0, 1, 1000, 0), new Activity.Drop(0, 2, 100, 0)));
		Map<Integer, ItemOdds> odds = new HashMap<>();
		odds.put(1, new ItemOdds(Collections.singletonList(new ItemOdds.Roll("Beast kills", 1 / 1000.0, 1)), 0));
		odds.put(2, new ItemOdds(Collections.singletonList(new ItemOdds.Roll("Beast kills", 1 / 100.0, 1)), 0));
		LogData data = new LogData(Collections.singletonList(beast), Collections.singletonList("Beast kills"), odds,
			Collections.singleton(1));
		Map<Integer, String> names = new HashMap<>();
		names.put(1, "Beastling");
		names.put(2, "Beast head");
		LogStructure structure = new LogStructure(Collections.singletonList(new LogStructure.Tab("Bosses",
			Collections.singletonList(new LogStructure.Page("Bosses", "Beast", Arrays.asList(1, 2))))), names);
		Progress p = new Progress();
		p.setCounter("Beast kills", 120);
		p.markPageRead("Beast", 1);
		Report report = new Analyst(data).analyse(structure, p, false, true, -1, -1);
		return Grind.of(report, "Beast kills", System.currentTimeMillis());
	}
}
