package com.codex.loginsight.ui;

import com.codex.loginsight.core.Contribution;
import com.codex.loginsight.core.ContributionLog;
import com.codex.loginsight.core.ContributionReport;
import com.codex.loginsight.core.Luck;
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
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * A shared boss's contribution log, shown on its collection log page: your share of each kill, the unique chance it
 * gave you from the wiki's rule, and the odds added up per unique against what you got. Only finished kills are shown;
 * nothing is drawn in the game view and nothing is shown while a fight is under way.
 */
final class ContributionView
{
	/** Kills listed under the latest one. */
	static final int LISTED = 4;

	private ContributionView()
	{
	}

	static Contribution.Boss bossOf(String page)
	{
		return Contribution.Boss.ofPage(page);
	}

	static List<JComponent> cards(ContributionReport report, ItemIcons icons, long now)
	{
		List<JComponent> out = new ArrayList<>();
		out.add(new SectionHeader("Contribution log", report.isEmpty() ? "no kills yet" : killsWord(report.getKills())));
		out.add(dry(report, now));
		if (!report.isEmpty())
		{
			out.add(strip(report));
			out.add(averages(report));
		}
		if (report.getLuck() != null)
		{
			out.add(luck(report));
		}
		if (!report.isEmpty())
		{
			out.add(new SectionHeader("Each unique", "got / expected"));
			for (ContributionReport.Odds odds : report.getOdds())
			{
				out.add(unique(odds, icons));
			}
		}
		ContributionLog.Kill latest = report.latest();
		if (latest != null)
		{
			out.add(new SectionHeader("Latest kill", latest.getKc() > 0 ? "kc " + Format.count(latest.getKc()) : null));
			out.add(latest(report.getBoss(), latest, icons));
			List<ContributionLog.Kill> recent = report.getRecent();
			if (recent.size() > 1)
			{
				out.add(new SectionHeader("Before that"));
				for (int i = 1; i < Math.min(recent.size(), LISTED + 1); i++)
				{
					out.add(row(recent.get(i), icons));
				}
			}
		}
		out.add(Rows.note(source(report.getBoss()), 190));
		return out;
	}

	static String killsWord(int kills)
	{
		return Format.count(kills) + (kills == 1 ? " kill" : " kills");
	}

	/** The dry spell in expected uniques: the headline. */
	static Card dry(ContributionReport report, long now)
	{
		Card card = new Card(new BorderLayout(0, 6));
		card.setBorder(new EmptyBorder(8, 9, 8, 9));
		card.highlighted(true);
		if (report.isEmpty())
		{
			card.add(Ui.wrapped("Kill " + report.getBoss().getTitle() + " and the kill is logged here once it ends: "
				+ "your share of the damage, the unique chance it gave you by the wiki's rule, and your odds so far "
				+ "for each unique against what you got. Nothing is shown during a fight.",
				Theme.small(), Theme.TEXT_MUTED, 186), BorderLayout.CENTER);
			return card;
		}
		double waiting = report.getDryWaiting();
		JPanel tiles = new JPanel(new GridLayout(1, 3, 5, 0));
		tiles.setOpaque(false);
		tiles.add(new StatTile(Format.slots(report.getDryExpected()), "Dry", "uniques", Theme.dry(waiting)));
		tiles.add(new StatTile(Format.count(report.getDryKills()), "Since", report.getDryKills() == 1 ? "kill" : "kills",
			Theme.GOLD_BRIGHT));
		tiles.add(new StatTile(Format.count(report.getUniques()), "Uniques", "logged",
			report.getUniques() > 0 ? Theme.VIOLET : Theme.TEXT_MUTED));
		card.add(tiles, BorderLayout.NORTH);
		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.add(new DryBar(waiting));
		body.add(javax.swing.Box.createVerticalStrut(5));
		body.add(Ui.wrapped(drySentence(report, now), Theme.small(), Theme.TEXT, 186));
		card.add(body, BorderLayout.CENTER);
		if (waiting < 0.2)
		{
			card.accent(Theme.dry(waiting));
		}
		return card;
	}

