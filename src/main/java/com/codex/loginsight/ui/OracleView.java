package com.codex.loginsight.ui;

import com.codex.loginsight.core.Oracle;
import com.codex.loginsight.core.Report;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The luck oracle: where your own kill counts put every rare slot you found or still chase among all players, and
 * how long each unfinished page should take at your own pace.
 */
final class OracleView
{
	static final String[] FILTERS = {"Finds", "Chasing", "Pages"};
	private static final int SHOWN = 60;

	private OracleView()
	{
	}

	static StackPanel build(Report report, int filter, ItemIcons icons, Consumer<String> openPage)
	{
		StackPanel stack = new StackPanel(5, 8);
		if (!report.isLoggedIn())
		{
			stack.add(Rows.note("Log in to read your luck, slot by slot.", 190));
			return stack;
		}
		Oracle oracle = report.getOracle();
		stack.add(header(oracle));
		if (filter == 0)
		{
			finds(stack, oracle.getOwned(), icons);
		}
		else if (filter == 1)
		{
			if (!report.ownedRead())
			{
				stack.add(Rows.note(QuarryView.UNREAD, 190));
				return stack;
			}
			chases(stack, oracle.getChasing(), icons);
		}
		else
		{
			if (!report.ownedRead())
			{
				stack.add(Rows.note(QuarryView.UNREAD, 190));
				return stack;
			}
			pages(stack, oracle.getPages(), openPage);
		}
		return stack;
	}

	/** The scale with every reading on it, and the oracle's two sentences: your luckiest find and your driest chase. */
	static Card header(Oracle oracle)
	{
		Card card = new Card(new BorderLayout(0, 6));
		card.setBorder(new EmptyBorder(8, 9, 8, 9));
		card.highlighted(true);
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(Ui.label("THE ORACLE", Theme.small(), Theme.GOLD), BorderLayout.WEST);
		int found = oracle.getOwned().size();
		if (found > 0)
		{
			top.add(Ui.label("lucky on " + oracle.luckyCount() + " of " + found, Theme.small(), Theme.TEXT_MUTED),
				BorderLayout.EAST);
		}
		card.add(top, BorderLayout.NORTH);

		double[] finds = new double[found];
		for (int i = 0; i < found; i++)
		{
			finds[i] = oracle.getOwned().get(i).getLuckier();
		}
		double[] chases = new double[oracle.getChasing().size()];
		for (int i = 0; i < chases.length; i++)
		{
			// Still waiting, you are luckier than nobody who has it and level with half of those who do not.
			chases[i] = oracle.getChasing().get(i).getDry() / 2;
		}
		card.add(new LuckStrip(finds, chases), BorderLayout.CENTER);

		JPanel lines = new JPanel();
		lines.setOpaque(false);
		lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
		Oracle.Owned best = oracle.luckiest();
		Oracle.Chase driest = oracle.driest();
		if (best != null)
		{
			lines.add(Ui.wrapped(foundSentence(best), Theme.small(), Theme.luck(best.getLuckier()), 190));
		}
		if (driest != null)
		{
			if (best != null)
			{
				lines.add(javax.swing.Box.createVerticalStrut(3));
			}
			lines.add(Ui.wrapped(drySentence(driest), Theme.small(), Theme.dry(driest.getDry()), 190));
		}
		if (best == null && driest == null)
		{
			lines.add(Ui.wrapped("The oracle reads each rare slot at your own kill count. Open your collection log pages"
				+ " to read their counts, and every slot that drops from now on is read at the kill it came on.",
				Theme.small(), Theme.TEXT_MUTED, 190));
		}
		card.add(lines, BorderLayout.SOUTH);
		card.setToolTipText("<html>Beads above the bar are slots you found, rings below are slots you still chase.<br>"
			+ "The diamond is the middle of your finds. Rates as listed in the OSRS Wiki's drop tables.</html>");
		return card;
	}

	/** "Tanzanite fang at 212 Zulrah kc: luckier than 78% of players." */
	static String foundSentence(Oracle.Owned o)
	{
		String at = o.getBasis() == Oracle.Basis.AT_KILL
			? o.getName() + " at " + kc(o.getCount()) + " " + Format.counterName(o.getLabel()) + " kc"
			: copies(o.getCopies(), o.getName()) + " by " + kc(o.getCount()) + " " + Format.counterName(o.getLabel()) + " kc";
		return at + ": " + lower(LuckDial.luckLine((int) Math.round(o.getLuckier() * 100))) + ".";
	}

	/** "Jar of swamp: dry at 2.3x the rate, 90% of players have it by now." */
	static String drySentence(Oracle.Chase c)
	{
		if (c.getDry() == 0)
		{
			return c.getName() + ": overdue, guaranteed by a count you have passed.";
		}
		return c.getName() + ": dry at " + times(c.getTimesRate()) + " the rate, " + Format.have(c.getDry())
			+ " of players have it by now.";
	}

