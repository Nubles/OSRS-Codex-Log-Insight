package com.codex.loginsight.ui;

import com.codex.loginsight.core.PetPlan;
import com.codex.loginsight.core.Projection;
import com.codex.loginsight.core.Prospect;
import com.codex.loginsight.core.Report;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Planning a stretch of play: which activities give the most new slots in the time you have,
 * which pages are closest to a green log, and how long each missing pet is likely to take.
 */
final class PlanView
{
	static final String[] HORIZONS = {"1h", "5h", "20h", "100h"};
	static final double[] HOURS = {1, 5, 20, 100};
	private static final int WIDTH = 190;
	private static final int TEXT = 118;
	private static final int BEST = 8;
	private static final int PAGES = 6;
	private static final int PETS = 6;

	private PlanView()
	{
	}

	static StackPanel build(Report report, int horizon, ItemIcons icons, Consumer<String> openPage)
	{
		StackPanel stack = new StackPanel(5, 8);
		if (!report.isLoggedIn())
		{
			stack.add(Rows.note("Log in to plan your next stretch of collection logging.", WIDTH));
			return stack;
		}
		if (!report.isStructureLoaded())
		{
			stack.add(Rows.note("Log Insight is still reading the collection log's pages from the game.", WIDTH));
			return stack;
		}
		if (!report.ownedRead())
		{
			stack.add(Rows.note(QuarryView.UNREAD, WIDTH));
			return stack;
		}
		double hours = HOURS[Math.max(0, Math.min(HOURS.length - 1, horizon))];
		String span = HORIZONS[Math.max(0, Math.min(HORIZONS.length - 1, horizon))];

		best(stack, report, hours, span, icons);
		greenLogs(stack, report, hours, span, openPage);
		pets(stack, report, hours, span, icons, openPage);
		return stack;
	}

	/** Activities ranked by the new slots an average player would get from them in the time chosen. */
	private static void best(StackPanel stack, Report report, double hours, String span, ItemIcons icons)
	{
		List<Prospect> open = new ArrayList<>();
		int locked = 0;
		for (Prospect p : report.getProspects())
		{
			if (p.getLockedBy() != null)
			{
				locked++;
			}
			else if (p.getRemaining() > 0 && p.getPerHour() > 0)
			{
				open.add(p);
			}
		}
		open.sort(Comparator.comparingDouble((Prospect p) -> -p.expectedSlots(hours))
			.thenComparingDouble(p -> -p.chanceOfAny(hours)));
		stack.add(new SectionHeader("Best use of " + span, "new slots"));
		if (open.isEmpty())
		{
			stack.add(Rows.note("Nothing you can do yet has slots left with known rates.", WIDTH));
			return;
		}
		for (int i = 0; i < Math.min(BEST, open.size()); i++)
		{
			stack.add(prospectRow(open.get(i), i + 1, hours, icons));
		}
		stack.add(Rows.note("Expected new slots for an average player in " + span + " at "
			+ (report.isIronRates() ? "ironman" : "typical") + " rates or your own pace"
			+ (locked > 0 ? ". " + locked + (locked == 1 ? " activity" : " activities") + " you cannot do yet are left out." : "."),
			WIDTH));
	}

