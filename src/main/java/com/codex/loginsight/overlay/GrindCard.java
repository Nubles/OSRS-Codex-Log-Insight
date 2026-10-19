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
import java.awt.geom.Line2D;
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
		Report.ItemReport focus = v.getFocus();
		int lines = 2 + (focus != null ? 1 : 0);
		int height = PAD * 2 + bold.getHeight() + lines * (small.getHeight() + 1) + (focus != null && focus.getDryChance() >= 0 ? 7 : 0);

		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, WIDTH - 1, height - 1, 10, 10);
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 228), 0, height, Theme.alpha(Theme.INK_DEEP, 228)));
		g.fill(shape);
		g.setColor(Theme.alpha(v.isFocusWanted() ? Theme.GOLD : Theme.STROKE, 220));
		g.draw(shape);

		int x = PAD;
		int width = WIDTH - PAD * 2;
		int y = PAD;
		String kc = Format.count(v.getCount()) + " kc";
		g.setFont(Theme.small());
		int kcWidth = small.stringWidth(kc);
		g.setFont(Theme.title());
		shadow(g, clip(v.getPage(), bold, width - kcWidth - 6), x, y + bold.getAscent(), Theme.GOLD_BRIGHT);
		g.setFont(Theme.small());
		shadow(g, kc, x + width - kcWidth, y + bold.getAscent(), Theme.GOLD);
		y += bold.getHeight();

		String tonight = (v.getAdded() > 0 ? "+" + Format.count(v.getAdded()) + " this session  ·  " : "")
			+ v.getMissing() + (v.getMissing() == 1 ? " slot left" : " slots left");
		shadow(g, clip(tonight, small, width), x, y + small.getAscent(), Theme.TEXT_MUTED);
		y += small.getHeight() + 1;
		String next = Double.isFinite(v.getHoursToNext()) ? "Next slot " + Format.about(v.getHoursToNext()) : "Next slot not timed";
		shadow(g, clip(next, small, width), x, y + small.getAscent(), Theme.TEAL);
		y += small.getHeight() + 1;

		if (focus != null)
		{
			String dry = focus.getDryChance() >= 0 ? Format.dry(focus.getDryChance()) : Format.chance(focus.getChance());
			int dryWidth = small.stringWidth(dry);
			Color color = focus.getDryChance() >= 0 ? Theme.dry(focus.getDryChance()) : Theme.TEXT_MUTED;
			String name = (v.isFocusWanted() ? "Wanted: " : "") + focus.getName();
			shadow(g, clip(name, small, width - dryWidth - 6), x, y + small.getAscent(), v.isFocusWanted() ? Theme.GOLD : Theme.TEXT);
			shadow(g, dry, x + width - dryWidth, y + small.getAscent(), color.equals(Theme.TEXT_MUTED) ? Theme.TEXT : color);
			y += small.getHeight() + 2;
			if (focus.getDryChance() >= 0)
			{
				bar(g, x, y, width, focus.getDryChance());
			}
		}
		return new Dimension(WIDTH, height);
	}

	/** The share of players who would have the slot by now, with notches at half and nine in ten. */
	private static void bar(Graphics2D g, int x, int y, int width, double stillWaiting)
	{
		int h = 4;
		g.setColor(Theme.INK_DEEP);
		g.fill(new RoundRectangle2D.Float(x, y, width, h, h, h));
		float fill = (float) Math.max(h, width * Math.max(0, Math.min(1, 1 - stillWaiting)));
		g.setPaint(new GradientPaint(x, 0, Theme.GOLD_DIM, x + fill, 0, Theme.dry(stillWaiting)));
		g.fill(new RoundRectangle2D.Float(x, y, fill, h, h, h));
		g.setColor(Theme.alpha(Theme.TEXT, 110));
		for (double notch : new double[]{0.5, 0.9})
		{
			float nx = (float) (x + width * notch);
			g.draw(new Line2D.Float(nx, y, nx, y + h - 1));
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
		String dots = "...";
		int end = text.length();
		while (end > 0 && fm.stringWidth(text.substring(0, end) + dots) > width)
		{
			end--;
		}
		return text.substring(0, end).trim() + dots;
	}
}
