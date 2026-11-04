package com.codex.loginsight.ui;

import com.codex.loginsight.core.Delve;
import com.codex.loginsight.core.DelveLog;
import com.codex.loginsight.core.DelveReport;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The Doom of Mokhaiotl's delve log, shown on its log page: every run you banked or lost, each unique's chance from the
 * levels you actually cleared, and whether cashing out one level deeper brings the next slot sooner at your own pace.
 * Read in the panel between runs; nothing is drawn in the game view.
 */
final class DelveView
{
	/** The log page and monster whose slots are the Doom's. */
	static final String PAGE = "Doom of Mokhaiotl";

	private DelveView()
	{
	}

	/** True for the Doom's own log page. */
	static boolean isDelvePage(String page)
	{
		return page != null && page.toLowerCase(Locale.ENGLISH).contains("mokhaiotl");
	}

	/** The delve log's cards, in the order they stand on the page. */
	static List<JComponent> cards(DelveReport report, ItemIcons icons)
	{
		List<JComponent> out = new ArrayList<>();
		out.add(new SectionHeader("Delve log", report.isEmpty() ? "no runs yet" : runsWord(report.getBankedRuns() + report.getDiedRuns())));
		out.add(runs(report));
		out.add(new SectionHeader("Uniques", report.getBankedLevels() > 0 ? "by your levels" : "per run to delve 9"));
		if (report.getLuck() != null)
		{
			out.add(luck(report));
		}
		for (DelveReport.Unique unique : report.getUniques())
		{
			out.add(unique(unique, report, icons));
		}
		out.add(new SectionHeader("Push or cash out", report.isOwnPace() ? "your pace" : "typical pace"));
		out.add(depths(report));
		out.add(Rows.note("Rates from the OSRS Wiki, per delve level; every level from 9 rolls at the delve 9 rate. "
			+ "Loot from a run the Doom ends is lost, so only levels from runs you kept count.", 190));
		return out;
	}

	private static String runsWord(int runs)
	{
		return Format.count(runs) + (runs == 1 ? " run" : " runs");
	}

	/** Runs at a glance: banked and lost, the deepest, the depth you usually cash out at, and a strip of recent runs. */
	static Card runs(DelveReport report)
	{
		Card card = new Card(new BorderLayout(0, 6));
		card.setBorder(new EmptyBorder(8, 9, 8, 9));
		card.highlighted(true);
		if (report.isEmpty())
		{
			card.add(Ui.wrapped("Delve the Doom of Mokhaiotl and every run is logged here: how deep it went and whether "
				+ "you kept the loot. Until then, the odds below are for a run to each depth.", Theme.small(), Theme.TEXT_MUTED, 186),
				BorderLayout.CENTER);
			return card;
		}
		JPanel tiles = new JPanel(new GridLayout(1, 3, 5, 0));
		tiles.setOpaque(false);
		tiles.add(new StatTile(String.valueOf(report.getDeepest()), "Deepest", "delve", Theme.GOLD_BRIGHT));
		tiles.add(new StatTile(report.getUsual() > 0 ? String.valueOf(report.getUsual()) : "-", "Cash out",
			report.getUsual() > 0 ? "usually" : "none kept", Theme.TEAL));
		tiles.add(new StatTile(Format.count(report.getDiedRuns()), "Lost", report.getDiedRuns() == 1 ? "run" : "runs",
			report.getDiedRuns() > 0 ? Theme.EMBER : Theme.TEXT_MUTED));
		card.add(tiles, BorderLayout.NORTH);
		card.add(new RunStrip(report.getRecent(), report.getOpenDepth()), BorderLayout.CENTER);
		StringBuilder line = new StringBuilder(Format.count(report.getBankedRuns()) + " kept, "
			+ Format.count(report.getBankedLevels()) + " levels banked");
		if (report.getOpenDepth() > 0)
		{
			line.append(". On delve ").append(report.getOpenDepth() + 1).append(" now");
		}
		line.append('.');
		card.add(Ui.wrapped(line.toString(), Theme.small(), Theme.TEXT_MUTED, 186), BorderLayout.SOUTH);
		return card;
	}

