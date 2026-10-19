package com.codex.loginsight.overlay;

import com.codex.loginsight.core.Grind;
import com.codex.loginsight.core.Report;
import com.codex.loginsight.ui.Format;
import com.codex.loginsight.ui.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A small card in the game view while you grind a page of the log: the kill count and what tonight added, the wait
 * for the page's next slot, and the slot most worth watching with how dry you are on it. It steps aside a few
 * minutes after the last kill count, so it is only there while the grind is.
 */
public class GrindCard extends Overlay
{
	private static final int WIDTH = 176;
	private static final int PAD = 8;

	private volatile Grind grind;

	public GrindCard()
	{
		setPosition(OverlayPosition.TOP_LEFT);
	}

	public void setGrind(Grind grind)
	{
		this.grind = grind;
	}

	public void clear()
	{
		grind = null;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Grind v = grind;
		if (v == null || !v.isShowing(System.currentTimeMillis()))
		{
			return null;
		}
		Theme.smooth(g);
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		Layout l = layout;
		if (l == null || l.grind != v || l.small != small || l.bold != bold)
		{
			// Every line, its clipping and the card's shape are worked out once per grind update, not every frame.
			l = new Layout(v, small, bold);
			layout = l;
		}

		g.setPaint(l.fill);
		g.fill(l.shape);
		g.setColor(l.rim);
		g.draw(l.shape);

		int x = PAD;
		int y = PAD;
		g.setFont(Theme.title());
		shadow(g, l.page, x, y + bold.getAscent(), Theme.GOLD_BRIGHT);
		g.setFont(Theme.small());
		shadow(g, l.kc, x + WIDTH - PAD * 2 - l.kcWidth, y + bold.getAscent(), Theme.GOLD);
		y += bold.getHeight();
		shadow(g, l.tonight, x, y + small.getAscent(), Theme.TEXT_MUTED);
		y += small.getHeight() + 1;
		shadow(g, l.next, x, y + small.getAscent(), Theme.TEAL);
		y += small.getHeight() + 1;
		if (l.name != null)
		{
			shadow(g, l.name, x, y + small.getAscent(), v.isFocusWanted() ? Theme.GOLD : Theme.TEXT);
			shadow(g, l.dry, x + WIDTH - PAD * 2 - l.dryWidth, y + small.getAscent(), l.dryColor);
			y += small.getHeight() + 2;
			if (l.barFill > 0)
			{
				bar(g, l, x, y);
			}
		}
		return l.size;
	}

	private static final Color FILL_TOP = Theme.alpha(Theme.CARD_TOP, 228);
	private static final Color FILL_BOTTOM = Theme.alpha(Theme.INK_DEEP, 228);
	private static final Color RIM_WANTED = Theme.alpha(Theme.GOLD, 220);
	private static final Color RIM = Theme.alpha(Theme.STROKE, 220);
	private static final Color NOTCH = Theme.alpha(Theme.TEXT, 110);
	private static final int BAR_H = 4;

	private volatile Layout layout;

	/** The card's text and shapes for one grind update. */
	private static final class Layout
	{
		final Grind grind;
		final FontMetrics small;
		final FontMetrics bold;
		final String page;
		final String kc;
		final int kcWidth;
		final String tonight;
		final String next;
		final String name;
		final String dry;
		final int dryWidth;
		final Color dryColor;
		final float barFill;
		final GradientPaint barPaint;
		final RoundRectangle2D shape;
		final GradientPaint fill;
		final Color rim;
		final Dimension size;

		Layout(Grind v, FontMetrics small, FontMetrics bold)
		{
			grind = v;
			this.small = small;
			this.bold = bold;
			int width = WIDTH - PAD * 2;
			kc = Format.count(v.getCount()) + " kc";
			kcWidth = small.stringWidth(kc);
			page = clip(v.getPage(), bold, width - kcWidth - 6);
			tonight = clip((v.getAdded() > 0 ? "+" + Format.count(v.getAdded()) + " this session  ·  " : "")
				+ v.getMissing() + (v.getMissing() == 1 ? " slot left" : " slots left"), small, width);
			next = clip(Double.isFinite(v.getHoursToNext()) ? "Next slot " + Format.about(v.getHoursToNext()) : "Next slot not timed",
				small, width);
			Report.ItemReport focus = v.getFocus();
			float bar = 0;
			GradientPaint paint = null;
			if (focus != null)
			{
				dry = focus.getDryChance() >= 0 ? Format.dry(focus.getDryChance()) : Format.chance(focus.getChance());
				dryWidth = small.stringWidth(dry);
				dryColor = focus.getDryChance() >= 0 ? Theme.dry(focus.getDryChance()) : Theme.TEXT;
				name = clip((v.isFocusWanted() ? "Wanted: " : "") + focus.getName(), small, width - dryWidth - 6);
				if (focus.getDryChance() >= 0)
				{
					double still = focus.getDryChance();
					bar = (float) Math.max(BAR_H, width * Math.max(0, Math.min(1, 1 - still)));
					paint = new GradientPaint(PAD, 0, Theme.GOLD_DIM, PAD + bar, 0, Theme.dry(still));
				}
			}
			else
			{
				dry = null;
				dryWidth = 0;
				dryColor = Theme.TEXT;
				name = null;
			}
			barFill = bar;
			barPaint = paint;
			int lines = 2 + (focus != null ? 1 : 0);
			int height = PAD * 2 + bold.getHeight() + lines * (small.getHeight() + 1) + (focus != null && focus.getDryChance() >= 0 ? 7 : 0);
			shape = new RoundRectangle2D.Float(0.5f, 0.5f, WIDTH - 1, height - 1, 10, 10);
			fill = new GradientPaint(0, 0, FILL_TOP, 0, height, FILL_BOTTOM);
			rim = v.isFocusWanted() ? RIM_WANTED : RIM;
			size = new Dimension(WIDTH, height);
		}
	}

	/** The share of players who would have the slot by now, with notches at half and nine in ten. */
	private static void bar(Graphics2D g, Layout l, int x, int y)
	{
		int width = WIDTH - PAD * 2;
		g.setColor(Theme.INK_DEEP);
		g.fillRoundRect(x, y, width, BAR_H, BAR_H, BAR_H);
		g.setPaint(l.barPaint);
		g.fillRoundRect(x, y, Math.round(l.barFill), BAR_H, BAR_H, BAR_H);
		g.setColor(NOTCH);
		g.drawLine(x + width / 2, y, x + width / 2, y + BAR_H - 1);
		g.drawLine(x + width * 9 / 10, y, x + width * 9 / 10, y + BAR_H - 1);
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
		String dots = "...";
		int end = text.length();
		while (end > 0 && fm.stringWidth(text.substring(0, end) + dots) > width)
		{
			end--;
		}
		return text.substring(0, end).trim() + dots;
	}
}
