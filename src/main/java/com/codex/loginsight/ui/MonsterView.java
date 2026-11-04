package com.codex.loginsight.ui;

import com.codex.loginsight.core.MonsterReport;
import com.codex.loginsight.core.Report;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * One monster's share of the log, opened from the "Log" option on a right-clicked monster: only the slots it drops, each
 * owned or missing with its chance per kill and the share of players who would have it by your kill count, your luck
 * on them, and the kills an average player needs from here for the next one.
 */
final class MonsterView
{
	private MonsterView()
	{
	}

	/**
	 * @param back     returns to the list of pages
	 * @param openPage opens the log page the monster's slots are on
	 * @param wanted   whether a slot is on the wanted list
	 * @param onWant   marks a missing slot wanted or not when its row is clicked, or null to leave rows inert
	 * @param full     the wanted list is full
	 */
	static StackPanel build(MonsterReport monster, ItemIcons icons, Runnable back, Runnable openPage, IntPredicate wanted,
		IntConsumer onWant, boolean full)
	{
		StackPanel stack = new StackPanel(6, 8);
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(new CodexButton("< All pages", false, back), BorderLayout.WEST);
		if (openPage != null)
		{
			top.add(new CodexButton("Whole page", false, openPage), BorderLayout.EAST);
		}
		stack.add(top);

		Card head = new Card(new BorderLayout(10, 0));
		head.setBorder(new EmptyBorder(9, 10, 9, 10));
		head.highlighted(true);
		ProgressRing ring = new ProgressRing(54, 6f);
		ring.setProgress(monster.total() == 0 ? 0 : monster.owned() / (double) monster.total(), monster.isComplete(), false);
		JPanel ringHolder = new JPanel(new BorderLayout());
		ringHolder.setOpaque(false);
		ringHolder.add(ring, BorderLayout.NORTH);
		head.add(ringHolder, BorderLayout.WEST);
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.add(Ui.label(("Log  ·  " + monster.getTab()).toUpperCase(Locale.ENGLISH), Theme.small(), Theme.GOLD));
		text.add(Ui.wrapped(monster.getNpc(), Theme.title(), Theme.GOLD_BRIGHT, 120));
		text.add(Ui.label(monster.owned() + " of " + monster.total() + (monster.total() == 1 ? " slot" : " slots")
			+ (monster.getNpc().equalsIgnoreCase(monster.getPage()) ? "" : "  ·  " + monster.getPage()), Theme.small(),
			Theme.TEXT_MUTED));
		text.add(Ui.label(killLine(monster), Theme.small(), monster.getKills() >= 0 ? Theme.TEXT : Theme.TEXT_FAINT));
		String rates = rates(monster);
		if (rates != null)
		{
			text.add(Ui.wrapped(rates, Theme.small(), Theme.TEXT_FAINT, 120));
		}
		head.add(text, BorderLayout.CENTER);
		if (monster.getBasis() != null)
		{
			head.setToolTipText("Slots without odds on " + (monster.getLabel() == null ? "a kill counter"
				: Ui.escape(monster.getLabel())) + " use the rates for " + Ui.escape(monster.getBasis().toLowerCase(Locale.ENGLISH)) + ".");
		}
		stack.add(head);

		if (monster.getLuck() != null)
		{
			Card dialCard = new Card(new BorderLayout());
			dialCard.setBorder(new EmptyBorder(8, 8, 8, 8));
			LuckDial dial = new LuckDial(104);
			dial.setLuck(monster.getLuck(), false);
			dialCard.add(dial, BorderLayout.CENTER);
			dialCard.setToolTipText("An average player would own " + Format.slots(monster.getLuck().getExpected())
				+ " of these " + monster.getLuck().getSlots() + " slots by your kill count; you own "
				+ monster.getLuck().getOwned() + ".");
			stack.add(dialCard);
		}
		else if (!monster.isComplete())
		{
			String note = note(monster);
			if (note != null)
			{
				stack.add(Rows.note(note, 190));
			}
		}

		if (!monster.isComplete())
		{
			JPanel tiles = new JPanel(new GridLayout(1, 2, 6, 6));
			tiles.setOpaque(false);
			tiles.add(new StatTile(kills(monster.getKillsToNext()), "Next slot",
				monster.getNextName() == null ? "none rated" : monster.getNextName(), Theme.TEAL));
			tiles.add(new StatTile(Format.hours(monster.getHoursToNext()), "At your pace", pace(monster), Theme.EMBER));
			stack.add(tiles);
		}
		List<Report.ItemReport> missing = new ArrayList<>();
		List<Report.ItemReport> owned = new ArrayList<>();
		for (Report.ItemReport item : monster.getItems())
		{
			(item.isOwned() ? owned : missing).add(item);
		}
		if (!missing.isEmpty())
		{
			// Rarest first among the ones you are driest on: the order a hunter reads them in.
			missing.sort(Comparator.comparingDouble(MonsterView::dryOrder));
			stack.add(new SectionHeader("Still to find", onWant == null ? String.valueOf(missing.size())
				: full ? "wanted list full" : "click to want"));
			for (Report.ItemReport item : missing)
			{
				boolean isWanted = wanted.test(item.getItemId());
				Card row = row(item, icons, isWanted, monster.getNextItem() == item.getItemId());
				if (onWant != null && (isWanted || !full))
				{
					row.onClick(() -> onWant.accept(item.getItemId()));
				}
				stack.add(row);
			}
		}
		if (!owned.isEmpty())
		{
			stack.add(new SectionHeader("Found", String.valueOf(owned.size())));
			for (Report.ItemReport item : owned)
			{
				stack.add(row(item, icons, false, false));
			}
		}
		stack.add(Box.createVerticalStrut(2));
		return stack;
	}