	/** Your luck on the uniques the delve log has seen you chase from the start. */
	private static Card luck(DelveReport report)
	{
		Card card = new Card(new BorderLayout(8, 2));
		card.setBorder(new EmptyBorder(7, 9, 7, 9));
		Color tone = Theme.luck(report.getLuck().verdict());
		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		head.add(Ui.label("LUCK", Theme.small(), Theme.TEXT_MUTED), BorderLayout.WEST);
		head.add(new Pill(report.getLuck().verdict().getLabel(), tone), BorderLayout.EAST);
		card.add(head, BorderLayout.NORTH);
		card.add(Ui.wrapped("Luckier than " + report.getLuck().luckierThan() + "% of players who banked the same levels: "
			+ report.getLuck().getOwned() + " found, " + Format.slots(report.getLuck().getExpected()) + " on average.",
			Theme.small(), Theme.TEXT, 186), BorderLayout.CENTER);
		return card;
	}

	/** One unique: its find and how lucky it was, or how dry you are on it from the levels you banked. */
	static Card unique(DelveReport.Unique unique, DelveReport report, ItemIcons icons)
	{
		String detail = "From delve " + unique.getFirstLevel() + "  ·  1/" + Format.count(unique.getBestOneIn()) + " at 9+";
		String extra;
		Color extraColor = Theme.TEXT_FAINT;
		JComponent tag;
		Color accent = null;
		DelveLog.Find find = unique.getFind();
		if (find != null && find.getDepth() > 0)
		{
			double luck = find.luckierThan();
			Color tone = Theme.luck(luck);
			detail = "Delve " + find.getDepth() + " run  ·  " + Format.chance(find.getChance()) + " that run";
			extra = "Luckier than " + Math.round(luck * 100) + "% of players";
			extraColor = tone;
			tag = new Pill("Found", Theme.GREEN);
			accent = tone;
		}
		else if (unique.isOwned())
		{
			extra = find != null ? "Found away from a logged run" : "Owned before logging";
			tag = new Pill("Owned", Theme.GREEN);
		}
		else if (report.getBankedLevels() == 0)
		{
			extra = Format.chance(Delve.runChance(Delve.index(unique.getItemId()), 9)) + " a run to delve 9";
			tag = null;
		}
		else
		{
			double waiting = 1 - unique.getByNow();
			extra = "Average by now: " + Format.slots(unique.getExpected());
			tag = report.isOwnedKnown() ? new Pill(Format.dry(waiting), Theme.dry(waiting)) : null;
			if (report.isOwnedKnown() && waiting < 0.2)
			{
				accent = Theme.dry(waiting);
			}
		}
		Card card = Rows.line(new IconBadge(unique.getItemId(), icons, unique.isOwned()), unique.getName(), detail, extra,
			extraColor, tag, accent);
		card.setToolTipText(tooltip(unique, report));
		return card;
	}

	static String tooltip(DelveReport.Unique unique, DelveReport report)
	{
		int u = Delve.index(unique.getItemId());
		StringBuilder tip = new StringBuilder("<html>").append(Ui.escape(unique.getName())).append("<br>");
		tip.append("Per level: ");
		boolean first = true;
		for (int level = unique.getFirstLevel(); level <= Delve.DEEPEST_ROW; level++)
		{
			tip.append(first ? "" : ", ").append(level == Delve.DEEPEST_ROW ? "9+" : String.valueOf(level)).append(" 1/")
				.append(Format.count(Delve.oneIn(u, level)));
			first = false;
		}
		tip.append("<br>A run to delve 8: ").append(Format.chance(Delve.runChance(u, 8)))
			.append("; to delve 9: ").append(Format.chance(Delve.runChance(u, 9)));
		if (report.getBankedLevels() > 0)
		{
			tip.append("<br>From your ").append(Format.count(report.getBankedLevels())).append(" banked levels: ")
				.append(Format.slots(unique.getExpected())).append(" expected, ").append(Format.percent(unique.getByNow()))
				.append(" of players would have one");
		}
		DelveLog.Find find = unique.getFind();
		if (find != null && find.getDepth() > 0)
		{
			tip.append("<br>").append(Format.percent(find.sooner())).append(" of players would have had it before that run");
		}
		return tip.append("</html>").toString();
	}