	static Card prospectRow(Prospect p, int rank, double hours, ItemIcons icons)
	{
		double slots = p.expectedSlots(hours);
		double any = p.chanceOfAny(hours);
		Card card = new Card(new BorderLayout(8, 0));
		card.setBorder(new EmptyBorder(6, 8, 6, 8));
		if (rank == 1)
		{
			card.accent(Theme.TEAL);
		}
		JPanel lead = new JPanel(new BorderLayout());
		lead.setOpaque(false);
		lead.add(new QuarryView.Rank(rank, false), BorderLayout.NORTH);
		card.add(lead, BorderLayout.WEST);

		JPanel text = column();
		text.add(Ui.clamped(Format.source(p.getName()), Theme.body(), Theme.TEXT, TEXT, 2));
		String detail = Format.detail(p.getName());
		if (detail != null && !detail.isEmpty())
		{
			text.add(Ui.clamped(detail, Theme.small(), Theme.GOLD_DIM, TEXT, 1));
		}
		// Past near certainty the chance of one says nothing the count does not.
		text.add(Ui.label(Format.slots(slots) + " new " + Format.slotWord(slots)
			+ (any < 0.995 ? "  ·  " + Format.percent(any) + " any" : ""), Theme.small(), Theme.TEAL));
		String next = p.getNextItemName() == null ? "" : "likeliest " + p.getNextItemName();
		JComponent nextLabel = Ui.clamped(next, Theme.small(), Theme.TEXT_MUTED, TEXT, 2);
		nextLabel.setToolTipText(next);
		text.add(nextLabel);
		text.add(Ui.label(p.getRemaining() + " left  ·  " + Math.round(p.getPerHour()) + " an hour"
			+ (p.isOwnPace() ? ", your pace" : ""), Theme.small(), p.isOwnPace() ? Theme.GOLD : Theme.TEXT_FAINT));
		fix(text);
		card.add(text, BorderLayout.CENTER);
		if (p.getNextItemId() > 0)
		{
			JPanel trail = new JPanel(new BorderLayout());
			trail.setOpaque(false);
			trail.add(new IconBadge(p.getNextItemId(), icons, false), BorderLayout.NORTH);
			card.add(trail, BorderLayout.EAST);
		}
		card.setToolTipText("<html>" + Ui.escape(p.getName()) + "<br>In " + Format.hours(hours) + ": "
			+ String.format(Locale.ENGLISH, "%.2f", slots) + " new slots on average, " + Format.percent(any)
			+ " chance of at least one.</html>");
		return card;
	}

	/** Unfinished pages whose every missing slot has rates, nearest to done first. */
	private static void greenLogs(StackPanel stack, Report report, double hours, String span, Consumer<String> openPage)
	{
		List<Report.PageReport> pages = new ArrayList<>();
		for (Report.PageReport page : report.getPages())
		{
			Projection p = page.getProjection();
			if (!page.isComplete() && p != null && p.getUnrated() == 0 && Double.isFinite(p.getMedian()))
			{
				pages.add(page);
			}
		}
		pages.sort(Comparator.comparing((Report.PageReport page) -> page.getProjection().getLockedBy() != null)
			.thenComparingDouble(page -> page.getProjection().getMedian()));
		stack.add(new SectionHeader("Nearest green logs", "chance in " + span));
		if (pages.isEmpty())
		{
			stack.add(Rows.note("No unfinished page can be projected yet.", WIDTH));
			return;
		}
		for (int i = 0; i < Math.min(PAGES, pages.size()); i++)
		{
			Report.PageReport page = pages.get(i);
			Projection p = page.getProjection();
			double within = p.chanceBy(hours);
			ProgressRing ring = new ProgressRing(26, 3.5f);
			ring.setProgress(page.progress(), false, false);
			int left = page.getTotal() - page.getOwned();
			String source = page.getNextSource() == null ? "" : Format.source(page.getNextSource());
			Card card = Rows.line(ring, page.getName(),
				left + (left == 1 ? " slot left" : " slots left")
					+ (source.isEmpty() || source.equalsIgnoreCase(page.getName()) ? "" : "  ·  " + source),
				p.getLockedBy() != null ? "Needs " + p.getLockedBy() : odds(p.getMedian(), p.getLikely()),
				p.getLockedBy() != null ? Theme.EMBER : Theme.GOLD,
				new Pill(Format.percent(within), p.getLockedBy() != null ? Theme.TEXT_FAINT : chanceColor(within)), null);
			card.setToolTipText("<html>" + Ui.escape(page.getName()) + "<br>" + Format.percent(within)
				+ " chance to finish it within " + span + " at your rates.</html>");
			card.onClick(() -> openPage.accept(page.getName()));
			stack.add(card);
		}
	}

