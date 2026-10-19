package com.codex.loginsight.ui;

import com.codex.loginsight.core.Report;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/**
 * One slot as a small inlaid tile: owned slots sit bright on a gilt rim, missing ones
 * are a faded ghost, with a coloured bead when the wait has run long.
 */
public class ItemTile extends JComponent
{
	public static final int SIZE = 38;

	private final Report.ItemReport item;
	private BufferedImage image;

	public ItemTile(Report.ItemReport item, ItemIcons icons)
	{
		this.item = item;
		Dimension d = new Dimension(SIZE, SIZE - 4);
		setPreferredSize(d);
		setMinimumSize(d);
		setMaximumSize(d);
		setOpaque(false);
		if (icons != null)
		{
			image = icons.icon(item.getItemId(), this::repaint);
		}
		setToolTipText(tooltip(item));
	}

	/** "Half of players by 2,080 kc, 9 in 10 by 6,907": the counts a grind can be measured against. */
	static String marks(double chance)
	{
		int half = com.codex.loginsight.core.DryWatch.countFor(chance, 0.5);
		int most = com.codex.loginsight.core.DryWatch.countFor(chance, 0.9);
		return "Half of players by " + Format.count(half) + " kc, 9 in 10 by " + Format.count(most);
	}

	static String tooltip(Report.ItemReport item)
	{
		StringBuilder sb = new StringBuilder("<html><b>").append(Ui.escape(item.getName())).append("</b>");
		if (item.isOwned())
		{
			sb.append("<br>Owned");
		}
		if (item.getChance() > 0)
		{
			sb.append("<br>").append(Format.chance(item.getChance()));
			if (item.getLabel() != null)
			{
				sb.append(" per ").append(Ui.escape(Format.counterName(item.getLabel()))).append(" kc");
			}
		}
		if (!item.isOwned() && item.getDryChance() >= 0)
		{
			sb.append("<br>").append(Format.dry(item.getDryChance())).append(": ")
				.append(Format.percent(1 - item.getDryChance())).append(" of players would have it by now");
		}
		if (!item.isOwned() && item.getChance() > 0 && item.getLabel() != null)
		{
			sb.append("<br>").append(marks(item.getChance()));
		}
		if (!item.isOwned() && item.getSource() != null)
		{
			sb.append("<br>Fastest: ").append(Ui.escape(item.getSource())).append(" (").append(Format.hours(item.getHours())).append(")");
		}
		return sb.append("</html>").toString();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 8, 8);
		boolean owned = item.isOwned();
		g.setPaint(new GradientPaint(0, 0, owned ? Theme.mix(Theme.CARD_TOP, Theme.GOLD_DIM, 0.18) : Theme.INK_DEEP, 0, h,
			owned ? Theme.CARD : Theme.INK_DEEP));
		g.fill(shape);
		Color rim = owned ? (item.isPet() ? Theme.VIOLET : Theme.alpha(Theme.GOLD, 170)) : Theme.STROKE_SOFT;
		g.setColor(rim);
		g.draw(shape);

		if (image != null)
		{
			int iw = image.getWidth();
			int ih = image.getHeight();
			int x = (w - iw) / 2;
			int y = (h - ih) / 2;
			Composite old = g.getComposite();
			if (!owned)
			{
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f));
			}
			g.drawImage(image, x, y, null);
			g.setComposite(old);
		}

		if (!owned && item.getDryChance() >= 0 && item.getDryChance() < 0.35)
		{
			Color bead = Theme.dry(item.getDryChance());
			g.setColor(Theme.INK_DEEP);
			g.fill(new Ellipse2D.Float(w - 10, 2, 8, 8));
			g.setColor(bead);
			g.fill(new Ellipse2D.Float(w - 9, 3, 6, 6));
		}
		if (owned)
		{
			g.setStroke(new BasicStroke(1f));
			g.setColor(Theme.alpha(Color.WHITE, 18));
			g.drawLine(5, 1, w - 5, 1);
		}
		g.dispose();
	}
}