	/** The comparison: hours to the next slot if you cash out at each depth, and what one level deeper changes. */
	static Card depths(DelveReport report)
	{
		Card card = new Card(new BorderLayout(0, 6));
		card.setBorder(new EmptyBorder(8, 9, 8, 9));
		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		head.add(Ui.label(report.isForNewSlots() ? "HOURS TO NEXT NEW SLOT" : "HOURS TO NEXT UNIQUE", Theme.small(), Theme.TEXT_MUTED),
			BorderLayout.WEST);
		card.add(head, BorderLayout.NORTH);
		DepthChart chart = new DepthChart(report);
		card.add(chart, BorderLayout.CENTER);
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.add(Ui.wrapped(verdict(report), Theme.small(), Theme.TEXT, 186));
		text.add(Ui.wrapped(basis(report), Theme.small(), Theme.TEXT_FAINT, 186));
		card.add(text, BorderLayout.SOUTH);
		return card;
	}

	/** The depth the comparison starts from: the one you usually cash out at, else delve 8. */
	static int from(DelveReport report)
	{
		int usual = report.getUsual();
		return usual >= 2 ? usual : 8;
	}

	/** Whether one level deeper than your usual cash out brings the next slot sooner, in a sentence or two. */
	static String verdict(DelveReport report)
	{
		int at = from(report);
		DelveReport.Depth here = report.depth(at);
		DelveReport.Depth deeper = report.depth(at + 1);
		String what = report.isForNewSlots() ? "new slot" : "unique";
		if (here == null)
		{
			return "";
		}
		StringBuilder out = new StringBuilder();
		String start = report.getUsual() >= 2 ? "You usually cash out at delve " + at + ": " : "Cashing out at delve " + at + ": ";
		out.append(start).append("next ").append(what).append(" in ").append(Format.about(here.getHoursToNext())).append(". ");
		if (deeper != null)
		{
			double gain = here.getHoursToNext() - deeper.getHoursToNext();
			if (gain > 0)
			{
				out.append("One deeper pays: ").append(Format.about(deeper.getHoursToNext())).append(" at delve ")
					.append(at + 1).append('.');
			}
			else
			{
				out.append("Going on to ").append(at + 1).append(" costs more than it adds (")
					.append(Format.about(deeper.getHoursToNext())).append(").");
			}
		}
		int best = report.getBest();
		if (best > 0 && best != at && best != at + 1)
		{
			out.append(" Quickest: delve ").append(best).append(", ").append(Format.about(report.depth(best).getHoursToNext()))
				.append('.');
		}
		return out.toString();
	}

	/** Whose pace and whose deaths the comparison uses. */
	static String basis(DelveReport report)
	{
		StringBuilder out = new StringBuilder();
		out.append(report.isOwnPace() ? "Your own level times" : "A typical 6 runs of delve 1 to 8 an hour (wiki)");
		if (report.getSurvival() < 0)
		{
			out.append("; no fights logged, so no deaths are counted.");
		}
		else if (report.getDeaths() == 0)
		{
			out.append("; no deaths in ").append(Format.count(report.getCleared())).append(" levels.");
		}
		else
		{
			out.append("; your ").append(Format.count(report.getDeaths())).append(report.getDeaths() == 1 ? " death" : " deaths")
				.append(" in ").append(Format.count(report.getCleared() + report.getDeaths())).append(" fights.");
		}
		int at = from(report) + 1;
		DelveReport.Depth deeper = report.depth(at);
		if (deeper != null && !deeper.isTried() && report.getSurvival() >= 0)
		{
			out.append(" You have not cleared ").append(at).append(" yet: it is read at your usual survival.");
		}
		return out.toString();
	}

	/** The most recent runs as small columns: height is depth, gold kept, blood lost, teal for the run under way. */
	static final class RunStrip extends JComponent
	{
		private static final int SHOWN = 30;
		private final List<DelveLog.Run> runs;
		private final int open;

