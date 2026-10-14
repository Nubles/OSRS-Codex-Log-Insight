package com.codex.loginsight.ui;

import java.awt.BasicStroke;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/** Log Insight's mark: an open, gilt-edged tome with a star of discovery above its spine. */
public final class Sigil
{
	private Sigil()
	{
	}

	public static BufferedImage icon(int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		Theme.smooth(g);
		paint(g, 0, 0, size);
		g.dispose();
		return image;
	}

	/** Draws the tome in a size by size box. */
	public static void paint(Graphics2D g, float x, float y, float size)
	{
		float s = size / 16f;
		float spine = x + 8 * s;
		float top = y + 6 * s;
		float bottom = y + 14.5f * s;

		Path2D left = new Path2D.Float();
		left.moveTo(spine, top + 1 * s);
		left.curveTo(spine - 2 * s, top - 0.5f * s, spine - 5 * s, top - 0.5f * s, x + 1 * s, top + 0.5f * s);
		left.lineTo(x + 1 * s, bottom - 1 * s);
		left.curveTo(spine - 5 * s, bottom - 2 * s, spine - 2 * s, bottom - 2 * s, spine, bottom);
		left.closePath();
		Path2D right = new Path2D.Float();
		right.moveTo(spine, top + 1 * s);
		right.curveTo(spine + 2 * s, top - 0.5f * s, spine + 5 * s, top - 0.5f * s, x + 15 * s, top + 0.5f * s);
		right.lineTo(x + 15 * s, bottom - 1 * s);
		right.curveTo(spine + 5 * s, bottom - 2 * s, spine + 2 * s, bottom - 2 * s, spine, bottom);
		right.closePath();

		g.setPaint(new GradientPaint(x, top, Theme.GOLD_BRIGHT, x, bottom, Theme.GOLD_DIM));
		g.fill(left);
		g.fill(right);
		g.setStroke(new BasicStroke(Math.max(0.8f, 0.8f * s)));
		g.setColor(Theme.INK_DEEP);
		g.draw(left);
		g.draw(right);
		// Lines of script on each page.
		g.setColor(Theme.alpha(Theme.INK_DEEP, 150));
		for (int i = 0; i < 2; i++)
		{
			float ly = top + (3 + i * 2.6f) * s;
			g.drawLine(Math.round(x + 3 * s), Math.round(ly), Math.round(spine - 1.5f * s), Math.round(ly));
			g.drawLine(Math.round(spine + 1.5f * s), Math.round(ly), Math.round(x + 13 * s), Math.round(ly));
		}

		// The star above the spine.
		float cx = spine;
		float cy = y + 3 * s;
		Path2D star = new Path2D.Float();
		for (int i = 0; i < 8; i++)
		{
			double angle = Math.PI / 4 * i - Math.PI / 2;
			float r = (i % 2 == 0 ? 3f : 1.1f) * s;
			float px = cx + (float) Math.cos(angle) * r;
			float py = cy + (float) Math.sin(angle) * r;
			if (i == 0)
			{
				star.moveTo(px, py);
			}
			else
			{
				star.lineTo(px, py);
			}
		}
		star.closePath();
		g.setColor(Theme.TEAL);
		g.fill(star);
	}
}