	/** Missing pets as hunts: the time to see each, and what else turns up on the way. */
	private static void pets(StackPanel stack, Report report, double hours, String span, ItemIcons icons,
		Consumer<String> openPage)
	{
		List<PetPlan> pets = report.getPets();
		stack.add(new SectionHeader("Pet hunt", pets.isEmpty() ? null : "chance in " + span));
		if (pets.isEmpty())
		{
			stack.add(Rows.note("Every pet Log Insight can rate is already yours.", WIDTH));
			return;
		}
		for (int i = 0; i < Math.min(PETS, pets.size()); i++)
		{
			PetPlan pet = pets.get(i);
			boolean locked = pet.getLockedBy() != null;
			double within = pet.chanceWithin(hours);
			double alongside = pet.slotsAlongside();
			Card card = new Card(new BorderLayout(8, 0));
			card.setBorder(new EmptyBorder(6, 8, 6, 8));
			if (i == 0 && !locked)
			{
				card.accent(Theme.VIOLET);
			}
			JPanel lead = new JPanel(new BorderLayout());
			lead.setOpaque(false);
			lead.add(new IconBadge(pet.getItemId(), icons, false), BorderLayout.NORTH);
			card.add(lead, BorderLayout.WEST);

			JPanel text = column();
			JPanel top = new JPanel(new BorderLayout(6, 0));
			top.setOpaque(false);
			JLabel name = Ui.label(pet.getName(), Theme.body(), locked ? Theme.TEXT_MUTED : Theme.TEXT);
			name.setMinimumSize(new Dimension(10, name.getPreferredSize().height));
			name.setPreferredSize(new Dimension(10, name.getPreferredSize().height));
			top.add(name, BorderLayout.CENTER);
			top.add(new Pill(Format.percent(within), locked ? Theme.TEXT_FAINT : chanceColor(within)), BorderLayout.EAST);
			top.setAlignmentX(0);
			text.add(top);
			text.add(Ui.label(Format.source(pet.getSource()) + "  ·  " + Format.chance(pet.getChance()), Theme.small(),
				Theme.TEXT_MUTED));
			if (locked)
			{
				text.add(Ui.label("Needs " + pet.getLockedBy(), Theme.small(), Theme.EMBER));
			}
			PetPlan.LevelUp up = pet.getLevelUp();
			if (up != null)
			{
				text.add(Ui.label(levelLine(up), Theme.small(), Theme.TEAL));
			}
			text.add(Ui.label(odds(pet.getMedian(), pet.getLikely()),
				Theme.small(), locked ? Theme.TEXT_FAINT : Theme.VIOLET));
			if (alongside >= 0.05)
			{
				text.add(Ui.label("+" + Format.slots(alongside) + " other " + Format.slotWord(alongside)
					+ " on the way", Theme.small(), Theme.TEXT_FAINT));
			}
			fix(text);
			card.add(text, BorderLayout.CENTER);
			card.setToolTipText("<html>" + Ui.escape(pet.getName()) + " from " + Ui.escape(pet.getSource()) + "<br>"
				+ Format.percent(within) + " chance within " + span + " of hunting it.<br>In the hours it takes half of players, "
				+ "they also find " + String.format(Locale.ENGLISH, "%.1f", alongside) + " other new slots there."
				+ (up == null ? "" : "<br>" + levelTip(up, pet.getChance())) + "</html>");
			if (pet.getPage() != null)
			{
				card.onClick(() -> openPage.accept(pet.getPage()));
			}
			stack.add(card);
		}
	}

	/** "At 99: 1 in 34,015", or for a 99, "At 200M xp: 1 in 2,268". */
	static String levelLine(PetPlan.LevelUp up)
	{
		return (up.isMaxedExperience() ? "At 200M xp: " : "At 99: ") + Format.chance(up.getChance());
	}

	static String levelTip(PetPlan.LevelUp up, double now)
	{
		return "Skilling pets get likelier as you level: " + Format.chance(now) + " at your " + up.skillName() + " "
			+ up.getLevel() + ", " + Format.chance(up.getChance())
			+ (up.isMaxedExperience() ? " with 200M experience." : " at 99.");
	}

	/** The hours by which half and nine in ten players are done: "50% 12h  ·  90% 31h". */
	static String odds(double median, double likely)
	{
		return "50% " + Format.hours(median) + "  ·  90% " + Format.hours(likely);
	}

	/** Faint when unlikely, gold through the middle, green once it is near certain. */
	static Color chanceColor(double chance)
	{
		if (chance >= 0.9)
		{
			return Theme.GREEN;
		}
		if (chance >= 0.5)
		{
			return Theme.mix(Theme.GOLD, Theme.TEAL, (chance - 0.5) / 0.4);
		}
		if (chance >= 0.1)
		{
			return Theme.GOLD;
		}
		return Theme.TEXT_MUTED;
	}

	private static JPanel column()
	{
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		return text;
	}

	/** Lets long lines clip instead of widening the card past the panel. */
	private static void fix(JComponent text)
	{
		text.setMinimumSize(new Dimension(10, 10));
		text.setPreferredSize(new Dimension(10, text.getPreferredSize().height));
	}
}