		RunStrip(List<DelveLog.Run> runs, int open)
		{
			this.runs = runs;
			this.open = open;
			setPreferredSize(new Dimension(10, 40));
			setOpaque(false);
			setToolTipText("Your latest runs, oldest on the left: the height is the delve reached; gold kept, red lost to the Doom.");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			int h = getHeight();
			int labelH = 10;
			int top = 2;
			int bottom = h - labelH;
			g.setFont(Theme.small());
			FontMetrics fm = g.getFontMetrics();
			// Room on the left for the delve 8 guide's label.
			int left = fm.stringWidth("8") + 3;
			List<int[]> bars = new ArrayList<>();
			int count = Math.min(SHOWN, runs.size());
			for (int i = count - 1; i >= 0; i--)
			{
				DelveLog.Run run = runs.get(i);
				bars.add(new int[]{run.getDeepest(), run.banked() ? 0 : 1});
			}
			if (open > 0)
			{
				bars.add(new int[]{open, 2});
			}
			int max = 9;
			for (int[] bar : bars)
			{
				max = Math.max(max, bar[0] + (bar[1] == 1 ? 1 : 0));
			}
			// Guides at delve 8 and the deepest shown.
			g.setColor(Theme.alpha(Theme.STROKE, 160));
			int eight = bottom - (int) Math.round((bottom - top) * 8.0 / max);
			for (int x = left; x < w; x += 4)
			{
				g.drawLine(x, eight, x + 1, eight);
			}
			g.setColor(Theme.STROKE_SOFT);
			g.drawLine(left, bottom, w, bottom);
			int slots = Math.max(SHOWN, bars.size());
			double step = (w - left) / (double) slots;
			int barW = Math.max(2, (int) Math.floor(step) - 2);
			double x0 = w - bars.size() * step;
			for (int i = 0; i < bars.size(); i++)
			{
				int[] bar = bars.get(i);
				// A death shows the level it ended on, the one after the deepest cleared.
				int depth = bar[1] == 1 ? bar[0] + 1 : bar[0];
				int barH = Math.max(2, (int) Math.round((bottom - top) * depth / (double) max));
				int x = (int) Math.round(x0 + i * step);
				Color c = bar[1] == 0 ? Theme.GOLD : bar[1] == 1 ? Theme.BLOOD : Theme.TEAL;
				g.setPaint(new GradientPaint(0, bottom - barH, c, 0, bottom, Theme.alpha(c, 110)));
				g.fill(new RoundRectangle2D.Float(x, bottom - barH, barW, barH, 2, 2));
				if (bar[1] == 1)
				{
					// A small cross on top of a lost run.
					g.setColor(Theme.TEXT);
					int cx = x + barW / 2;
					int cy = bottom - barH - 3;
					g.drawLine(cx - 1, cy - 1, cx + 1, cy + 1);
					g.drawLine(cx - 1, cy + 1, cx + 1, cy - 1);
				}
			}
			g.setColor(Theme.TEXT_FAINT);
			g.drawString("8", 0, Math.min(bottom, eight + fm.getAscent() / 2));
			String label = "older";
			g.drawString(label, left, h - 1);
			String now = open > 0 ? "now" : "latest";
			g.drawString(now, w - fm.stringWidth(now), h - 1);
			g.dispose();
		}
	}

	/**
	 * The wait for the next slot against the depth you cash out at: a column per depth, the shortest wait tallest, the
	 * quickest depth in teal and your usual one ringed in gold. Depths you have not cleared are drawn hollow.
	 */
	static final class DepthChart extends JComponent
	{
		private final DelveReport report;

