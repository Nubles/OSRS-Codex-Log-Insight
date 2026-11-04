package com.codex.loginsight.ui;

import com.codex.loginsight.core.Luck;
import com.codex.loginsight.core.RaidLog;
import com.codex.loginsight.core.RaidReport;
import com.codex.loginsight.core.Raids;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * A raid's log, shown on its collection log page: your purple chance each raid from the points and raid level the game
 * gave at its end, and how dry you are counted in expected purples rather than kill count. Read in the panel between
 * raids; nothing is drawn in the game view and nothing is read during a raid.
 */
final class RaidView
{
	/** Raids listed under the latest one. */
	static final int LISTED = 4;

	private RaidView()
	{
	}

	/** The raid whose log page this is, or null. */
	static Raids.Kind kindOf(String page)
	{
		return Raids.Kind.ofPage(page);
	}

	/** The raid log's cards, in the order they stand on the page. */
	static List<JComponent> cards(RaidReport report, ItemIcons icons, long now)
	{
		List<JComponent> out = new ArrayList<>();
		out.add(new SectionHeader("Raid log", report.isEmpty() ? "no raids yet" : raidsWord(report.getRuns())));
		out.add(dry(report, now));
		if (!report.isEmpty())
		{
			out.add(strip(report));
		}
		if (report.getLuck() != null)
		{
			out.add(luck(report));
		}
		RaidLog.Run latest = report.latest();
		if (latest != null)
		{
			out.add(new SectionHeader("Latest raid", latest.getKc() > 0 ? "kc " + Format.count(latest.getKc()) : null));
			out.add(latest(report.getKind(), latest, icons));
			List<RaidLog.Run> recent = report.getRecent();
			if (recent.size() > 1)
			{
				out.add(new SectionHeader("Before that"));
				for (int i = 1; i < Math.min(recent.size(), LISTED + 1); i++)
				{
					out.add(row(report.getKind(), recent.get(i), icons));
				}
			}
		}
		out.add(Rows.note(source(report.getKind()), 190));
		return out;
	}

	private static String raidsWord(int raids)
	{
		return Format.count(raids) + (raids == 1 ? " raid" : " raids");
	}

	/** The dry spell in expected purples: the headline of the page. */
	static Card dry(RaidReport report, long now)
	{
		Card card = new Card(new BorderLayout(0, 6));
		card.setBorder(new EmptyBorder(8, 9, 8, 9));
		card.highlighted(true);
		if (report.isEmpty())
		{
			card.add(Ui.wrapped("Finish a " + report.getKind().getTitle() + " raid and it is logged here: your points, "
				+ "the team, your purple chance from the wiki's formula, and whether a purple came to you. Dryness is "
				+ "then counted in expected purples rather than kill count.", Theme.small(), Theme.TEXT_MUTED, 186),
				BorderLayout.CENTER);
			return card;
		}
		double waiting = report.getDryWaiting();
		boolean counted = report.getDryRuns() > report.getDryUnknown();
		JPanel tiles = new JPanel(new GridLayout(1, 3, 5, 0));
		tiles.setOpaque(false);
		tiles.add(new StatTile(counted ? Format.slots(report.getDryExpected()) : "-", "Dry", "purples",
			counted ? Theme.dry(waiting) : Theme.TEXT_MUTED));
		tiles.add(new StatTile(Format.count(report.getDryRuns()), "Since", report.getDryRuns() == 1 ? "raid" : "raids",
			Theme.GOLD_BRIGHT));
		tiles.add(new StatTile(Format.count(report.getPurples()), "Purples", "logged",
			report.getPurples() > 0 ? Theme.VIOLET : Theme.TEXT_MUTED));
		card.add(tiles, BorderLayout.NORTH);
		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		if (counted)
		{
			body.add(new DryBar(waiting));
			body.add(javax.swing.Box.createVerticalStrut(5));
		}
		body.add(Ui.wrapped(drySentence(report, now), Theme.small(), Theme.TEXT, 186));
		card.add(body, BorderLayout.CENTER);
		if (counted && waiting < 0.2)
		{
			card.accent(Theme.dry(waiting));
		}
		return card;
	}

