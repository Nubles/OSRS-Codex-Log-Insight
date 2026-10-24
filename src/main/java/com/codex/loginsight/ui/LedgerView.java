package com.codex.loginsight.ui;

import com.codex.loginsight.core.Luck;
import com.codex.loginsight.core.Progress;
import com.codex.loginsight.core.Report;
import com.codex.loginsight.core.Session;
import com.codex.loginsight.core.Setup;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** The log at a glance: luck, the numbers, the best hunts, the longest waits and recent finds. */
final class LedgerView
{
	private static final int WIDTH = 190;

	private LedgerView()
	{
	}

	/** The Vs average tile's note before any kill count is read: short enough not to be cut at sidebar width. */
	static final String NO_COUNTS = "no kill counts";

	static StackPanel build(Report report, LuckDial dial, ItemIcons icons, Consumer<String> openPage, Runnable openQuarry, long now)
	{
		return build(report, dial, icons, openPage, openQuarry, now, null);
	}

	/** @param share the finest finds card's save and copy row, shown under the podium, or null for none */
	static StackPanel build(Report report, LuckDial dial, ItemIcons icons, Consumer<String> openPage, Runnable openQuarry, long now,
		javax.swing.JComponent share)
	{
		StackPanel stack = new StackPanel(6, 8);
		if (!report.isLoggedIn())
		{
			stack.add(welcome());
			return stack;
		}

		Setup setup = report.setup();
		if (!setup.isDone())
		{
			stack.add(setupCard(setup, openPage));
		}

		Card dialCard = new Card(new BorderLayout());
		dialCard.setBorder(new EmptyBorder(8, 8, 8, 8));
		dial.setLuck(report.getLuck(), true);
		dialCard.add(dial, BorderLayout.CENTER);
		Luck luck = report.getLuck();
		if (luck != null)
		{
			dialCard.setToolTipText("<html>Across " + luck.getSlots() + " slots with known odds and kill counts.<br>"
				+ "An average player would own " + Math.round(luck.getExpected()) + " of them; you own " + luck.getOwned() + ".</html>");
		}
		stack.add(dialCard);

		// The crest already carries slots, pages and hours; the tiles add what it cannot say.
		List<Report.Hunt> hunts = new java.util.ArrayList<>();
		for (Report.Hunt hunt : report.getHunts())
		{
			if (hunt.getLockedBy() == null && hunts.size() < 3)
			{
				hunts.add(hunt);
			}
		}
		JPanel tiles = new JPanel(new GridLayout(2, 2, 6, 6));
		tiles.setOpaque(false);
		if (luck != null)
		{
			long diff = Math.round(luck.getOwned() - luck.getExpected());
			tiles.add(new StatTile((diff > 0 ? "+" : "") + diff, "Vs average", "at your counts",
				diff >= 0 ? Theme.GREEN : Theme.EMBER));
		}
		else
		{
			tiles.add(new StatTile("-", "Vs average", NO_COUNTS, Theme.TEXT_MUTED));
		}
		if (!report.ownedRead())
		{
			tiles.add(new StatTile("-", "Next slot", "after a log read", Theme.TEXT_MUTED));
		}
		else if (hunts.isEmpty())
		{
			tiles.add(new StatTile("-", "Next slot", "nothing rated", Theme.TEXT_MUTED));
		}
		else
		{
			tiles.add(new StatTile(Format.hours(hunts.get(0).getHoursToNext()), "Next slot",
				Format.source(hunts.get(0).getName()), Theme.TEAL));
		}
		int pets = 0;
		int petsOwned = 0;
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (Report.PageReport page : report.getPages())
		{
			for (Report.ItemReport item : page.getItems())
			{
				if (item.isPet() && seen.add(item.getItemId()))
				{
					pets++;
					petsOwned += item.isOwned() ? 1 : 0;
				}
			}
		}
		tiles.add(new StatTile(petsOwned + "/" + pets, "Pets", pets == petsOwned ? "every one" : (pets - petsOwned) + " to go",
			Theme.VIOLET));
		tiles.add(new StatTile(report.getPagesRead() + "/" + report.getPagesTotal(), "Pages read",
			report.getPagesRead() < report.getPagesTotal() ? "open for luck" : "all kill counts", Theme.GOLD));
		stack.add(tiles);
		if (report.getDrops() != null)
		{
			stack.add(PageView.drops(report.getDrops(), Format.count(report.getDrops().getSlots()) + " slots"));
		}

		// The finest drops on a podium: the card worth a screenshot.
		if (!report.getTrophies().isEmpty())
		{
			stack.add(new SectionHeader("Finest finds", "rarest seen"));
			stack.add(new TrophyShelf(report.getTrophies(), icons));
			if (share != null)
			{
				stack.add(share);
			}
		}

		if (!report.getWanted().isEmpty())
		{
			stack.add(new SectionHeader("Wanted", report.getWanted().size() + " of " + Progress.MAX_WANTED));
			for (Report.ItemReport item : report.getWanted())
			{
				Card card = wantedCard(item, icons);
				if (item.getPage() != null)
				{
					card.onClick(() -> openPage.accept(item.getPage()));
				}
				stack.add(card);
			}
		}

		Session.Summary session = report.getSession();
		if (session != null)
		{
			stack.add(new SectionHeader("This session", Format.duration(session.getMillis())));
			stack.add(sessionCard(session));
		}

		if (!hunts.isEmpty() && report.ownedRead())
		{
			stack.add(new SectionHeader("Best next hunts", "all in Quarry"));
			for (int i = 0; i < hunts.size(); i++)
			{
				stack.add(QuarryView.row(hunts.get(i), i + 1, icons).onClick(openQuarry));
			}
		}

		if (!report.getDriest().isEmpty() && report.ownedRead())
		{
			stack.add(new SectionHeader("Longest waits"));
			for (Report.ItemReport item : report.getDriest())
			{
				Card card = PageView.missingRow(item, icons, true);
				if (item.getPage() != null)
				{
					card.onClick(() -> openPage.accept(item.getPage()));
				}
				stack.add(card);
			}
		}

		List<Progress.Find> finds = report.getFinds();
		stack.add(new SectionHeader("Recent finds"));
		if (finds.isEmpty())
		{
			stack.add(Rows.note("New slots will be written here as they drop, with how lucky each one was.", WIDTH));
		}
		else
		{
			for (int i = 0; i < Math.min(6, finds.size()); i++)
			{
				stack.add(findRow(finds.get(i), icons, now));
			}
		}
		return stack;
	}