		DepthChart(DelveReport report)
		{
			this.report = report;
			setPreferredSize(new Dimension(10, 92));
			setOpaque(false);
			StringBuilder tip = new StringBuilder("<html>Cash out after delve...<br>");
			for (DelveReport.Depth d : report.getDepths())
			{
				tip.append(d.getDepth()).append(": ").append(Format.about(d.getHoursToNext())).append(" to the next slot, ")
					.append(Format.chance(d.getPerRun())).append(" a kept run, ").append(Format.percent(d.getReach()))
					.append(" of runs kept<br>");
			}
			setToolTipText(tip.append("</html>").toString());
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			List<DelveReport.Depth> depths = report.getDepths();
			int w = getWidth();
			int h = getHeight();
			g.setFont(Theme.small());
			FontMetrics fm = g.getFontMetrics();
			int labelH = fm.getHeight();
			int top = labelH + 2;
			int bottom = h - labelH - 1;
			double most = 0;
			for (DelveReport.Depth d : depths)
			{
				if (Double.isFinite(d.getHoursToNext()) && d.getHoursToNext() > 0)
				{
					most = Math.max(most, 1 / d.getHoursToNext());
				}
			}
			int n = depths.size();
			double step = w / (double) Math.max(1, n);
			int barW = Math.max(4, (int) Math.floor(step) - 3);
			int usual = from(report);
			List<int[]> labels = new ArrayList<>();
			List<String> values = new ArrayList<>();
			for (int i = 0; i < n; i++)
			{
				DelveReport.Depth d = depths.get(i);
				double rate = Double.isFinite(d.getHoursToNext()) && d.getHoursToNext() > 0 ? 1 / d.getHoursToNext() : 0;
				int barH = most <= 0 ? 0 : Math.max(2, (int) Math.round((bottom - top) * rate / most));
				int x = (int) Math.round(i * step + (step - barW) / 2);
				boolean best = d.getDepth() == report.getBest();
				boolean mine = d.getDepth() == usual;
				Color c = best ? Theme.TEAL : mine ? Theme.GOLD_BRIGHT : Theme.GOLD_DIM;
				RoundRectangle2D shape = new RoundRectangle2D.Float(x, bottom - barH, barW, barH, 3, 3);
				if (d.isTried() || report.isEmpty() || report.getSurvival() < 0)
				{
					g.setPaint(new GradientPaint(0, bottom - barH, c, 0, bottom, Theme.alpha(c, 90)));
					g.fill(shape);
				}
				else
				{
					g.setColor(Theme.alpha(c, 60));
					g.fill(shape);
					g.setColor(c);
					g.draw(shape);
				}
				if (mine)
				{
					// Your usual depth: a gold chevron above it.
					int cx = x + barW / 2;
					int cy = bottom - barH - 3;
					Path2D tri = new Path2D.Float();
					tri.moveTo(cx - 3, cy - 3);
					tri.lineTo(cx + 3, cy - 3);
					tri.lineTo(cx, cy);
					tri.closePath();
					g.setColor(Theme.GOLD_BRIGHT);
					g.fill(tri);
				}
				if (best || mine)
				{
					String value = shortHours(d.getHoursToNext());
					int tw = fm.stringWidth(value);
					int tx = Math.max(0, Math.min(w - tw, x + barW / 2 - tw / 2));
					int ty = Math.max(fm.getAscent(), bottom - barH - (mine ? 7 : 3));
					labels.add(new int[]{tx, ty, tw, best ? 1 : 0, i});
					values.add(value);
				}
				String label = String.valueOf(d.getDepth());
				g.setColor(best ? Theme.TEAL : mine ? Theme.GOLD_BRIGHT : Theme.TEXT_MUTED);
				g.drawString(label, x + barW / 2 - fm.stringWidth(label) / 2, h - 2);
			}
			g.setColor(Theme.STROKE_SOFT);
			g.drawLine(0, bottom + 1, w, bottom + 1);
			if (labels.size() == 2)
			{
				// Your depth and the quickest side by side: the left label steps aside so both stay readable.
				int[] a = labels.get(0);
				int[] b = labels.get(1);
				if (a[0] + a[2] + 3 > b[0] && Math.abs(a[1] - b[1]) < fm.getHeight())
				{
					a[0] = Math.max(0, b[0] - a[2] - 3);
					if (a[0] + a[2] + 3 > b[0])
					{
						a[1] = Math.max(fm.getAscent(), Math.min(a[1], b[1]) - fm.getHeight());
					}
				}
			}
			for (int k = 0; k < labels.size(); k++)
			{
				int[] l = labels.get(k);
				g.setColor(l[3] == 1 ? Theme.TEAL : Theme.GOLD_BRIGHT);
				g.drawString(values.get(k), l[0], l[1]);
			}
			g.dispose();
		}
	}

	/** "6.9h", "14h" or "45m": short enough to sit over one column. */
	static String shortHours(double hours)
	{
		if (!Double.isFinite(hours) || hours < 0)
		{
			return "-";
		}
		if (hours < 1)
		{
			return Math.max(1, Math.round(hours * 60)) + "m";
		}
		if (hours < 9.95)
		{
			return String.format(Locale.ENGLISH, "%.1fh", hours);
		}
		return Format.count(Math.round(hours)) + "h";
	}
}