	/** "11 raids since your last purple, worth 1.4 purples on average: 75% of players would have one by now." */
	static String drySentence(RaidReport report, long now)
	{
		StringBuilder out = new StringBuilder();
		int runs = report.getDryRuns();
		boolean ever = report.getPurples() > 0;
		String spell = ever ? "since your last purple" : "logged, no purple yet";
		if (runs == 0)
		{
			out.append(ever ? "A purple on your latest raid" : "No raids counted yet").append('.');
		}
		else if (runs == report.getDryUnknown())
		{
			out.append(raidsWord(runs)).append(' ').append(spell).append("; none with a chance the log could read.");
		}
		else
		{
			out.append(raidsWord(runs)).append(' ').append(spell).append(", worth ")
				.append(Format.slots(report.getDryExpected())).append(report.getDryExpected() == 1 ? " purple" : " purples")
				.append(" on average: ").append(Format.have(report.getDryWaiting())).append(" of players would have one by now.");
			if (report.getDryUnknown() > 0)
			{
				out.append(' ').append(report.getDryUnknown()).append(report.getDryUnknown() == 1 ? " raid" : " raids")
					.append(report.getDryUnknown() == 1 ? " without a chance is" : " without a chance are").append(" left out.");
			}
		}
		if (ever && report.getLastPurple() != 0 && report.getLastTook() >= 0 && report.getLastPurpleAt() > 0)
		{
			out.append(" Your last came ").append(Format.ago(report.getLastPurpleAt(), now)).append(" after ")
				.append(Format.slots(report.getLastTook())).append(" expected purples.");
		}
		return out.toString();
	}

	/** Your luck over the recent raids with a known chance. */
	private static Card luck(RaidReport report)
	{
		Luck luck = report.getLuck();
		Card card = new Card(new BorderLayout(8, 2));
		card.setBorder(new EmptyBorder(7, 9, 7, 9));
		Color tone = Theme.luck(luck.verdict());
		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		head.add(Ui.label("LUCK", Theme.small(), Theme.TEXT_MUTED), BorderLayout.WEST);
		head.add(new Pill(luck.verdict().getLabel(), tone), BorderLayout.EAST);
		card.add(head, BorderLayout.NORTH);
		card.add(Ui.wrapped("Luckier than " + luck.luckierThan() + "% of players with the same " + luck.getSlots()
			+ " raids at the same points: " + luck.getOwned() + (luck.getOwned() == 1 ? " purple, " : " purples, ")
			+ Format.slots(luck.getExpected()) + " on average.", Theme.small(), Theme.TEXT, 186), BorderLayout.CENTER);
		return card;
	}

	/** The recent raids as columns of your chance, a violet gem where a purple came. */
	static Card strip(RaidReport report)
	{
		Card card = new Card(new BorderLayout(0, 4));
		card.setBorder(new EmptyBorder(7, 9, 7, 9));
		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		head.add(Ui.label("YOUR CHANCE EACH RAID", Theme.small(), Theme.TEXT_MUTED), BorderLayout.WEST);
		if (report.getAverage() > 0)
		{
			head.add(Ui.label("avg " + Format.chance(report.getAverage()), Theme.small(), Theme.GOLD), BorderLayout.EAST);
		}
		card.add(head, BorderLayout.NORTH);
		card.add(new ChanceStrip(report.getRecent()), BorderLayout.CENTER);
		return card;
	}

	/** The latest raid in full: team, points, raid level and the chance they made. */
	static Card latest(Raids.Kind kind, RaidLog.Run run, ItemIcons icons)
	{
		Card card = new Card(new BorderLayout(0, 5));
		card.setBorder(new EmptyBorder(8, 9, 8, 9));
		if (run.getPurple() != 0)
		{
			card.accent(Theme.VIOLET);
		}
		JPanel tiles = new JPanel(new GridLayout(1, 3, 5, 0));
		tiles.setOpaque(false);
		tiles.add(new StatTile(run.getChance() >= 0 ? Format.chance(run.getChance()) : "-", "Yours",
			run.isEstimate() ? "estimate" : "chance", run.getPurple() != 0 ? Theme.VIOLET : Theme.GOLD_BRIGHT));
		tiles.add(new StatTile(run.getTeamChance() >= 0 ? Format.chance(run.getTeamChance()) : "-", "Team", "chance",
			Theme.TEAL));
		tiles.add(new StatTile(run.getTeam() > 0 ? String.valueOf(run.getTeam()) : "-", "Size", run.getTeam() == 1 ? "solo" : "players",
			Theme.GOLD));
		card.add(tiles, BorderLayout.NORTH);
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		for (String line : facts(kind, run))
		{
			text.add(Ui.wrapped(line, Theme.small(), Theme.TEXT, 186));
		}
		String why = why(kind, run);
		if (why != null)
		{
			text.add(Ui.wrapped(why, Theme.small(), Theme.TEXT_FAINT, 186));
		}
		card.add(text, BorderLayout.CENTER);
		if (run.getPurple() != 0)
		{
			JPanel found = new JPanel(new BorderLayout(6, 0));
			found.setOpaque(false);
			found.add(new IconBadge(run.getPurple(), icons, true), BorderLayout.WEST);
			found.add(Ui.label("A purple came to you", Theme.small(), Theme.VIOLET), BorderLayout.CENTER);
			card.add(found, BorderLayout.SOUTH);
		}
		return card;
	}

