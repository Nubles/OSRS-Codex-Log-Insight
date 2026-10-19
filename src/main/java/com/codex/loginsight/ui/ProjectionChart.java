package com.codex.loginsight.ui;

import com.codex.loginsight.core.Projection;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import javax.swing.JComponent;

/**
 * The chance a page is finished against hours played, as a gilt curve rising from nothing
 * to near certain. Two engraved marks stand where half and nine in ten players would be done.
 */
class ProjectionChart extends JComponent
{
	private static final int HEIGHT = 78;
	private static final int LEFT = 4;
	private static final int RIGHT = 4;
	private static final int TOP = 6;
	/** Room under the plot for the hour labels. */
	private static final int AXIS = 14;

	private final Projection projection;

	ProjectionChart(Projection projection)
	{
		this.projection = projection;
		setOpaque(false);
		setPreferredSize(new Dimension(10, HEIGHT));
		setMinimumSize(new Dimension(10, HEIGHT));
		setToolTipText("<html>The chance this page is finished after each number of hours at your rates.<br>"
			+ "Half of players would be done by " + Format.hours(projection.getMedian()) + ", nine in ten by "
			+ Format.hours(projection.getLikely()) + ".</html>");
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int plotW = w - LEFT - RIGHT;
		int plotH = HEIGHT - TOP - AXIS;
		int bottom = TOP + plotH;
		double[] hours = projection.getHours();
		double[] chance = projection.getChance();
		double end = hours[hours.length - 1];
		if (end <= 0 || plotW <= 0)
		{
			g.dispose();
			return;
		}

		// Faint rules at a quarter, half and three quarters.
		g.setColor(Theme.STROKE_SOFT);
		for (int q = 1; q <= 3; q++)
		{
			int y = bottom - plotH * q / 4;
			g.drawLine(LEFT, y, LEFT + plotW, y);
		}
		g.setColor(Theme.STROKE);
		g.drawLine(LEFT, bottom, LEFT + plotW, bottom);

		Path2D line = new Path2D.Float();
		Path2D area = new Path2D.Float();
		area.moveTo(LEFT, bottom);
		for (int i = 0; i < hours.length; i++)
		{
			float x = (float) (LEFT + plotW * hours[i] / end);
			float y = (float) (bottom - plotH * Math.max(0, Math.min(1, chance[i])));
			if (i == 0)
			{
				line.moveTo(x, y);
			}
			else
			{
				line.lineTo(x, y);
			}
			area.lineTo(x, y);
		}
		area.lineTo(LEFT + plotW, bottom);
		area.closePath();
		g.setPaint(new GradientPaint(0, TOP, Theme.alpha(Theme.GOLD, 70), 0, bottom, Theme.alpha(Theme.GOLD, 6)));
		g.fill(area);

		g.setFont(Theme.small());
		FontMetrics fm = g.getFontMetrics();
		int medianX = mark(g, projection.getMedian(), 0.5, end, plotW, plotH, bottom, Theme.GOLD_BRIGHT);
		int likelyX = mark(g, projection.getLikely(), 0.9, end, plotW, plotH, bottom, Theme.TEAL);

		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Theme.GOLD);
		g.draw(line);

		// Hour labels under the two marks, kept apart and inside the plot.
		String a = Format.hours(projection.getMedian());
		String b = Format.hours(projection.getLikely());
		int aw = fm.stringWidth(a);
		int bw = fm.stringWidth(b);
		int ax = Math.max(LEFT, Math.min(medianX - aw / 2, LEFT + plotW - aw));
		int bx = Math.max(LEFT, Math.min(likelyX - bw / 2, LEFT + plotW - bw));
		if (bx < ax + aw + 6)
		{
			bx = Math.min(LEFT + plotW - bw, ax + aw + 6);
			ax = Math.min(ax, bx - aw - 6);
		}
		int base = bottom + fm.getAscent() + 2;
		ProgressRing.drawShadowed(g, a, Math.max(LEFT, ax), base, Theme.GOLD_BRIGHT);
		ProgressRing.drawShadowed(g, b, bx, base, Theme.TEAL);
		g.dispose();
	}

	/** A dotted drop from the curve to the axis where it crosses this chance, with a bead on the curve. */
	private static int mark(Graphics2D g, double hours, double chance, double end, int plotW, int plotH, int bottom, Color color)
	{
		int x = (int) Math.round(LEFT + plotW * Math.min(1, hours / end));
		int y = (int) Math.round(bottom - plotH * chance);
		g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{2f, 2f}, 0f));
		g.setColor(Theme.alpha(color, 150));
		g.drawLine(x, y, x, bottom);
		g.setStroke(new BasicStroke(1f));
		g.setColor(Theme.INK_DEEP);
		g.fillOval(x - 3, y - 3, 7, 7);
		g.setColor(color);
		g.drawOval(x - 3, y - 3, 6, 6);
		return x;
	}
}