	private static void finds(StackPanel stack, List<Oracle.Owned> owned, ItemIcons icons)
	{
		stack.add(new SectionHeader("Your finds", owned.isEmpty() ? null : "luckiest first"));
		if (owned.isEmpty())
		{
			stack.add(Rows.note("No rare finds read yet. A slot is read at the kill count it dropped on once Log Insight"
				+ " sees it drop, or from its copies once its page has been read.", 190));
			return;
		}
		int shown = 0;
		for (Oracle.Owned o : owned)
		{
			stack.add(findRow(o, icons));
			if (++shown >= SHOWN)
			{
				break;
			}
		}
		stack.add(Rows.note("Luck at the kill is the share of players who would have needed more kills; from copies,"
			+ " the share who would have fewer by the same count. Ties count half.", 190));
	}

	static Card findRow(Oracle.Owned o, ItemIcons icons)
	{
		Color tone = Theme.luck(o.getLuckier());
		String detail;
		if (o.getBasis() == Oracle.Basis.AT_KILL)
		{
			detail = Format.chance(o.getChance()) + "  ·  at " + kc(o.getCount()) + " kc  ·  " + times(o.getTimesRate());
		}
		else
		{
			detail = o.getCopies() + " by " + kc(o.getCount()) + " kc  ·  average " + Format.slots(o.getTimesRate());
		}
		String standing = TrophyShelf.standing(o.getLuckier());
		Card card = Rows.line(new IconBadge(o.getItemId(), icons, true), o.getName(), detail,
			LuckDial.luckLine((int) Math.round(o.getLuckier() * 100)), tone,
			new Pill(standing == null ? "-" : standing, tone), o.getLuckier() >= 0.9 ? Theme.GREEN : null);
		card.setToolTipText("<html>" + Ui.escape(o.getName()) + (o.getPage() == null ? "" : " (" + Ui.escape(o.getPage()) + ")")
			+ "<br>" + Format.chance(o.getChance()) + " per " + Ui.escape(Format.counterName(o.getLabel())) + " kc"
			+ (o.getBasis() == Oracle.Basis.AT_KILL
			? "<br>Dropped at " + Format.count(o.getCount()) + " kc, " + times(o.getTimesRate()) + " the rate"
			: "<br>" + o.getCopies() + (o.getCopies() == 1 ? " copy" : " copies") + " by " + Format.count(o.getCount())
			+ " kc; an average player would have " + Format.slots(o.getTimesRate()))
			+ "</html>");
		return card;
	}

	private static void chases(StackPanel stack, List<Oracle.Chase> chasing, ItemIcons icons)
	{
		stack.add(new SectionHeader("Still chasing", chasing.isEmpty() ? null : "driest first"));
		if (chasing.isEmpty())
		{
			stack.add(Rows.note("No missing rare slot has a kill count yet. Open its page in your collection log.", 190));
			return;
		}
		int shown = 0;
		for (Oracle.Chase c : chasing)
		{
			stack.add(chaseRow(c, icons));
			if (++shown >= SHOWN)
			{
				break;
			}
		}
		stack.add(Rows.note("Drops have no memory: a dry streak does not bring the next one closer, so the time to it"
			+ " is the same average wait from here.", 190));
	}

	static Card chaseRow(Oracle.Chase c, ItemIcons icons)
	{
		String detail = Format.chance(c.getChance()) + "  ·  " + kc(c.getCount()) + " kc  ·  " + times(c.getTimesRate());
		String extra;
		if (c.getDry() == 0)
		{
			extra = "Guaranteed by a count you have passed";
		}
		else if (Double.isFinite(c.getHours()) && c.getPerHour() > 0)
		{
			extra = Format.about(c.getHours()) + " more at " + (c.isWanted() ? "" : c.isOwnPace() ? "your " : "a typical ")
				+ perHour(c.getPerHour()) + "/h";
		}
		else
		{
			extra = Format.have(c.getDry()) + " of players have it by now";
		}
		if (c.isWanted())
		{
			extra = "Wanted  ·  " + extra;
		}
		Color tone = Theme.dry(c.getDry());
		Card card = Rows.line(new IconBadge(c.getItemId(), icons, false), c.getName(), detail, extra,
			c.isWanted() ? Theme.GOLD : c.isOwnPace() ? Theme.TEAL : Theme.TEXT_FAINT,
			new Pill(Format.dry(c.getDry()), tone), c.getDry() < 0.2 ? tone : null);
		if (c.isWanted())
		{
			card.highlighted(true);
		}
		card.setToolTipText("<html>" + Ui.escape(c.getName()) + (c.getPage() == null ? "" : " (" + Ui.escape(c.getPage()) + ")")
			+ "<br>" + Format.chance(c.getChance()) + " per " + Ui.escape(Format.counterName(c.getLabel())) + " kc, "
			+ Format.count(c.getCount()) + " kc so far<br>" + times(c.getTimesRate()) + " the rate: "
			+ Format.have(c.getDry()) + " of players would have it by now"
			+ (c.getSource() == null ? "" : "<br>Soonest at " + Ui.escape(Format.source(c.getSource())))
			+ "</html>");
		return card;
	}