	/** Logged out: what the Ledger opens on, so the page promises something rather than sitting blank. */
	private static Card welcome()
	{
		Card card = new Card(new BorderLayout()).highlighted(true);
		card.setBorder(new EmptyBorder(10, 12, 12, 12));
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
		JComponent head = Ui.label("YOUR LOG, READ", Theme.small(), Theme.GOLD);
		head.setAlignmentX(0f);
		text.add(head);
		JComponent body = Ui.wrapped("Log in and Log Insight reads your collection log: how lucky you have been, the slots "
			+ "quickest to get next, and your finest finds on a podium.", Theme.body(), Theme.TEXT, WIDTH - 12);
		body.setAlignmentX(0f);
		text.add(body);
		JComponent note = Ui.wrapped("It only reads what your client shows. Nothing leaves your computer.", Theme.small(),
			Theme.TEXT_MUTED, WIDTH - 12);
		note.setBorder(new EmptyBorder(6, 0, 0, 0));
		note.setAlignmentX(0f);
		text.add(note);
		card.add(text, BorderLayout.CENTER);
		return card;
	}

	/** Kill counts added since logging in, the slots they gave, and how that compares with an average player. */
	static Card sessionCard(Session.Summary session)
	{
		Card card = new Card(new BorderLayout(0, 6));
		card.setBorder(new EmptyBorder(7, 10, 8, 10));
		if (session.isEmpty())
		{
			card.add(Ui.wrapped("Kill counts you add from now on show here, with the slots they give and how lucky "
				+ "that was.", Theme.small(), Theme.TEXT_MUTED, 170), BorderLayout.CENTER);
			return card;
		}
		StackPanel rows = new StackPanel(3, 0);
		List<Session.Gain> gains = session.getGains();
		for (int i = 0; i < Math.min(3, gains.size()); i++)
		{
			Session.Gain gain = gains.get(i);
			rows.add(pair(Format.counterName(gain.getLabel()), "+" + Format.count(gain.getAdded()), Theme.TEXT, Theme.GOLD));
		}
		if (gains.size() > 3)
		{
			int rest = 0;
			for (int i = 3; i < gains.size(); i++)
			{
				rest += gains.get(i).getAdded();
			}
			rows.add(pair((gains.size() - 3) + " more", "+" + Format.count(rest), Theme.TEXT_MUTED, Theme.TEXT_MUTED));
		}
		Luck luck = session.getLuck();
		int found = session.getFinds().size();
		String expected = luck == null ? "" : "  ·  avg " + Format.slots(luck.getExpected());
		rows.add(pair("New slots", found + expected, Theme.TEXT, found > 0 ? Theme.GREEN : Theme.TEXT_MUTED));
		card.add(rows, BorderLayout.CENTER);
		if (luck != null)
		{
			Color color = Theme.luck(luck.getPercentile());
			card.add(Ui.label(luck.verdict().getLabel() + "  ·  luckier than " + luck.luckierThan() + "%", Theme.small(), color),
				BorderLayout.SOUTH);
			card.accent(color);
			card.setToolTipText("<html>Across the " + luck.getSlots() + " slots tonight's kills could have given.<br>"
				+ "An average player would have found " + String.format(java.util.Locale.ENGLISH, "%.2f", luck.getExpected())
				+ " of them; you found " + luck.getOwned() + ".</html>");
		}
		return card;
	}

