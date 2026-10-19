package com.codex.loginsight.overlay;

import com.codex.loginsight.ui.Sigil;
import com.codex.loginsight.ui.Theme;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A gilt plaque that rises in the game view when a new slot lands: the item, its odds,
 * the count it came at, and how lucky that was. It draws a view the plugin prepares,
 * so rendering never touches game state.
 */
public class FindPlaque extends Overlay
{
	private static final int WIDTH = 228;
	private static final int PAD = 9;
	private static final int ICON = 40;
	private static final long FADE_MS = 450;

	/** What the plaque shows. */
	public static final class View
	{
		final String name;
		final String page;
		final String odds;
		final String verdict;
		final Color color;
		final boolean pet;
		final BufferedImage icon;
		final long shownAt;
		final long durationMs;
		/** The slot was on the wanted list: the plaque says the hunt is over. */
		final boolean wanted;

		public View(String name, String page, String odds, String verdict, Color color, boolean pet, BufferedImage icon,
			long shownAt, long durationMs)
		{
			this(name, page, odds, verdict, color, pet, icon, shownAt, durationMs, false);
		}

		public View(String name, String page, String odds, String verdict, Color color, boolean pet, BufferedImage icon,
			long shownAt, long durationMs, boolean wanted)
		{
			this.wanted = wanted;
			this.name = name;
			this.page = page;
			this.odds = odds;
			this.verdict = verdict;
			this.color = color;
			this.pet = pet;
			this.icon = icon;
			this.shownAt = shownAt;
			this.durationMs = durationMs;
		}
	}

	private volatile View view;

	public FindPlaque()
	{
		setPosition(OverlayPosition.TOP_CENTER);
	}

	public void show(View view)
	{
		this.view = view;
	}

	public void clear()
	{
		view = null;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		View v = view;
		if (v == null)
		{
			return null;
		}
		long age = System.currentTimeMillis() - v.shownAt;
		if (age > v.durationMs)
		{
			view = null;
			return null;
		}
		float alpha = (float) Math.min(1, Math.min(age / (double) FADE_MS, (v.durationMs - age) / (double) FADE_MS));
		Composite old = g.getComposite();
		g.setComposite(FADE[Math.max(0, Math.min(FADE_STEPS, Math.round(alpha * FADE_STEPS)))]);
		Theme.smooth(g);

		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		Layout l = layout;
		if (l == null || l.view != v || !l.small.getFont().equals(small.getFont()) || !l.bold.getFont().equals(bold.getFont()))
		{
			// The plaque's text, shape and paints are worked out once per find, not every frame it is up.
			l = new Layout(v, small, bold);
			layout = l;
		}

		g.setPaint(l.fill);
		g.fill(l.shape);
		double beat = 0.5 + 0.5 * Math.sin(age / 220.0);
		g.setStroke(GLOW);
		g.setColor(l.pulse[(int) Math.round(beat * PULSE_STEPS)]);
		g.draw(l.shape);
		g.setStroke(LINE);
		g.setColor(l.rim);
		g.draw(l.shape);

		// The item sits in a lit well.
		g.setPaint(l.wellPaint);
		g.fill(l.well);
		g.setColor(l.wellRim);
		g.draw(l.well);
		if (v.icon != null)
		{
			g.drawImage(v.icon, l.wellX + (ICON - v.icon.getWidth()) / 2, l.wellY + (ICON - v.icon.getHeight()) / 2, null);
		}
		else
		{
			Sigil.paint(g, l.wellX + 8, l.wellY + 8, ICON - 16);
		}

		int x = PAD + ICON + 9;
		int y = l.textY;
		g.setFont(Theme.small());
		shadow(g, l.heading, x, y + small.getAscent(), l.tint);
		y += small.getHeight();
		g.setFont(Theme.title());
		shadow(g, l.name, x, y + bold.getAscent(), Theme.GOLD_BRIGHT);
		y += bold.getHeight();
		g.setFont(Theme.small());
		if (l.odds != null)
		{
			shadow(g, l.odds, x, y + small.getAscent(), Theme.TEXT_MUTED);
			y += small.getHeight() + 1;
		}
		if (l.verdict != null)
		{
			shadow(g, l.verdict, x, y + small.getAscent(), v.color);
		}
		g.setComposite(old);
		return l.size;
	}

