package com.codex.loginsight.ui;

import com.codex.loginsight.core.Report;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Every page of the log, grouped by tab, each with its progress, kill count and luck. */
final class FoliosView
{
	private FoliosView()
	{
	}

	static StackPanel build(Report report, String filter, Consumer<String> openPage)
	{
		StackPanel stack = new StackPanel(5, 8);
		if (!report.isLoggedIn())
		{
			stack.add(Rows.note("Log in to see your log page by page.", 190));
			return stack;
		}
		if (report.getPages().isEmpty())
		{
			stack.add(Rows.note("Reading the log's pages from the game...", 190));
			return stack;
		}
		String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ENGLISH);
		Map<String, List<Report.PageReport>> byTab = new LinkedHashMap<>();
		for (Report.PageReport page : report.getPages())
		{
			if (needle.isEmpty() || page.getName().toLowerCase(Locale.ENGLISH).contains(needle) || matchesItem(page, needle))
			{
				byTab.computeIfAbsent(page.getTab(), k -> new ArrayList<>()).add(page);
			}
		}
		if (byTab.isEmpty())
		{
			stack.add(Rows.note("No page or slot matches \"" + filter.trim() + "\".", 190));
			return stack;
		}
		for (Map.Entry<String, List<Report.PageReport>> tab : byTab.entrySet())
		{
			int done = 0;
			for (Report.PageReport page : tab.getValue())
			{
				done += page.isComplete() ? 1 : 0;
			}
			stack.add(new SectionHeader(tab.getKey(), done + "/" + tab.getValue().size()));
			for (Report.PageReport page : tab.getValue())
			{
				stack.add(row(page).onClick(() -> openPage.accept(page.getName())));
			}
		}
		return stack;
	}

	private static boolean matchesItem(Report.PageReport page, String needle)
	{
		for (Report.ItemReport item : page.getItems())
		{
			if (item.getName().toLowerCase(Locale.ENGLISH).contains(needle))
			{
				return true;
			}
		}
		return false;
	}

	static Card row(Report.PageReport page)
	{
		Card card = new Card(new BorderLayout(0, 3));
		card.setBorder(new EmptyBorder(6, 10, 7, 10));
		if (page.isComplete())
		{
			card.accent(Theme.GREEN);
		}
		else if (page.getLuck() != null)
		{
			card.accent(Theme.luck(page.getLuck().verdict()));
		}

		JPanel line = new JPanel(new BorderLayout(6, 0));
		line.setOpaque(false);
		line.add(Ui.label(page.getName(), Theme.body(), page.isComplete() ? Theme.GOLD_BRIGHT : Theme.TEXT), BorderLayout.CENTER);
		line.add(Ui.label(page.getOwned() + "/" + page.getTotal(), Theme.small(), page.isComplete() ? Theme.GREEN : Theme.TEXT_MUTED),
			BorderLayout.EAST);
		card.add(line, BorderLayout.NORTH);

		ProgressBar bar = new ProgressBar(4);
		bar.setProgress(page.progress(), page.isComplete(), false);
		card.add(bar, BorderLayout.CENTER);

		JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		meta.setOpaque(false);
		if (page.getLuck() != null && !page.isComplete())
		{
			meta.add(new Pill(page.getLuck().verdict().getLabel(), Theme.luck(page.getLuck().verdict())));
		}
		if (page.getCount() >= 0)
		{
			meta.add(Ui.label(Format.count(page.getCount()) + " kc", Theme.small(), Theme.TEXT_MUTED));
		}
		if (!page.isComplete() && Double.isFinite(page.getHoursToNext()))
		{
			meta.add(Ui.label("next " + Format.about(page.getHoursToNext()), Theme.small(), Theme.TEXT_FAINT));
		}
		if (meta.getComponentCount() > 0)
		{
			((FlowLayout) meta.getLayout()).setHgap(5);
			card.add(meta, BorderLayout.SOUTH);
		}
		return card;
	}
}