	/** "Zulrah kc 1,240", or why there is none. */
	static String killLine(MonsterReport monster)
	{
		if (monster.getLabel() == null)
		{
			return "No kill count kept";
		}
		if (monster.getKills() < 0)
		{
			return Format.counterName(monster.getLabel()) + " kc not read yet";
		}
		return Format.counterName(monster.getLabel()) + " kc " + Format.count(monster.getKills());
	}

	/** "Rates on task" for a monster timed by an activity alone, or null when its counter has the odds. */
	static String rates(MonsterReport monster)
	{
		if (monster.getLabel() != null || monster.getBasis() == null)
		{
			return null;
		}
		String detail = Format.detail(monster.getBasis());
		return detail == null ? null : "Rates " + detail.toLowerCase(Locale.ENGLISH);
	}

	/** Why there is no luck dial yet, or null when there is nothing to say. */
	static String note(MonsterReport monster)
	{
		if (monster.getLabel() == null)
		{
			return "Without a kill count there is no luck to read: each slot shows its rate and the kills to the next.";
		}
		if (monster.getKills() < 0)
		{
			return "Open its page in your collection log, or get a kill, to read your kill count and see your luck here.";
		}
		if (!monster.isRead())
		{
			return "Open its page in your collection log to read what you own and see your luck here.";
		}
		return null;
	}

	/** "~312 kills", "1 kill", or "-" when no slot is left that can come. */
	static String kills(double kills)
	{
		if (Double.isNaN(kills) || Double.isInfinite(kills) || kills <= 0)
		{
			return "-";
		}
		long whole = Math.max(1, Math.round(kills));
		return whole == 1 ? "1 kill" : "~" + Format.count(whole) + " kills";
	}

	/** "38/h, your pace" or "30/h, typical"; "no pace" when there is none. */
	static String pace(MonsterReport monster)
	{
		if (monster.getPerHour() <= 0)
		{
			return "no pace";
		}
		double perHour = monster.getPerHour();
		String rate = perHour >= 10 ? String.valueOf(Math.round(perHour))
			: String.format(Locale.ENGLISH, "%.1f", perHour).replace(".0", "");
		return rate + "/h, " + (monster.isOwnPace() ? "your pace" : "typical");
	}

	/** "1/512 a kill  ·  64% by now", or as much of it as is known. */
	static String detail(Report.ItemReport item)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(item.getChance() > 0 ? Format.chance(item.getChance()) + " a kill" : "Not a roll per kill");
		double by = MonsterReport.byNow(item);
		if (by >= 0)
		{
			sb.append("  ·  ").append(item.getDryChance() == 0 ? "due by now" : Format.have(item.getDryChance()) + " by now");
		}
		return sb.toString();
	}

	private static double dryOrder(Report.ItemReport item)
	{
		return item.getDryChance() < 0 ? 2 + (item.getChance() > 0 ? -item.getChance() : 1) : item.getDryChance();
	}

	private static Card row(Report.ItemReport item, ItemIcons icons, boolean wanted, boolean next)
	{
		Pill tag;
		Color accent = null;
		String extra = null;
		Color extraColor = Theme.TEXT_FAINT;
		if (item.isOwned())
		{
			tag = new Pill(item.getObtained() > 1 ? "Owned x" + Format.count(item.getObtained()) : "Owned",
				item.isPet() ? Theme.VIOLET : Theme.GOLD);
			if (item.getExpectedCount() >= 0)
			{
				extra = "Average by now: " + Format.slots(item.getExpectedCount());
			}
		}
		else
		{
			tag = item.getDryChance() >= 0 ? new Pill(Format.dry(item.getDryChance()), Theme.dry(item.getDryChance()))
				: new Pill("Missing", Theme.TEXT_MUTED);
			if (item.getDryChance() >= 0 && item.getDryChance() < 0.2)
			{
				accent = Theme.dry(item.getDryChance());
			}
			if (wanted)
			{
				extra = "Wanted";
				extraColor = Theme.GOLD;
				accent = Theme.GOLD;
			}
			else if (next)
			{
				extra = "Likeliest next";
				extraColor = Theme.TEAL;
			}
			else if (item.getChance() > 0 && item.getLabel() != null)
			{
				extra = "Half of players by " + Format.count(com.codex.loginsight.core.DryWatch.countFor(item.getChance(), 0.5)) + " kc";
			}
		}
		Card card = Rows.line(new IconBadge(item.getItemId(), icons, item.isOwned()), item.getName(), detail(item), extra,
			extraColor, tag, accent);
		if (wanted)
		{
			card.highlighted(true);
		}
		card.setToolTipText(ItemTile.tooltip(item));
		return card;
	}
}