	private static final int FADE_STEPS = 32;
	/** The fade in and out in 32 steps, so no composite is made while the plaque is up. */
	private static final AlphaComposite[] FADE = new AlphaComposite[FADE_STEPS + 1];
	private static final int PULSE_STEPS = 35;
	private static final BasicStroke GLOW = new BasicStroke(3f);
	private static final BasicStroke LINE = new BasicStroke(1f);

	static
	{
		for (int i = 0; i <= FADE_STEPS; i++)
		{
			FADE[i] = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, i / (float) FADE_STEPS);
		}
	}

	private volatile Layout layout;

	/** Everything about the plaque that depends only on the find and the fonts. */
	private static final class Layout
	{
		final View view;
		final FontMetrics small;
		final FontMetrics bold;
		final Color tint;
		final Color rim;
		/** The beating outer glow, from faint to full. */
		final Color[] pulse = new Color[PULSE_STEPS + 1];
		final String heading;
		final String name;
		final String odds;
		final String verdict;
		final int wellX;
		final int wellY;
		final int textY;
		final RoundRectangle2D shape;
		final GradientPaint fill;
		final Ellipse2D well;
		final RadialGradientPaint wellPaint;
		final Color wellRim;
		final Dimension size;

		Layout(View v, FontMetrics small, FontMetrics bold)
		{
			view = v;
			this.small = small;
			this.bold = bold;
			int lines = 2 + (v.odds == null ? 0 : 1) + (v.verdict == null ? 0 : 1);
			int textHeight = small.getHeight() + bold.getHeight() + (lines - 2) * (small.getHeight() + 1);
			int height = PAD * 2 + Math.max(ICON, textHeight);
			tint = v.pet ? Theme.VIOLET : Theme.GOLD;
			rim = Theme.alpha(tint, 200);
			for (int i = 0; i <= PULSE_STEPS; i++)
			{
				pulse[i] = Theme.alpha(tint, 40 + 70 * i / PULSE_STEPS);
			}
			shape = new RoundRectangle2D.Float(0.5f, 0.5f, WIDTH - 1, height - 1, 12, 12);
			fill = new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 238), 0, height, Theme.alpha(Theme.INK_DEEP, 238));
			wellX = PAD;
			wellY = (height - ICON) / 2;
			well = new Ellipse2D.Float(wellX, wellY, ICON, ICON);
			wellPaint = new RadialGradientPaint(wellX + ICON / 2f, wellY + ICON / 2f, ICON / 2f,
				new float[]{0f, 1f}, new Color[]{Theme.alpha(tint, 110), Theme.alpha(Theme.INK_DEEP, 220)});
			wellRim = Theme.alpha(tint, 170);
			int width = WIDTH - (PAD + ICON + 9) - PAD;
			textY = PAD + Math.max(0, (Math.max(ICON, textHeight) - textHeight) / 2);
			heading = v.wanted ? "WANTED, AND FOUND" : v.pet ? "A NEW COMPANION" : "NEW LOG SLOT";
			name = clip(v.name, bold, width);
			odds = v.odds == null ? null : clip(v.odds, small, width);
			verdict = v.verdict == null ? null : clip(v.verdict, small, width);
			size = new Dimension(WIDTH, height);
		}
	}

	private static void shadow(Graphics2D g, String text, int x, int y, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	private static String clip(String text, FontMetrics fm, int width)
	{
		if (text == null)
		{
			return "";
		}
		if (fm.stringWidth(text) <= width)
		{
			return text;
		}
		String ellipsis = "...";
		int end = text.length();
		while (end > 0 && fm.stringWidth(text.substring(0, end) + ellipsis) > width)
		{
			end--;
		}
		return text.substring(0, end) + ellipsis;
	}
}