	/**
	 * A slot you are after: how dry you are on it, the counts by which half and nine in ten of players have it, and
	 * the play after which the odds of it are even.
	 */
	static Card wantedCard(Report.ItemReport item, ItemIcons icons)
	{
		StringBuilder odds = new StringBuilder(Format.chance(item.getChance()));
		if (item.getCount() >= 0)
		{
			odds.append(" at ").append(Format.count(item.getCount())).append(" kc");
		}
		Pill dry = item.getDryChance() >= 0 ? new Pill(Format.dry(item.getDryChance()), Theme.dry(item.getDryChance())) : null;
		Card card = Rows.line(new IconBadge(item.getItemId(), icons, false), item.getName(), odds.toString(), dry, Theme.GOLD);
		card.highlighted(true);
		JPanel more = new JPanel();
		more.setOpaque(false);
		more.setLayout(new javax.swing.BoxLayout(more, javax.swing.BoxLayout.Y_AXIS));
		more.setBorder(new EmptyBorder(5, 0, 0, 0));
		if (item.getDryChance() >= 0)
		{
			more.add(new DryBar(item.getDryChance()));
			more.add(javax.swing.Box.createVerticalStrut(3));
		}
		String marks = marks(item);
		if (marks != null)
		{
			more.add(left(Ui.label(marks, Theme.small(), Theme.TEXT_MUTED)));
		}
		double even = com.codex.loginsight.core.Wanted.evenChanceHours(item);
		if (even > 0 && item.getSource() != null)
		{
			// The even-chance mark and the average are both said, so this card and the page's "~98h on its own" agree.
			JLabel when = Ui.label("Even odds in " + Format.hours(even) + "  ·  " + Format.about(item.getHours()) + " average",
				Theme.small(), Theme.TEAL);
			when.setToolTipText("Hunting it at " + Format.source(item.getSource()) + ": half of players have it within "
				+ Format.hours(even) + "; the average wait is " + Format.hours(item.getHours()) + ".");
			more.add(left(when));
		}
		else if (item.getSource() == null)
		{
			more.add(left(Ui.label("No timed source yet", Theme.small(), Theme.TEXT_FAINT)));
		}
		// The text column already lays out name and odds; the rest runs the card's full width beneath.
		card.add(more, BorderLayout.SOUTH);
		card.setToolTipText(ItemTile.tooltip(item));
		return card;
	}

	/** "Half by 2,079 · 1,667 to go" until you pass half, then the nine-in-ten mark; null without odds. */
	static String marks(Report.ItemReport item)
	{
		int half = com.codex.loginsight.core.Wanted.countFor(item, 0.5);
		int most = com.codex.loginsight.core.Wanted.countFor(item, 0.9);
		if (half < 0 || item.getCount() < 0)
		{
			return half < 0 ? null : "Half of players by " + Format.count(half) + " kc";
		}
		if (item.getCount() < half)
		{
			return "Half by " + Format.count(half) + "  ·  " + Format.count(half - item.getCount()) + " to go";
		}
		if (item.getCount() < most)
		{
			return "9 in 10 by " + Format.count(most) + "  ·  " + Format.count(most - item.getCount()) + " to go";
		}
		return "Past 9 in 10 at " + Format.count(most) + " kc";
	}

	private static JComponent left(JComponent c)
	{
		c.setAlignmentX(0f);
		return c;
	}