	/** "23 kills since your last unique, worth 0.31 uniques on average: 27% of players would have one by now." */
	static String drySentence(ContributionReport report, long now)
	{
		StringBuilder out = new StringBuilder();
		int kills = report.getDryKills();
		boolean ever = report.getUniques() > 0;
		if (kills == 0)
		{
			out.append(ever ? "A unique on your latest kill." : "No kills logged yet.");
		}
		else
		{
			double e = report.getDryExpected();
			out.append(killsWord(kills)).append(' ').append(ever ? "since your last unique" : "logged, no unique yet")
				.append(", worth ").append(Format.slots(e)).append("1".equals(Format.slots(e)) ? " unique" : " uniques")
				.append(" for you on average: ").append(Format.have(report.getDryWaiting()))
				.append(" of players would have one by now.");
		}
		if (ever && report.getLastTook() >= 0 && report.getLastUniqueAt() > 0)
		{
			out.append(" Your last came ").append(Format.ago(report.getLastUniqueAt(), now)).append(" after ")
				.append(Format.slots(report.getLastTook())).append(" expected uniques.");
		}
		return out.toString();
	}

	/** Averages over your kills. */
	static Card averages(ContributionReport report)
	{
		Card card = new Card(new BorderLayout(0, 5));
		card.setBorder(new EmptyBorder(7, 9, 7, 9));
		JPanel tiles = new JPanel(new GridLayout(1, 3, 5, 0));
		tiles.setOpaque(false);
		tiles.add(new StatTile(report.getAverageShare() >= 0 ? Format.percent(report.getAverageShare()) : "-", "Share",
			"average", Theme.GOLD_BRIGHT));
		tiles.add(new StatTile(Format.chance(report.getAverageChance()), "Unique", "a kill", Theme.TEAL));
		tiles.add(new StatTile(Format.count(report.getCredited()), "Credit", "of " + Format.count(report.getKills()),
			report.getCredited() < report.getKills() ? Theme.EMBER : Theme.GOLD));
		card.add(tiles, BorderLayout.NORTH);
		card.add(Ui.wrapped(averageSentence(report), Theme.small(), Theme.TEXT_MUTED, 186), BorderLayout.CENTER);
		return card;
	}

	/** "Your kills add up to 0.42 uniques on average; you have 1." */
	static String averageSentence(ContributionReport report)
	{
		double e = report.getExpected();
		StringBuilder out = new StringBuilder("Your kills add up to ").append(Format.slots(e))
			.append("1".equals(Format.slots(e)) ? " unique" : " uniques").append(" on average; you have ")
			.append(report.getUniques()).append('.');
		int lost = report.getKills() - report.getCredited();
		if (lost > 0)
		{
			out.append(' ').append(lost).append(lost == 1 ? " kill" : " kills").append(' ')
				.append(report.getBoss() == Contribution.Boss.CORP ? "went to a player who dealt more."
					: "gave no kill count line, so no drop.");
		}
		return out.toString();
	}

