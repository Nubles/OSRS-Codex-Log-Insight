package com.codex.loginsight.ui;

import com.codex.loginsight.core.Projection;
import com.codex.loginsight.core.Report;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** One page in depth: its counts and luck, the wait for the next slot, and every slot with its odds. */
final class PageView
{
	private static final int COLUMNS = 5;

	private PageView()
	{
	}

	static StackPanel build(Report.PageReport page, ItemIcons icons, Runnable back)
	{
		return build(page, icons, back, id -> false, null);
	}

	/**
	 * @param wanted whether a slot is on the wanted list
	 * @param onWant marks a missing slot wanted or not when its row is clicked, or null to leave rows inert
	 */
	static StackPanel build(Report.PageReport page, ItemIcons icons, Runnable back, IntPredicate wanted, IntConsumer onWant)
	{
		return build(page, icons, back, wanted, onWant, false);
	}

	/** @param full the wanted list is full: slots not on it say so rather than taking a click that does nothing */
	static StackPanel build(Report.PageReport page, ItemIcons icons, Runnable back, IntPredicate wanted, IntConsumer onWant,
		boolean full)
	{
		StackPanel stack = new StackPanel(6, 8);
		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		top.setOpaque(false);
		top.add(new CodexButton("< All pages", false, back));
		stack.add(top);

		Card head = new Card(new BorderLayout(10, 0));
		head.setBorder(new EmptyBorder(9, 10, 9, 10));
		head.highlighted(true);
		ProgressRing ring = new ProgressRing(54, 6f);
		ring.setProgress(page.progress(), page.isComplete(), false);
		JPanel ringHolder = new JPanel(new BorderLayout());
		ringHolder.setOpaque(false);
		ringHolder.add(ring, BorderLayout.NORTH);
		head.add(ringHolder, BorderLayout.WEST);
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.add(Ui.label(page.getTab().toUpperCase(), Theme.small(), Theme.GOLD));
		text.add(Ui.wrapped(page.getName(), Theme.title(), Theme.GOLD_BRIGHT, 120));
		text.add(Ui.label(page.getOwned() + " of " + page.getTotal() + " slots", Theme.small(), Theme.TEXT_MUTED));
		for (String counter : page.getCounters())
		{
			text.add(Ui.label(counter, Theme.small(), Theme.TEXT));
		}
		head.add(text, BorderLayout.CENTER);
		stack.add(head);

		if (page.getLuck() != null)
		{
			Card dialCard = new Card(new BorderLayout());
			dialCard.setBorder(new EmptyBorder(8, 8, 8, 8));
			LuckDial dial = new LuckDial(104);
			dial.setLuck(page.getLuck(), false);
			dialCard.add(dial, BorderLayout.CENTER);
			dialCard.setToolTipText("An average player would own " + String.format(java.util.Locale.ENGLISH, "%.1f", page.getLuck().getExpected())
				+ " of these " + page.getLuck().getSlots() + " slots by now; you own " + page.getLuck().getOwned() + ".");
			stack.add(dialCard);
		}
		else if (!page.isComplete() && !page.isRead())
		{
			stack.add(Rows.note("Open this page in your collection log to read its kill count and see your luck here.", 190));
		}

		if (!page.isComplete())
		{
			JPanel tiles = new JPanel(new GridLayout(1, 2, 6, 6));
			tiles.setOpaque(false);
			tiles.add(new StatTile(Format.hours(page.getHoursToNext()), "Next slot",
				page.getNextSource() == null ? "no rates" : Format.source(page.getNextSource()), Theme.TEAL));
			String finishNote = page.getUnrated() > 0 ? "+" + page.getUnrated() + " unrated" : "whole page";
			tiles.add(new StatTile(Format.hours(page.getHoursToFinish()), "To finish", finishNote, Theme.EMBER));
			stack.add(tiles);
			if (page.getProjection() != null)
			{
				stack.add(projection(page.getProjection()));
			}
		}

		List<Report.ItemReport> missing = new ArrayList<>();
		List<Report.ItemReport> owned = new ArrayList<>();
		for (Report.ItemReport item : page.getItems())
		{
			(item.isOwned() ? owned : missing).add(item);
		}
		if (!missing.isEmpty())
		{
			missing.sort(Comparator.comparingDouble(PageView::dryOrder));
			stack.add(new SectionHeader("Still to find", onWant == null ? String.valueOf(missing.size())
				: full ? "wanted list full" : "click to want"));
			for (Report.ItemReport item : missing)
			{
				boolean isWanted = wanted.test(item.getItemId());
				Card row = missingRow(item, icons, false, page.getNextSource(), isWanted);
				if (isWanted)
				{
					row.setToolTipText("<html>On your wanted list. Click to take it off.<br>" + stripHtml(row.getToolTipText()) + "</html>");
				}
				else if (onWant != null)
				{
					row.setToolTipText("<html>" + (full ? "Your wanted list is full. Take a slot off it to add this one."
						: "Click to add it to your wanted list.") + "<br>" + stripHtml(row.getToolTipText()) + "</html>");
				}
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
			stack.add(grid(owned, icons));
		}
		return stack;
	}

	/** The green log curve: the chance the page is done against hours, with the half and nine-in-ten marks. */
	static Card projection(Projection projection)
	{
		Card card = new Card(new BorderLayout(0, 4));
		card.setBorder(new EmptyBorder(7, 9, 7, 9));
		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		head.add(Ui.label("GREEN LOG", Theme.small(), Theme.TEXT_MUTED), BorderLayout.WEST);
		head.add(Ui.label("chance done", Theme.small(), Theme.TEXT_FAINT), BorderLayout.EAST);
		card.add(head, BorderLayout.NORTH);
		card.add(new ProjectionChart(projection), BorderLayout.CENTER);
		String text = "Half of players finish by " + Format.hours(projection.getMedian()) + ", nine in ten by "
			+ Format.hours(projection.getLikely()) + " at your rates.";
		if (projection.getUnrated() > 0)
		{
			text += " " + projection.getUnrated() + (projection.getUnrated() == 1 ? " slot has" : " slots have")
				+ " no rates and " + (projection.getUnrated() == 1 ? "is" : "are") + " not counted.";
		}
		card.add(Ui.wrapped(text, Theme.small(), Theme.TEXT_MUTED, 168), BorderLayout.SOUTH);
		return card;
	}

	private static String stripHtml(String tip)
	{
		return tip == null ? "" : tip.replace("<html>", "").replace("</html>", "");
	}

	/** Missing slots with a known dry chance first, driest at the top. */
	private static double dryOrder(Report.ItemReport item)
	{
		return item.getDryChance() < 0 ? 2 + (Double.isFinite(item.getHours()) ? 0 : 1) : item.getDryChance();
	}

	static JPanel grid(List<Report.ItemReport> items, ItemIcons icons)
	{
		JPanel grid = new JPanel(new GridLayout(0, COLUMNS, 4, 4));
		grid.setOpaque(false);
		for (Report.ItemReport item : items)
		{
			grid.add(new ItemTile(item, icons));
		}
		for (int i = items.size(); i % COLUMNS != 0; i++)
		{
			JPanel filler = new JPanel();
			filler.setOpaque(false);
			grid.add(filler);
		}
		return grid;
	}

	/** A missing slot: its odds at your count, how dry you are, and where and how soon it comes alone. */
	static Card missingRow(Report.ItemReport item, ItemIcons icons, boolean withPage)
	{
		return missingRow(item, icons, withPage, null);
	}

	/** @param pageSource the activity the page is mostly hunted at, so its own slots just say "alone" */
	static Card missingRow(Report.ItemReport item, ItemIcons icons, boolean withPage, String pageSource)
	{
		return missingRow(item, icons, withPage, pageSource, false);
	}

	/** @param wanted the slot is on the wanted list: the card is gilt and its last line says so */
	static Card missingRow(Report.ItemReport item, ItemIcons icons, boolean withPage, String pageSource, boolean wanted)
	{
		StringBuilder detail = new StringBuilder(Format.chance(item.getChance()));
		if (item.getCount() >= 0)
		{
			detail.append(" at ").append(Format.count(item.getCount())).append(" kc");
		}
		String where = null;
		if (item.getSource() != null && Double.isFinite(item.getHours()))
		{
			where = item.getSource().equals(pageSource)
				? Format.about(item.getHours()) + " on its own"
				: Format.about(item.getHours()) + "  ·  " + Format.source(item.getSource());
		}
		if (withPage && item.getPage() != null)
		{
			where = item.getPage() + (where == null ? "" : "  ·  " + Format.about(item.getHours()));
		}
		Pill dry = null;
		if (item.getDryChance() >= 0)
		{
			dry = new Pill(Format.dry(item.getDryChance()), Theme.dry(item.getDryChance()));
		}
		if (wanted)
		{
			where = "Wanted" + (where == null ? "" : "  ·  " + where);
		}
		Card card = Rows.line(new IconBadge(item.getItemId(), icons, false), item.getName(), detail.toString(), where,
			wanted ? Theme.GOLD : Theme.TEXT_FAINT, dry,
			wanted ? Theme.GOLD : item.getDryChance() >= 0 && item.getDryChance() < 0.2 ? Theme.dry(item.getDryChance()) : null);
		if (wanted)
		{
			card.highlighted(true);
		}
		card.setToolTipText(ItemTile.tooltip(item));
		return card;
	}
}