	private static void pages(StackPanel stack, List<Oracle.Forecast> pages, Consumer<String> openPage)
	{
		stack.add(new SectionHeader("Time to finish", pages.isEmpty() ? null : "soonest first"));
		if (pages.isEmpty())
		{
			stack.add(Rows.note("Every page with rates is finished.", 190));
			return;
		}
		int shown = 0;
		for (Oracle.Forecast f : pages)
		{
			Card row = pageRow(f);
			if (openPage != null)
			{
				row.onClick(() -> openPage.accept(f.getPage()));
			}
			stack.add(row);
			if (++shown >= SHOWN)
			{
				break;
			}
		}
		stack.add(Rows.note("Average hours to finish each page's slots that have rates, at your own pace where Log"
			+ " Insight has timed enough of your kill counts. Click a page to open it.", 190));
	}

	static Card pageRow(Oracle.Forecast f)
	{
		ProgressRing ring = new ProgressRing(26, 3.5f);
		ring.setProgress(f.getTotal() == 0 ? 0 : f.getOwned() / (double) f.getTotal(), false, false);
		StringBuilder detail = new StringBuilder();
		if (Double.isFinite(f.getHalf()))
		{
			detail.append("Half by ").append(Format.hours(f.getHalf()));
			if (Double.isFinite(f.getNineInTen()))
			{
				detail.append("  ·  9/10 by ").append(Format.hours(f.getNineInTen()));
			}
		}
		else
		{
			detail.append(Format.about(f.getHours())).append(" on average");
		}
		String extra = f.getOwned() + " of " + f.getTotal();
		if (f.getSource() != null && f.getPerHour() > 0)
		{
			String where = Format.source(f.getSource());
			extra += (where.equalsIgnoreCase(f.getPage()) ? "" : "  ·  " + where) + "  ·  " + (f.isOwnPace() ? "your " : "typical ") + perHour(f.getPerHour()) + "/h";
		}
		if (f.getUnrated() > 0)
		{
			extra += "  ·  +" + f.getUnrated() + " unrated";
		}
		Card card = Rows.line(ring, f.getPage(), detail.toString(), extra, f.isOwnPace() ? Theme.TEAL : Theme.TEXT_FAINT,
			new Pill(Format.hours(f.getHours()), Theme.EMBER), null);
		card.setToolTipText("<html>" + Ui.escape(f.getPage()) + ": " + Format.about(f.getHours()) + " on average to finish"
			+ (Double.isFinite(f.getHalf()) ? "<br>Half of players finish by " + Format.hours(f.getHalf())
			+ ", nine in ten by " + Format.hours(f.getNineInTen()) : "")
			+ (f.getLuck() >= 0 ? "<br>" + LuckDial.luckLine((int) Math.round(f.getLuck() * 100)) + " on this page so far" : "")
			+ "</html>");
		return card;
	}

	/** "0.41x", "2.3x", "14x"; "<0.01x" for a find on almost the first kill. */
	static String times(double multiple)
	{
		if (!(multiple > 0))
		{
			return "0x";
		}
		if (multiple < 0.01)
		{
			return "<0.01x";
		}
		if (multiple < 1)
		{
			return trim(String.format(Locale.ENGLISH, "%.2f", multiple)) + "x";
		}
		if (multiple < 9.95)
		{
			return trim(String.format(Locale.ENGLISH, "%.1f", multiple)) + "x";
		}
		return Format.count(Math.round(multiple)) + "x";
	}

	private static String trim(String number)
	{
		String out = number.replaceAll("0+$", "");
		return out.endsWith(".") ? out.substring(0, out.length() - 1) : out;
	}

	/** A kill count by the suite's number rule: in full under 10,000, then in whole thousands. */
	static String kc(long count)
	{
		return count < 10_000 ? Format.count(count) : Format.count(Math.round(count / 1000.0)) + "k";
	}

	private static String perHour(double perHour)
	{
		return perHour >= 10 ? String.valueOf(Math.round(perHour)) : String.format(Locale.ENGLISH, "%.1f", perHour).replace(".0", "");
	}

	private static String copies(int copies, String name)
	{
		return copies == 1 ? "1 " + name : copies + " x " + name;
	}

	private static String lower(String sentence)
	{
		return sentence.isEmpty() ? sentence : Character.toLowerCase(sentence.charAt(0)) + sentence.substring(1);
	}
}