	/** The latest raid's points and level, line by line. */
	static List<String> facts(Raids.Kind kind, RaidLog.Run run)
	{
		List<String> out = new ArrayList<>();
		String mode = Raids.NORMAL.equals(run.getMode()) ? null : run.getMode() + " Mode";
		if (kind == Raids.Kind.TOA && run.getLevel() >= 0)
		{
			out.add("Raid level " + run.getLevel() + (mode == null ? "" : " (" + mode + ")") + ": 1% per "
				+ Format.count(Math.round(Raids.toaPointsPerPercent(run.getLevel()))) + " points.");
		}
		else if (mode != null)
		{
			out.add(mode + ".");
		}
		if (run.getPersonal() >= 0)
		{
			StringBuilder points = new StringBuilder("Your points: ").append(Format.count(run.getPersonal()));
			if (run.getTotal() > 0 && run.getTotal() != run.getPersonal())
			{
				points.append(" of ").append(Format.count(run.getTotal())).append(" (")
					.append(Format.percent(run.getPersonal() / (double) run.getTotal())).append(')');
			}
			if (kind == Raids.Kind.TOA)
			{
				// The value the game sends leaves out the starting 5,000, as the chance does (see Raids.toa).
				points.append(" (without the starting ").append(Format.count(Raids.TOA_START_POINTS)).append(')');
			}
			out.add(points.append('.').toString());
		}
		else if (kind != Raids.Kind.TOB)
		{
			out.add("No points were read for this raid.");
		}
		return out;
	}

	/** Why a chance is an estimate or unknown, for the line under the latest raid. */
	static String why(Raids.Kind kind, RaidLog.Run run)
	{
		if (run.getChance() < 0)
		{
			if (kind == Raids.Kind.TOB && Raids.ENTRY.equals(run.getMode()))
			{
				return "The wiki gives no Entry Mode purple chance, so this raid is not counted.";
			}
			return "Its chance could not be worked out, so it is not counted in the dryness.";
		}
		if (kind == Raids.Kind.TOB)
		{
			return run.getTeam() > 1 ? "Estimate: the wiki weights the purple by deaths and damage with no formula, so "
				+ "an even share is assumed. Deaths are not counted." : "Deaths would lower this; they are not counted.";
		}
		if (kind == Raids.Kind.COX && run.isEstimate())
		{
			return "Estimate: the wiki says more points make you likelier but gives no formula, so your share of the "
				+ "points is used.";
		}
		if (kind == Raids.Kind.TOA && run.isEstimate())
		{
			return "Your share is proportional to your points (wiki); the team's total is not sent, so this assumes "
				+ "the team stayed under the 55% cap.";
		}
		return null;
	}

	/** A raid before the latest, on one line. */
	static Card row(Raids.Kind kind, RaidLog.Run run, ItemIcons icons)
	{
		StringBuilder detail = new StringBuilder();
		detail.append(run.getTeam() == 1 ? "Solo" : run.getTeam() > 1 ? run.getTeam() + " players" : "Team unknown");
		if (kind == Raids.Kind.TOA && run.getLevel() >= 0)
		{
			detail.append("  ·  level ").append(run.getLevel());
		}
		else if (!Raids.NORMAL.equals(run.getMode()))
		{
			detail.append("  ·  ").append(run.getMode());
		}
		if (run.getPersonal() >= 0)
		{
			detail.append("  ·  ").append(shortPoints(run.getPersonal())).append(" pts");
		}
		String extra = run.getChance() >= 0 ? Format.chance(run.getChance()) + " for you" + (run.isEstimate() ? " (est.)" : "")
			: "chance unknown";
		JComponent tag = run.getPurple() != 0 ? new Pill("Purple", Theme.VIOLET) : null;
		JComponent lead = run.getPurple() != 0 ? new IconBadge(run.getPurple(), icons, true) : null;
		String title = run.getKc() > 0 ? "KC " + Format.count(run.getKc()) : "Raid";
		return Rows.line(lead, title, detail.toString(), extra, run.getPurple() != 0 ? Theme.VIOLET : Theme.TEXT_FAINT,
			tag, run.getPurple() != 0 ? Theme.VIOLET : null);
	}

	/** "31.2k", "870", "1.2m". */
	static String shortPoints(long points)
	{
		if (points < 1000)
		{
			return String.valueOf(points);
		}
		if (points < 1_000_000)
		{
			double k = points / 1000.0;
			return (k < 100 ? String.format(Locale.ENGLISH, "%.1f", k).replace(".0", "") : String.valueOf(Math.round(k))) + "k";
		}
		return String.format(Locale.ENGLISH, "%.1f", points / 1_000_000.0).replace(".0", "") + "m";
	}

