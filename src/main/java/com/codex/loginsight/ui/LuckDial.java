package com.codex.loginsight.ui;

import com.codex.loginsight.core.Luck;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import javax.swing.JComponent;

/**
 * A half-moon gauge from parched to blessed with a gilt needle at where the account
 * stands, and the verdict written underneath.
 */
public class LuckDial extends JComponent
{
	private static final String[] ENDS = {"PARCHED", "BLESSED"};

	private final Animator needle = new Animator(this);
	private final int height;
	private Luck luck;

	public LuckDial(int height)
	{
		this.height = height;
		setPreferredSize(new Dimension(10, height));
		setMinimumSize(new Dimension(10, height));
		setOpaque(false);
		needle.set(0.5, false);
	}

	public void setLuck(Luck luck, boolean animate)
	{
		this.luck = luck;
		needle.set(luck == null ? 0.5 : luck.getPercentile(), animate);
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();

		// Below the hub sit the end labels' gap, the verdict and the percentile line.
		int textBlock = small.getHeight() + 4 + bold.getHeight() + small.getHeight() + 2;
		float thickness = 9;
		float radius = Math.max(20, Math.min(w / 2f - 14, getHeight() - textBlock - thickness / 2 - 4));
		float cx = w / 2f;
		float cy = thickness / 2 + 4 + radius;
		Arc2D.Float box = new Arc2D.Float(cx - radius, cy - radius, radius * 2, radius * 2, 180, -180, Arc2D.OPEN);

		// The track, in short segments so the colour can travel from blood to green.
		g.setStroke(new BasicStroke(thickness + 4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Theme.INK_DEEP);
		g.draw(box);
		int segments = 60;
		boolean known = luck != null;
		g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
		for (int i = 0; i < segments; i++)
		{
			double t = (i + 0.5) / segments;
			Color c = Theme.luck(t);
			g.setColor(known ? Theme.alpha(c, 215) : Theme.alpha(Theme.STROKE, 200));
			g.draw(new Arc2D.Double(cx - radius, cy - radius, radius * 2, radius * 2, 180 - i * 180.0 / segments, -180.0 / segments - 0.4, Arc2D.OPEN));
		}
		// Tick marks at the verdict borders.
		g.setStroke(new BasicStroke(1.2f));
		for (double edge : new double[]{0.15, 0.35, 0.65, 0.85})
		{
			double a = Math.PI * (1 - edge);
			float inner = radius - thickness / 2 - 2;
			float outer = radius + thickness / 2 + 2;
			g.setColor(Theme.alpha(Theme.INK_DEEP, 220));
			g.drawLine(Math.round(cx + (float) Math.cos(a) * inner), Math.round(cy - (float) Math.sin(a) * inner),
				Math.round(cx + (float) Math.cos(a) * outer), Math.round(cy - (float) Math.sin(a) * outer));
		}
		g.setColor(Theme.alpha(Theme.GOLD_DIM, 120));
		g.setStroke(new BasicStroke(1f));
		g.draw(new Arc2D.Float(cx - radius - thickness / 2 - 3, cy - radius - thickness / 2 - 3, (radius + thickness / 2 + 3) * 2,
			(radius + thickness / 2 + 3) * 2, 180, -180, Arc2D.OPEN));

		if (known)
		{
			double value = needle.get();
			double a = Math.PI * (1 - value);
			float len = radius - thickness / 2 - 4;
			float tipX = cx + (float) Math.cos(a) * len;
			float tipY = cy - (float) Math.sin(a) * len;
			float baseW = 3.2f;
			Path2D hand = new Path2D.Float();
			hand.moveTo(tipX, tipY);
			hand.lineTo(cx + (float) Math.cos(a + Math.PI / 2) * baseW, cy - (float) Math.sin(a + Math.PI / 2) * baseW);
			hand.lineTo(cx + (float) Math.cos(a - Math.PI / 2) * baseW, cy - (float) Math.sin(a - Math.PI / 2) * baseW);
			hand.closePath();
			g.setColor(Theme.alpha(java.awt.Color.BLACK, 120));
			g.translate(1, 1);
			g.fill(hand);
			g.translate(-1, -1);
			g.setColor(Theme.GOLD_BRIGHT);
			g.fill(hand);
			g.setColor(Theme.luck(value));
			g.fill(new Ellipse2D.Float(tipX - 2.5f, tipY - 2.5f, 5, 5));
		}
		g.setColor(Theme.GOLD);
		g.fill(new Ellipse2D.Float(cx - 5, cy - 5, 10, 10));
		g.setColor(Theme.INK_DEEP);
		g.fill(new Ellipse2D.Float(cx - 2, cy - 2, 4, 4));

		g.setFont(Theme.small());
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(ENDS[0], Math.max(0, cx - radius - small.stringWidth(ENDS[0]) / 2f), cy + small.getAscent() + 2);
		g.drawString(ENDS[1], Math.min(w - small.stringWidth(ENDS[1]), cx + radius - small.stringWidth(ENDS[1]) / 2f), cy + small.getAscent() + 2);

		int y = Math.round(cy + small.getHeight() + 4);
		String verdict = known ? luck.verdict().getLabel() : "Not read yet";
		String line = known ? luckLine(luck.luckierThan()) : NOT_READ_LINE;
		g.setFont(Theme.title());
		ProgressRing.drawShadowed(g, verdict, Math.round(cx - bold.stringWidth(verdict) / 2f), y + bold.getAscent(),
			known ? Theme.luck(luck.verdict()) : Theme.TEXT_MUTED);
		g.setFont(Theme.small());
		ProgressRing.drawShadowed(g, line, Math.round(cx - small.stringWidth(line) / 2f), y + bold.getHeight() + small.getAscent(), Theme.TEXT_MUTED);
		g.dispose();
	}

	/** Under "Not read yet": short enough to sit inside the dial's card at sidebar width. */
	static final String NOT_READ_LINE = "Open log pages to read your luck";

	/**
	 * "Luckier than 68% of players", or for the unlucky half "Drier than 90% of players": never "luckier than 0%",
	 * which reads as a mistake. The figure stays between 1 and 99, since no read is ever certain.
	 */
	static String luckLine(int luckierThan)
	{
		if (luckierThan >= 50)
		{
			return "Luckier than " + Math.min(99, luckierThan) + "% of players";
		}
		return "Drier than " + Math.min(99, Math.max(1, 100 - luckierThan)) + "% of players";
	}
}
