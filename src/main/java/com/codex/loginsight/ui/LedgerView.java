package com.codex.loginsight.ui;

import com.codex.loginsight.core.Luck;
import com.codex.loginsight.core.Progress;
import com.codex.loginsight.core.Report;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** The log at a glance: luck, the numbers, the best hunts, the longest waits and recent finds. */
final class LedgerView
{
	private static final int WIDTH = 190;

	private LedgerView()
	{
	}

	static StackPanel build(Report report, LuckDial dial, ItemIcons icons, Consumer<String> openPage, Runnable openQuarry, long now)
	{
		StackPanel stack = new StackPanel(6, 8);
		if (!report.isLoggedIn())
		{
			stack.add(Rows.note("Log in and Log Insight will read your collection log.", WIDTH));
			return stack;
		}

		if (report.needsFullRead())
		{
			stack.add(callout("Read your whole log",
				"Open your collection log and press its Search button once. Log Insight reads every slot you own from it, "
					+ "and each page you open also refreshes that page's kill counts."));
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

		JPanel tiles = new JPanel(new GridLayout(2, 2, 6, 6));
		tiles.setOpaque(false);
		tiles.add(new StatTile(Format.count(report.owned()), "Owned", "of " + Format.count(report.total()) + " slots", Theme.GOLD_BRIGHT));
		tiles.add(new StatTile(report.getPagesComplete() + "/" + report.getPagesTotal(), "Pages done",
			report.getPagesRead() + " pages read", Theme.TEAL));
		if (luck != null)
		{
			long diff = Math.round(luck.getOwned() - luck.getExpected());
			tiles.add(new StatTile((diff > 0 ? "+" : "") + diff, "Vs average", "at your counts",
				diff >= 0 ? Theme.GREEN : Theme.EMBER));
		}
		else
		{
			tiles.add(new StatTile("-", "Vs average", "needs kill counts", Theme.TEXT_MUTED));
		}
		tiles.add(new StatTile(Format.hours(report.getHoursLeft()), "Hours left",
			report.isIronRates() ? "at ironman rates" : "at your rates", Theme.EMBER));
		stack.add(tiles);

		List<Report.Hunt> hunts = report.getHunts();
		if (!hunts.isEmpty())
		{
			stack.add(new SectionHeader("Best next hunts", "all in Quarry"));
			for (int i = 0; i < Math.min(3, hunts.size()); i++)
			{
				stack.add(QuarryView.row(hunts.get(i), i + 1, icons).onClick(openQuarry));
			}
		}

		if (!report.getDriest().isEmpty())
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

	static Card callout(String title, String text)
	{
		Card card = new Card(new BorderLayout(0, 3));
		card.accent(Theme.GOLD).highlighted(true);
		card.setBorder(new EmptyBorder(7, 10, 8, 10));
		card.add(Ui.label(title, Theme.body(), Theme.GOLD_BRIGHT), BorderLayout.NORTH);
		card.add(Ui.wrapped(text, Theme.small(), Theme.TEXT, 170), BorderLayout.CENTER);
		return card;
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
