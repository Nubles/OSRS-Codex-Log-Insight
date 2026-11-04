package com.codex.loginsight.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/**
 * Every reading the oracle made, on one luck scale from Parched to Blessed: a gilt bead for each slot you found, set
 * where its kill count puts you among all players, and a hollow ring for each slot still missing. A gold diamond
 * marks the middle of your finds, so one glance says whether the log has been kind.
 */
final class LuckStrip extends JComponent
{
	private static final int BAR_Y = 22;
	private static final int BAR_H = 6;
	private static final int HEIGHT = 54;
	private static final float BEAD = 5f;
	/** Beads that would overlap are stacked upwards, at most this many rows. */
	private static final int ROWS = 3;

	private final double[] finds;
	private final double[] chases;

	/**
	 * @param finds  each found slot's share of players it is luckier than
	 * @param chases each missing slot's share of players it is luckier than (half of those also still waiting)
	 */
	LuckStrip(double[] finds, double[] chases)
	{
		this.finds = finds.clone();
		this.chases = chases.clone();
		java.util.Arrays.sort(this.finds);
		java.util.Arrays.sort(this.chases);
		setOpaque(false);
		setFont(Theme.small());
		setPreferredSize(new Dimension(10, HEIGHT));
		setMinimumSize(new Dimension(10, HEIGHT));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		setAlignmentX(0f);
	}

	/** The middle of these readings, or -1 with none. */
	static double median(double[] sorted)
	{
		int n = sorted.length;
		if (n == 0)
		{
			return -1;
		}
		return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		float left = 6;
		float right = w - 6;
		float span = right - left;

		// The scale: the same ramp as the luck dial.
		Color[] ramp = new Color[9];
		float[] stops = new float[9];
		for (int i = 0; i < ramp.length; i++)
		{
			stops[i] = i / 8f;
			ramp[i] = Theme.alpha(Theme.luck(stops[i]), 200);
		}
		RoundRectangle2D bar = new RoundRectangle2D.Float(left, BAR_Y, span, BAR_H, BAR_H, BAR_H);
		g.setColor(Theme.INK_DEEP);
		g.fill(bar);
		g.setPaint(new LinearGradientPaint(left, 0, right, 0, stops, ramp));
		g.fill(new RoundRectangle2D.Float(left, BAR_Y + 1, span, BAR_H - 2, BAR_H - 2, BAR_H - 2));
		g.setColor(Theme.alpha(Theme.STROKE, 200));
		g.draw(bar);
		g.setColor(Theme.alpha(Theme.TEXT, 90));
		float mid = left + span / 2;
		g.drawLine(Math.round(mid), BAR_Y - 2, Math.round(mid), BAR_Y + BAR_H + 2);

		// Hollow rings under the bar for slots still missing.
		g.setStroke(new BasicStroke(1.2f));
		float[] lastX = new float[ROWS];
		java.util.Arrays.fill(lastX, -100);
		for (double c : chases)
		{
			float x = left + (float) (span * c);
			float y = BAR_Y + BAR_H + 3;
			g.setColor(Theme.alpha(Theme.luck(c), 220));
			g.draw(new Ellipse2D.Float(x - BEAD / 2, y, BEAD, BEAD));
		}

		// Gilt beads above the bar for slots found, stacked where they crowd.
		java.util.Arrays.fill(lastX, -100);
		for (double f : finds)
		{
			float x = left + (float) (span * f);
			int row = row(lastX, x);
			float y = BAR_Y - 3 - BEAD - row * (BEAD + 1);
			g.setColor(Theme.alpha(Color.BLACK, 120));
			g.fill(new Ellipse2D.Float(x - BEAD / 2, y + 1, BEAD, BEAD));
			g.setColor(Theme.luck(f));
			g.fill(new Ellipse2D.Float(x - BEAD / 2, y, BEAD, BEAD));
			g.setColor(Theme.alpha(Theme.GOLD_BRIGHT, 160));
			g.draw(new Ellipse2D.Float(x - BEAD / 2, y, BEAD, BEAD));
		}

		// The middle of your finds.
		double median = median(finds);
		if (median >= 0)
		{
			float x = left + (float) (span * median);
			float cy = BAR_Y + BAR_H / 2f;
			Path2D diamond = new Path2D.Float();
			diamond.moveTo(x, cy - 6);
			diamond.lineTo(x + 5, cy);
			diamond.lineTo(x, cy + 6);
			diamond.lineTo(x - 5, cy);
			diamond.closePath();
			g.setColor(Theme.INK_DEEP);
			g.fill(diamond);
			g.setColor(Theme.GOLD_BRIGHT);
			g.setStroke(new BasicStroke(1.4f));
			g.draw(diamond);
		}

		g.setFont(getFont());
		FontMetrics fm = g.getFontMetrics();
		int base = HEIGHT - 2;
		g.setColor(Theme.alpha(Theme.luck(0), 230));
		g.drawString("Parched", Math.round(left), base);
		String even = "Even";
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(even, Math.round(mid - fm.stringWidth(even) / 2f), base);
		String blessed = "Blessed";
		g.setColor(Theme.alpha(Theme.luck(1), 230));
		g.drawString(blessed, Math.round(right - fm.stringWidth(blessed)), base);
		g.dispose();
	}

	/** The lowest row whose last bead is clear of this one, or the top row when every row is crowded. */
	private static int row(float[] lastX, float x)
	{
		for (int r = 0; r < lastX.length; r++)
		{
			if (x - lastX[r] >= BEAD + 1)
			{
				lastX[r] = x;
				return r;
			}
		}
		lastX[lastX.length - 1] = x;
		return lastX.length - 1;
	}
}