	private static Card luck(ContributionReport report)
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
			+ " kills at the same shares: " + luck.getOwned() + (luck.getOwned() == 1 ? " kill" : " kills")
			+ " with a unique, " + Format.slots(luck.getExpected()) + " on average.", Theme.small(), Theme.TEXT, 186),
			BorderLayout.CENTER);
		return card;
	}

	/** One unique: got against expected, and how dry or lucky that is. */
	static Card unique(ContributionReport.Odds odds, ItemIcons icons)
	{
		boolean got = odds.getGot() > 0;
		String detail = odds.getGot() + " got  ·  " + Format.slots(odds.getExpected()) + " expected";
		String verdict = verdict(odds);
		Color tone = got ? (odds.getRatio() >= 1 ? Theme.GREEN : Theme.VIOLET) : Theme.dry(odds.getWaiting());
		JComponent tag = got ? new Pill(odds.getGot() == 1 ? "Got" : odds.getGot() + "x", Theme.VIOLET) : null;
		return Rows.line(new IconBadge(odds.getUnique().getIcon(), icons, got), odds.getUnique().getName(), detail,
			verdict, tone, tag, got ? Theme.VIOLET : null);
	}

	/** "24% of players would have one by now", or "2.4x the expected". */
	static String verdict(ContributionReport.Odds odds)
	{
		if (odds.getExpected() <= 0)
		{
			return "no chance logged yet";
		}
		if (odds.getGot() == 0)
		{
			return Format.have(odds.getWaiting()) + " would have one by now";
		}
		double ratio = odds.getRatio();
		String x = ratio >= 9.95 ? String.valueOf(Math.round(ratio)) : String.format(java.util.Locale.ENGLISH, "%.1f", ratio);
		return x + "x the expected: " + (ratio >= 1 ? "lucky" : "behind");
	}

	/** The latest kill in full. */
	static Card latest(Contribution.Boss boss, ContributionLog.Kill kill, ItemIcons icons)
	{
		Card card = new Card(new BorderLayout(0, 5));
		card.setBorder(new EmptyBorder(8, 9, 8, 9));
		if (kill.getUnique() != 0)
		{
			card.accent(Theme.VIOLET);
		}
		JPanel tiles = new JPanel(new GridLayout(1, 3, 5, 0));
		tiles.setOpaque(false);
		tiles.add(new StatTile(Format.percent(kill.getShare()), "Share", kill.isTeam() ? "estimate" : "solo",
			Theme.GOLD_BRIGHT));
		tiles.add(new StatTile(Format.chance(kill.getChance()), "Unique", kill.isEstimate() ? "estimate" : "chance",
			kill.getUnique() != 0 ? Theme.VIOLET : Theme.TEAL));
		tiles.add(new StatTile(shortDamage(kill.getMine()), "Damage", "yours", Theme.GOLD));
		card.add(tiles, BorderLayout.NORTH);
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.add(Ui.wrapped(damageSentence(boss, kill), Theme.small(), Theme.TEXT, 186));
		String why = why(boss, kill);
		if (why != null)
		{
			text.add(Ui.wrapped(why, Theme.small(), Theme.TEXT_FAINT, 186));
		}
		card.add(text, BorderLayout.CENTER);
		if (kill.getUnique() != 0)
		{
			JPanel found = new JPanel(new BorderLayout(6, 0));
			found.setOpaque(false);
			found.add(new IconBadge(kill.getUnique(), icons, true), BorderLayout.WEST);
			found.add(Ui.label("A unique came to you", Theme.small(), Theme.VIOLET), BorderLayout.CENTER);
			card.add(found, BorderLayout.SOUTH);
		}
		return card;
	}

	/** "You dealt 1,840 of at least 5,400 (Nex 3,400 and her four generals)." */
	static String damageSentence(Contribution.Boss boss, ContributionLog.Kill kill)
	{
		StringBuilder out = new StringBuilder("You dealt ").append(Format.count(kill.getMine()));
		if (!kill.isTeam())
		{
			return out.append(" damage alone; the kill was yours.").toString();
		}
		out.append(" of ");
		if (kill.getTotal() > boss.getHitpoints())
		{
			out.append(Format.count(kill.getTotal())).append(" seen");
		}
		else
		{
			out.append("at least ").append(Format.count(kill.getTotal()));
			switch (boss)
			{
				case NEX:
					out.append(" (Nex 3,400 and her four generals at 500)");
					break;
				case TITANS:
					out.append(" (two titans at 600)");
					break;
				default:
					break;
			}
		}
		return out.append('.').toString();
	}

	/** Why the kill's chance is what it is, when it needs saying. */
	static String why(Contribution.Boss boss, ContributionLog.Kill kill)
	{
		switch (boss)
		{
			case CORP:
				return kill.isCredit() ? (kill.isTeam() ? "You dealt the most damage, so the whole drop was yours." : null)
					: "No kill count line: another player dealt the most damage, and only they get loot.";
			case NEX:
				if (!kill.isCredit())
				{
					return "No kill count line, so no drop: the wiki says a minimum damage is needed but not how much.";
				}
				return kill.isTeam() ? "Estimate: the wiki shares uniques by contribution with no formula, so your "
					+ "share of the 1/43 a kill is used. The MVP's 10% boost is not added." : null;
			default:
				return kill.isTeam() ? "Uniques scale with your whole-percent share (wiki); the share is measured from "
					+ "the hitsplats you saw, so it is an estimate. Assumes you chose Loot." : "Assumes you chose Loot.";
		}
	}

	static Card row(ContributionLog.Kill kill, ItemIcons icons)
	{
		String detail = (kill.isTeam() ? Format.percent(kill.getShare()) + " share" : "Solo") + "  ·  "
			+ shortDamage(kill.getMine()) + " dmg";
		String extra = !kill.isCredit() && kill.getChance() <= 0 ? "no credit"
			: Format.chance(kill.getChance()) + " for you" + (kill.isEstimate() ? " (est.)" : "");
		boolean unique = kill.getUnique() != 0;
		JComponent tag = unique ? new Pill("Unique", Theme.VIOLET) : null;
		JComponent lead = unique ? new IconBadge(kill.getUnique(), icons, true) : null;
		String title = kill.getKc() > 0 ? "KC " + Format.count(kill.getKc()) : "Kill";
		return Rows.line(lead, title, detail, extra, unique ? Theme.VIOLET : Theme.TEXT_FAINT, tag,
			unique ? Theme.VIOLET : null);
	}

	/** "870", "1.8k". */
	static String shortDamage(long damage)
	{
		if (damage < 1000)
		{
			return String.valueOf(damage);
		}
		return String.format(java.util.Locale.ENGLISH, "%.1f", damage / 1000.0).replace(".0", "") + "k";
	}

	static String source(Contribution.Boss boss)
	{
		switch (boss)
		{
			case NEX:
				return "Rates from the OSRS Wiki: an effective 1/43 a kill for any unique, shared by contribution "
					+ "(damage to Nex, her generals and reavers) with no formula given, so your share is an estimate. "
					+ "Damage is counted from your own hitsplats.";
			case CORP:
				return "Rates from the OSRS Wiki. Only the player who dealt the most damage gets kill credit and "
					+ "loot. Damage is counted from your own hitsplats against its 2,000 hitpoints.";
			default:
				return "Rates from the OSRS Wiki at 100% contribution, scaled linearly by your whole-percent share of "
					+ "damage to both titans (600 hitpoints each). Prayer scrolls and the pet are left out.";
		}
	}

	/**
	 * The latest kills as columns, oldest on the left: the height is your share, gold for a kill with credit, a faint
	 * stub where the game gave the kill to someone else, and a violet gem where a unique came.
	 */
	static final class ShareStrip extends JComponent
	{
		static final int SHOWN = 30;
		private final List<ContributionLog.Kill> kills;

		ShareStrip(List<ContributionLog.Kill> kills)
		{
			this.kills = kills;
			setPreferredSize(new Dimension(10, 54));
			setOpaque(false);
			setToolTipText("Your latest kills, oldest on the left: the height is your share, violet where a unique came, faint where you had no credit.");
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
			int top = 7;
			int bottom = h - 10;
			int count = Math.min(SHOWN, kills.size());
			double max = 0;
			for (int i = 0; i < count; i++)
			{
				max = Math.max(max, kills.get(i).getShare());
			}
			// Rounded up to a tidy share so a strip of team kills still fills the height.
			double scale = max <= 0 ? 1 : RaidView.niceCeiling(max);
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
				ContributionLog.Kill kill = kills.get(count - 1 - k);
				int x = (int) Math.round(x0 + k * step);
				boolean unique = kill.getUnique() != 0;
				int barH = Math.max(2, (int) Math.round((bottom - top) * Math.min(1, kill.getShare() / scale)));
				if (!kill.isCredit() && kill.getChance() <= 0)
				{
					g.setColor(Theme.alpha(Theme.TEXT_FAINT, 110));
					g.fill(new RoundRectangle2D.Float(x, bottom - barH, barW, barH, 2, 2));
				}
				else
				{
					Color c = unique ? Theme.VIOLET : Theme.GOLD;
					g.setPaint(new GradientPaint(0, bottom - barH, c, 0, bottom, Theme.alpha(c, 100)));
					g.fill(new RoundRectangle2D.Float(x, bottom - barH, barW, barH, 2, 2));
				}
				if (unique)
				{
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

	static Card strip(ContributionReport report)
	{
		Card card = new Card(new BorderLayout(0, 4));
		card.setBorder(new EmptyBorder(7, 9, 7, 9));
		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		head.add(Ui.label("YOUR SHARE EACH KILL", Theme.small(), Theme.TEXT_MUTED), BorderLayout.WEST);
		if (report.getAverageShare() >= 0)
		{
			head.add(Ui.label("avg " + Format.percent(report.getAverageShare()), Theme.small(), Theme.GOLD),
				BorderLayout.EAST);
		}
		card.add(head, BorderLayout.NORTH);
		card.add(new ShareStrip(report.getRecent()), BorderLayout.CENTER);
		return card;
	}
}
