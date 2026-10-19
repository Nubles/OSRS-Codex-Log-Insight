package com.codex.loginsight.ui;

import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/**
 * How far into a slot's odds you are: the bar fills with the share of players who would have it by your count,
 * gilt while that is ordinary and ember to blood as it gets rare, with notches where half and nine in ten of
 * players would have it.
 */
final class DryBar extends JComponent
{
	private static final int HEIGHT = 7;
	private final double stillWaiting;

	/** @param stillWaiting the share of players still without the slot at your count */
	DryBar(double stillWaiting)
	{
		this.stillWaiting = stillWaiting;
		setOpaque(false);
		setPreferredSize(new Dimension(10, HEIGHT));
		setMinimumSize(new Dimension(10, HEIGHT));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		setAlignmentX(0f);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = HEIGHT - 1;
		g.setColor(Theme.INK_DEEP);
		g.fill(new RoundRectangle2D.Float(0, 0, w - 1, h, h, h));
		double share = Math.max(0, Math.min(1, 1 - stillWaiting));
		if (stillWaiting >= 0 && share > 0)
		{
			float fill = (float) Math.max(h, (w - 1) * share);
			g.setPaint(new GradientPaint(0, 0, Theme.GOLD_DIM, fill, 0, Theme.dry(stillWaiting)));
			g.fill(new RoundRectangle2D.Float(0, 0, fill, h, h, h));
		}
		g.setColor(Theme.alpha(Theme.TEXT, 120));
		for (double notch : new double[]{0.5, 0.9})
		{
			float x = (float) ((w - 1) * notch);
			g.draw(new Line2D.Float(x, 1, x, h - 1));
		}
		g.setColor(Theme.alpha(Theme.STROKE, 160));
		g.draw(new RoundRectangle2D.Float(0, 0, w - 1, h, h, h));
		g.dispose();
	}
}
