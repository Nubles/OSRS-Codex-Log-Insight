package com.codex.loginsight.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** A slim gilt scrollbar on an ink track, in place of the look-and-feel's bright default. */
final class Scrollbars extends BasicScrollBarUI
{
	static final int WIDTH = 7;

	static void style(JScrollBar bar)
	{
		bar.setUI(new Scrollbars());
		bar.setOpaque(false);
		bar.setPreferredSize(new Dimension(WIDTH, 0));
		bar.setUnitIncrement(16);
	}

	@Override
	protected JButton createDecreaseButton(int orientation)
	{
		return none();
	}

	@Override
	protected JButton createIncreaseButton(int orientation)
	{
		return none();
	}

	private static JButton none()
	{
		JButton button = new JButton();
		Dimension zero = new Dimension(0, 0);
		button.setPreferredSize(zero);
		button.setMinimumSize(zero);
		button.setMaximumSize(zero);
		return button;
	}

	@Override
	protected void paintTrack(Graphics graphics, JComponent c, Rectangle r)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		g.setColor(Theme.alpha(Theme.INK_DEEP, 160));
		g.fill(new RoundRectangle2D.Float(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 3, 3));
		g.dispose();
	}

	@Override
	protected void paintThumb(Graphics graphics, JComponent c, Rectangle r)
	{
		if (r.isEmpty() || !scrollbar.isEnabled())
		{
			return;
		}
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		g.setColor(isThumbRollover() || isDragging ? Theme.GOLD : Theme.GOLD_DIM);
		g.fill(new RoundRectangle2D.Float(r.x + 1, r.y + 1, r.width - 2, r.height - 2, r.width - 2, r.width - 2));
		g.dispose();
	}
}