	private static JPanel pair(String left, String right, Color leftColor, Color rightColor)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		javax.swing.JLabel name = Ui.label(left, Theme.small(), leftColor);
		name.setMinimumSize(new java.awt.Dimension(10, name.getPreferredSize().height));
		name.setPreferredSize(new java.awt.Dimension(10, name.getPreferredSize().height));
		row.add(name, BorderLayout.CENTER);
		row.add(Ui.label(right, Theme.small(), rightColor), BorderLayout.EAST);
		return row;
	}

	/**
	 * The three things that make the numbers trustworthy, each ticked off as it is done: the whole log read, the
	 * game announcing new slots, and kill counts on the pages you have finds on. Gone once all three are done.
	 */
	static Card setupCard(Setup setup, Consumer<String> openPage)
	{
		Card card = new Card(new BorderLayout(0, 4));
		card.accent(Theme.GOLD).highlighted(true);
		card.setBorder(new EmptyBorder(7, 10, 8, 10));
		card.add(Ui.label("Getting your log ready", Theme.body(), Theme.GOLD_BRIGHT), BorderLayout.NORTH);
		StackPanel steps = new StackPanel(5, 0);
		steps.add(step(setup.isLogRead() ? Mark.Kind.GOOD : Mark.Kind.WARN, "Whole log read",
			setup.isLogRead() ? null : "Open your collection log and press its Search button once."));
		if (setup.noticesKnown())
		{
			steps.add(step(setup.noticesOff() ? Mark.Kind.BAD : Mark.Kind.GOOD, "New slots announced",
				setup.noticesOff() ? "The game announces none, so finds go unseen. Turn on the collection log's new"
					+ " addition notification (chat or pop-up) in the game's settings." : null));
		}
		boolean counted = setup.getToOpenTotal() == 0;
		// On a first day nothing is owned, so nothing is missing a count, but "0 of 73" beside a tick reads as a
		// mistake: it says how the counts arrive instead.
		boolean firstDay = counted && setup.getPagesCounted() == 0 && setup.getPagesCountable() > 0;
		steps.add(step(firstDay ? Mark.Kind.NOTE : counted ? Mark.Kind.GOOD : Mark.Kind.NOTE, "Kill counts on "
			+ setup.getPagesCounted() + " of " + setup.getPagesCountable() + " pages", firstDay
			? "Each page's count is read when you open it." : counted ? null : "Open these once to count your finds there:"));
		for (Report.PageReport page : setup.getToOpen())
		{
			// The name gives way before the count does: "Fishing from the Reward Pool" is wider than the card.
			JPanel link = new JPanel(new BorderLayout(6, 0));
			link.setOpaque(false);
			JComponent name = Ui.label(page.getName(), Theme.small(), Theme.GOLD);
			name.setMinimumSize(new java.awt.Dimension(10, name.getPreferredSize().height));
			link.add(name, BorderLayout.CENTER);
			link.add(Ui.label(page.getOwned() + (page.getOwned() == 1 ? " slot" : " slots"), Theme.small(), Theme.TEXT_MUTED),
				BorderLayout.EAST);
			link.setToolTipText("Open " + page.getName());
			link.setBorder(new EmptyBorder(0, 18, 0, 0));
			link.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			link.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseReleased(java.awt.event.MouseEvent e)
				{
					openPage.accept(page.getName());
				}
			});
			steps.add(link);
		}
		int more = setup.getToOpenTotal() - setup.getToOpen().size();
		if (more > 0)
		{
			JComponent rest = Ui.label("and " + more + " more", Theme.small(), Theme.TEXT_FAINT);
			rest.setBorder(new EmptyBorder(0, 18, 0, 0));
			steps.add(rest);
		}
		card.add(steps, BorderLayout.CENTER);
		return card;
	}

	/** One setup step: its mark, what it is, and what to do while it is not done. */
	private static JComponent step(Mark.Kind kind, String title, String todo)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		JPanel markHolder = new JPanel(new BorderLayout());
		markHolder.setOpaque(false);
		markHolder.add(new Mark(kind, 12), BorderLayout.NORTH);
		row.add(markHolder, BorderLayout.WEST);
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
		JComponent head = Ui.label(title, Theme.small(), kind == Mark.Kind.GOOD ? Theme.TEXT_MUTED : Theme.TEXT);
		head.setAlignmentX(0f);
		text.add(head);
		if (todo != null)
		{
			JComponent body = Ui.wrapped(todo, Theme.small(), Theme.TEXT_MUTED, 152);
			body.setAlignmentX(0f);
			text.add(body);
		}
		row.add(text, BorderLayout.CENTER);
		return row;
	}

	/** A find: what, the odds and count it came at, then how lucky it was and when. */
	private static JComponent findRow(Progress.Find find, ItemIcons icons, long now)
	{
		StringBuilder detail = new StringBuilder();
		if (find.getChance() > 0)
		{
			detail.append(Format.chance(find.getChance()));
		}
		if (find.getCount() > 0)
		{
			detail.append(detail.length() > 0 ? " at " : "").append(Format.count(find.getCount())).append(" kc");
		}
		if (detail.length() == 0 && find.getPage() != null)
		{
			detail.append(find.getPage());
		}
		String when = Format.ago(find.getAt(), now);
		boolean known = find.getLuckier() >= 0;
		String verdict = known ? "Luckier than " + Math.round(find.getLuckier() * 100) + "%  ·  " + when : when;
		Color color = known ? Theme.luck(find.getLuckier()) : Theme.GOLD;
		return Rows.line(new IconBadge(find.getItemId(), icons, true), find.getName(), detail.toString(), verdict,
			known ? color : Theme.TEXT_FAINT, null, color);
	}
}
