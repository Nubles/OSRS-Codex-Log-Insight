package com.codex.loginsight.ui;

import com.codex.loginsight.core.Report;
import com.codex.loginsight.data.Activity;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Every activity ranked by the expected wait for its next new slot. */
final class QuarryView
{
	static final String[] FILTERS = {"All", "Combat", "Games", "Other", "Pets"};
	private static final int SHOWN = 40;

	private QuarryView()
	{
	}

	static StackPanel build(Report report, int filter, ItemIcons icons)
	{
		StackPanel stack = new StackPanel(5, 8);
		if (!report.isLoggedIn())
		{
			stack.add(Rows.note("Log in to rank where your next slots will come from.", 190));
			return stack;
		}
		boolean pets = filter == 4;
		stack.add(Rows.note((pets ? "Ranked by the wait for a pet" : "Ranked by the wait for a new slot")
			+ (report.isIronRates() ? " at ironman rates" : " at typical rates")
			+ ". Activities you do often switch to your own pace; ones you cannot do yet come last.", 190));
		List<Report.Hunt> hunts = new ArrayList<>();
		for (Report.Hunt hunt : report.getHunts())
		{
			if (matches(hunt, filter))
			{
				hunts.add(hunt);
			}
		}
		if (pets)
		{
			hunts.sort(Comparator.comparing((Report.Hunt h) -> h.getLockedBy() != null)
				.thenComparingDouble(Report.Hunt::getHoursToPet));
		}
		int rank = 0;
		for (Report.Hunt hunt : hunts)
		{
			rank++;
			stack.add(row(hunt, rank, icons, pets));
			if (rank >= SHOWN)
			{
				break;
			}
		}
		if (rank == 0)
		{
			stack.add(Rows.note("Nothing left to hunt here.", 190));
		}
		return stack;
	}

	private static boolean matches(Report.Hunt hunt, int filter)
	{
		switch (filter)
		{
			case 1:
				return hunt.getCategory() == Activity.Category.COMBAT;
			case 2:
				return hunt.getCategory() == Activity.Category.MINIGAME;
			case 3:
				return hunt.getCategory() == Activity.Category.OTHER;
			case 4:
				return hunt.isPet();
			default:
				return true;
		}
	}

	static Card row(Report.Hunt hunt, int rank, ItemIcons icons)
	{
		return row(hunt, rank, icons, false);
	}

	/** @param pets lead with the wait for this activity's pet rather than its next slot */
	static Card row(Report.Hunt hunt, int rank, ItemIcons icons, boolean pets)
	{
		boolean locked = hunt.getLockedBy() != null;
		Card card = new Card(new BorderLayout(8, 0));
		card.setBorder(new EmptyBorder(6, 8, 6, 8));
		if (rank == 1 && !locked)
		{
			card.accent(Theme.TEAL);
		}
		JPanel lead = new JPanel(new BorderLayout());
		lead.setOpaque(false);
		lead.add(new Rank(rank, locked), BorderLayout.NORTH);
		card.add(lead, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.add(Ui.clamped(Format.source(hunt.getName()), Theme.body(), locked ? Theme.TEXT_MUTED : Theme.TEXT, 118, 2));
		String detail = Format.detail(hunt.getName());
		if (detail != null && !detail.isEmpty())
		{
			text.add(Ui.clamped(detail, Theme.small(), Theme.GOLD_DIM, 118, 1));
		}
		if (locked)
		{
			text.add(Ui.clamped("Needs " + hunt.getLockedBy(), Theme.small(), Theme.EMBER, 118, 1));
		}
		if (pets && hunt.getPetName() != null)
		{
			text.add(Ui.label("Pet " + Format.about(hunt.getHoursToPet()) + "  ·  " + hunt.getRemaining() + " left",
				Theme.small(), Theme.VIOLET));
		}
		else
		{
			text.add(Ui.label("Next " + Format.about(hunt.getHoursToNext()) + "  ·  " + hunt.getRemaining() + " left",
				Theme.small(), locked ? Theme.TEXT_MUTED : Theme.TEAL));
		}
		String next = "";
		if (hunt.getNextItemName() != null)
		{
			long after = Math.round(1 / hunt.getNextChance());
			next = hunt.getNextItemName() + (hunt.isNextEarned()
				? (after <= 1 ? ", every time" : ", earned in " + Format.count(after))
				: " " + Format.chance(hunt.getNextChance()));
		}
		if (pets && hunt.getPetName() != null)
		{
			next = hunt.getPetName();
		}
		JLabel nextLabel = Ui.label(next, Theme.small(), Theme.TEXT_MUTED);
		nextLabel.setToolTipText(next);
		text.add(nextLabel);
		text.add(Ui.label("All " + hunt.getRemaining() + " " + Format.about(hunt.getHoursToFinish())
			+ (hunt.isOwnPace() ? "  ·  your pace" : ""), Theme.small(), hunt.isOwnPace() ? Theme.GOLD : Theme.TEXT_FAINT));
		text.setMinimumSize(new Dimension(10, 10));
		text.setPreferredSize(new Dimension(10, text.getPreferredSize().height));
		card.add(text, BorderLayout.CENTER);

		int shown = pets && hunt.getPetItemId() > 0 ? hunt.getPetItemId() : hunt.getNextItemId();
		if (shown > 0)
		{
			JPanel trail = new JPanel(new BorderLayout());
			trail.setOpaque(false);
			trail.add(new IconBadge(shown, icons, false), BorderLayout.NORTH);
			card.add(trail, BorderLayout.EAST);
		}
		card.setToolTipText("<html>" + Ui.escape(hunt.getName()) + "<br>" + Math.round(hunt.getPerHour()) + " per hour"
			+ (hunt.isOwnPace() ? " (your pace)" : "") + "</html>");
		return card;
	}

	/** A small gilt medallion with the rank number. */
	static final class Rank extends JComponent
	{
		private final int rank;
		private final boolean locked;

		Rank(int rank, boolean locked)
		{
			this.rank = rank;
			this.locked = locked;
			Dimension d = new Dimension(20, 20);
			setPreferredSize(d);
			setMinimumSize(d);
			setMaximumSize(d);
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			boolean first = rank <= 3 && !locked;
			g.setColor(first ? Theme.alpha(Theme.GOLD, 60) : Theme.INK_DEEP);
			g.fill(new Ellipse2D.Float(1, 1, 18, 18));
			g.setColor(first ? Theme.GOLD : Theme.STROKE);
			g.draw(new Ellipse2D.Float(1, 1, 18, 18));
			g.setFont(Theme.small());
			FontMetrics fm = g.getFontMetrics();
			String text = String.valueOf(rank);
			ProgressRing.drawShadowed(g, text, 10 - fm.stringWidth(text) / 2, 10 + fm.getAscent() / 2 - 1,
				first ? Theme.GOLD_BRIGHT : Theme.TEXT_MUTED);
			g.dispose();
		}
	}
}
