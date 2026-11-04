package com.codex.loginsight.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/**
 * Two engraved bars, one over the other: what a kill has been worth to you against what the drop table says it is
 * worth, scaled to the larger, each with its figure at the end. A bar with no figure is left as an empty groove.
 */
final class WorthBars extends JComponent
{
	private static final int ROW = 17;
	private static final int BAR = 7;

	private final String[] names;
	private final double[] values;
	private final Color[] colors;

	/** @param values coins a kill, or a negative number for unknown */
	WorthBars(String[] names, double[] values, Color[] colors)
	{
		this.names = names.clone();
		this.values = values.clone();
		this.colors = colors.clone();
		setOpaque(false);
		Dimension d = new Dimension(10, ROW * names.length);
		setPreferredSize(d);
		setMinimumSize(d);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, d.height));
		setAlignmentX(0f);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		g.setFont(Theme.small());
		FontMetrics fm = g.getFontMetrics();
		int nameWidth = 0;
		int valueWidth = 0;
		double max = 0;
		for (int i = 0; i < names.length; i++)
		{
			nameWidth = Math.max(nameWidth, fm.stringWidth(names[i]));
			valueWidth = Math.max(valueWidth, fm.stringWidth(Format.gp(values[i])));
			max = Math.max(max, values[i]);
		}
		int x0 = nameWidth + 8;
		int x1 = getWidth() - valueWidth - 8;
		int width = Math.max(10, x1 - x0);
		for (int i = 0; i < names.length; i++)
		{
			int top = i * ROW;
			int baseline = top + (ROW + fm.getAscent()) / 2 - 1;
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(names[i], 0, baseline);
			int barTop = top + (ROW - BAR) / 2;
			RoundRectangle2D groove = new RoundRectangle2D.Float(x0, barTop, width, BAR - 1, BAR - 1, BAR - 1);
			g.setColor(Theme.INK_DEEP);
			g.fill(groove);
			if (values[i] > 0 && max > 0)
			{
				float fill = (float) Math.max(BAR - 1, width * values[i] / max);
				g.setPaint(new GradientPaint(x0, 0, Theme.alpha(colors[i], 110), x0 + fill, 0, colors[i]));
				g.fill(new RoundRectangle2D.Float(x0, barTop, fill, BAR - 1, BAR - 1, BAR - 1));
			}
			g.setColor(Theme.alpha(Theme.STROKE, 160));
			g.draw(groove);
			String value = Format.gp(values[i]);
			ProgressRing.drawShadowed(g, value, getWidth() - fm.stringWidth(value), baseline,
				values[i] >= 0 ? colors[i] : Theme.TEXT_FAINT);
		}
		g.dispose();
	}
}