	/** Where the chance comes from, under the log. */
	static String source(Raids.Kind kind)
	{
		switch (kind)
		{
			case COX:
				return "Chance from the OSRS Wiki: 1% per 8,676 team points, each roll capped at 65.7% (570,000 points), "
					+ "further points rolling again. Points are the game's own, read as the raid ends.";
			case TOB:
				return "Chance from the OSRS Wiki: 1/9.1 for the team without deaths, 1/7.7 in Hard Mode, the same for "
					+ "every team size. Entry Mode has no stated chance.";
			default:
				return "Chance from the OSRS Wiki: 1% per 10,500 − 20 × raid level points (the level scaled past 310 and "
					+ "430), capped at 55%, shared by points. Your points are the game's own, sent as the raid ends.";
		}
	}

	/**
	 * The latest raids as columns, oldest on the left: the height is your chance, gold for a raid you counted, a violet
	 * gem on top where a purple came, and a faint stub where the chance was unknown.
	 */
	static final class ChanceStrip extends JComponent
	{
		static final int SHOWN = 30;
		private final List<RaidLog.Run> runs;

		ChanceStrip(List<RaidLog.Run> runs)
		{
			this.runs = runs;
			setPreferredSize(new Dimension(10, 54));
			setOpaque(false);
			setToolTipText("Your latest raids, oldest on the left: the height is your purple chance, violet where one came.");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			int h = getHeight();
			g.setFont(Theme.small());
			FontMetrics fm = g.getFontMetrics();
			int labelH = 10;
			int top = 7;
			int bottom = h - labelH;
			int count = Math.min(SHOWN, runs.size());
			double max = 0;
			for (int i = 0; i < count; i++)
			{
				max = Math.max(max, runs.get(i).getChance());
			}
			// The scale is rounded up to a tidy chance so a strip of small chances still fills the height.
			double scale = max <= 0 ? 0.1 : niceCeiling(max);
			String scaleLabel = Format.percent(scale);
			int left = fm.stringWidth(scaleLabel) + 4;
			g.setColor(Theme.alpha(Theme.STROKE, 160));
			for (int x = left; x < w; x += 4)
			{
				g.drawLine(x, top, x + 1, top);
			}
			g.setColor(Theme.STROKE_SOFT);
			g.drawLine(left, bottom, w, bottom);
			double step = (w - left) / (double) SHOWN;
			int barW = Math.max(2, (int) Math.floor(step) - 2);
			double x0 = w - count * step;
			for (int k = 0; k < count; k++)
			{
				RaidLog.Run run = runs.get(count - 1 - k);
				int x = (int) Math.round(x0 + k * step);
				boolean purple = run.getPurple() != 0;
				if (run.getChance() < 0)
				{
					g.setColor(Theme.alpha(Theme.TEXT_FAINT, 120));
					g.fill(new RoundRectangle2D.Float(x, bottom - 3, barW, 3, 2, 2));
				}
				else
				{
					int barH = Math.max(2, (int) Math.round((bottom - top) * Math.min(1, run.getChance() / scale)));
					Color c = purple ? Theme.VIOLET : Theme.GOLD;
					g.setPaint(new GradientPaint(0, bottom - barH, c, 0, bottom, Theme.alpha(c, 100)));
					g.fill(new RoundRectangle2D.Float(x, bottom - barH, barW, barH, 2, 2));
				}
				if (purple)
				{
					int barH = run.getChance() < 0 ? 3
						: Math.max(2, (int) Math.round((bottom - top) * Math.min(1, run.getChance() / scale)));
					float d = Math.max(4, barW + 1);
					float cx = x + barW / 2f;
					float cy = Math.max(d / 2, bottom - barH - d / 2 - 1);
					g.setColor(Theme.alpha(Theme.VIOLET, 70));
					g.fill(new Ellipse2D.Float(cx - d, cy - d, d * 2, d * 2));
					g.setColor(Theme.VIOLET);
					g.fill(new Ellipse2D.Float(cx - d / 2, cy - d / 2, d, d));
					g.setColor(Theme.alpha(Theme.TEXT, 200));
					g.setStroke(new BasicStroke(1f));
					g.draw(new Ellipse2D.Float(cx - d / 2, cy - d / 2, d, d));
				}
			}
			g.setColor(Theme.TEXT_FAINT);
			g.drawString(scaleLabel, 0, top + fm.getAscent() / 2);
			g.drawString("older", left, h - 1);
			String latest = "latest";
			g.drawString(latest, w - fm.stringWidth(latest), h - 1);
			g.dispose();
		}
	}

	/** 0.137 to 0.15, 0.04 to 0.05, 0.55 to 0.6: the next tidy step above. */
	static double niceCeiling(double value)
	{
		double[] steps = {0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.08, 0.1, 0.12, 0.15, 0.2, 0.25, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 1};
		for (double s : steps)
		{
			if (value <= s + 1e-9)
			{
				return s;
			}
		}
		return 1;
	}
}
