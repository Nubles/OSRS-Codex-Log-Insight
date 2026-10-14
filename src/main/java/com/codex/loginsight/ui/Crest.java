package com.codex.loginsight.ui;

import com.codex.loginsight.core.Luck;
import com.codex.loginsight.core.Report;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.geom.Path2D;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The crest at the top of the panel: a ring for the whole log, the slot count, and
 * three ledgers for finished pages, luck and the hours still ahead.
 */
public class Crest extends JPanel
{
	private final ProgressRing ring = new ProgressRing(70, 7f);
	private final JLabel title = Ui.label("Log Insight", Theme.title(), Theme.GOLD_BRIGHT);
	private final JLabel slots = Ui.label("Log in to read your log", Theme.small(), Theme.TEXT_MUTED);
	private final Ledger pages = new Ledger("Pages done", true);
	private final Ledger luck = new Ledger("Luck", false);
	private final Ledger left = new Ledger("Hours left", false);
	private Color glow = Theme.GOLD_DIM;

	public Crest()
	{
		super(new BorderLayout(10, 0));
		setOpaque(false);
		setBorder(new EmptyBorder(12, 10, 12, 10));

		JPanel ringHolder = new JPanel(new BorderLayout());
		ringHolder.setOpaque(false);
		ringHolder.add(ring, BorderLayout.NORTH);
		add(ringHolder, BorderLayout.WEST);

		JPanel right = new JPanel();
		right.setOpaque(false);
		right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
		title.setAlignmentX(LEFT_ALIGNMENT);
		slots.setAlignmentX(LEFT_ALIGNMENT);
		right.add(title);
		right.add(slots);
		JPanel ledgers = new JPanel(new GridLayout(3, 1, 0, 2));
		ledgers.setOpaque(false);
		ledgers.setBorder(new EmptyBorder(4, 0, 0, 0));
		ledgers.add(pages);
		ledgers.add(luck);
		ledgers.add(left);
		ledgers.setAlignmentX(LEFT_ALIGNMENT);
		right.add(ledgers);
		add(right, BorderLayout.CENTER);
		ring.setCaption("logged");
		ring.setProgress(0, false, false);
	}

	public void update(Report report, String playerName)
	{
		if (!report.isLoggedIn())
		{
			title.setText("Log Insight");
			slots.setText("Log in to read your log");
			ring.setProgress(0, false, true);
			pages.set("-", 0, Theme.TEXT);
			luck.set("-", -1, Theme.TEXT);
			left.set("-", -1, Theme.TEXT);
			glow = Theme.GOLD_DIM;
			repaint();
			return;
		}
		int owned = report.owned();
		int total = report.total();
		title.setText(playerName == null ? "Log Insight" : playerName);
		slots.setText(total <= 0 ? "Reading the log..." : Format.count(owned) + " / " + Format.count(total) + " slots");
		ring.setProgress(total <= 0 ? 0 : owned / (double) total, total > 0 && owned >= total, true);
		pages.set(report.getPagesComplete() + "/" + report.getPagesTotal(),
			report.getPagesTotal() == 0 ? 0 : report.getPagesComplete() / (double) report.getPagesTotal(), Theme.TEXT);
		Luck l = report.getLuck();
		if (l == null)
		{
			luck.set("Unread", -1, Theme.TEXT_MUTED);
			glow = Theme.GOLD_DIM;
		}
		else
		{
			Color c = Theme.luck(l.verdict());
			luck.set(l.verdict().getLabel(), -1, c);
			glow = Theme.mix(Theme.GOLD_DIM, c, 0.45);
		}
		left.set(Format.hours(report.getHoursLeft()), -1, Theme.TEXT);
		left.setToolTipText(report.isIronRates() ? "At ironman rates" : "At typical rates, or your own pace where Log Insight has learned it");
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.INK, glow, 0.2), 0, h, Theme.INK));
		g.fillRect(0, 0, w, h);

		// Gilt rule along the bottom with a small diamond at its centre.
		int y = h - 2;
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.GOLD, 0), w / 2f, 0, Theme.alpha(Theme.GOLD, 150), true));
		g.drawLine(0, y, w, y);
		Path2D diamond = new Path2D.Float();
		diamond.moveTo(w / 2f, y - 3);
		diamond.lineTo(w / 2f + 3, y);
		diamond.lineTo(w / 2f, y + 3);
		diamond.lineTo(w / 2f - 3, y);
		diamond.closePath();
		g.setColor(Theme.GOLD);
		g.fill(diamond);
		g.dispose();
		super.paintComponent(graphics);
	}

	/** One labelled value, with a hairline bar under it when the value is a share of something. */
	private static final class Ledger extends JPanel
	{
		private final JLabel value = Ui.label("-", Theme.small(), Theme.TEXT);
		private final ProgressBar bar;

		Ledger(String label, boolean withBar)
		{
			super(new BorderLayout(0, 1));
			setOpaque(false);
			JPanel line = new JPanel(new BorderLayout());
			line.setOpaque(false);
			line.add(Ui.label(label, Theme.small(), Theme.TEXT_MUTED), BorderLayout.WEST);
			line.add(value, BorderLayout.EAST);
			add(line, BorderLayout.CENTER);
			bar = withBar ? new ProgressBar(3) : null;
			if (bar != null)
			{
				add(bar, BorderLayout.SOUTH);
			}
			setPreferredSize(new Dimension(10, 17));
		}

		void set(String text, double share, Color color)
		{
			value.setText(text);
			value.setForeground(color);
			if (bar != null)
			{
				bar.setProgress(Math.max(0, share), share >= 1, true);
			}
		}
	}
}
