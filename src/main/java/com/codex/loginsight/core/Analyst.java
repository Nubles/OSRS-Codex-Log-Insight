package com.codex.loginsight.core;

import com.codex.loginsight.data.Activity;
import com.codex.loginsight.data.ItemOdds;
import com.codex.loginsight.data.LogData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the log's shape, the bundled rates and this account's progress into a Report.
 * Pure Java over immutable inputs, so it runs off the client thread and is tested
 * without a game.
 */
public final class Analyst
{
	/** Kill count gaps needed before your own pace replaces the typical rate. */
	static final int PACE_SAMPLES = 15;
	private static final int DRIEST = 6;
	/** At most this many of the driest slots come from one page, so one dry casket tier cannot fill the list. */
	private static final int DRIEST_PER_PAGE = 2;
	private static final double DRY_SHOWN_BELOW = 0.35;

	private final LogData data;
	/** The counter each activity advances, when its slots agree on one. */
	private final Map<Integer, String> activityLabels = new HashMap<>();
	private static final int MAX_CACHED = 20_000;
	/**
	 * Curves are kilobytes each and a new own pace misses for every page it touches, so they get a much
	 * smaller budget than evaluations: about ten full reports' worth.
	 */
	private static final int MAX_CURVES = 2_500;
	private final Map<String, Eval> evaluations = new java.util.concurrent.ConcurrentHashMap<>();
	/** Page curves by the evaluations and rates they came from; null values are kept too, for pages with none. */
	private final Map<List<Object>, Projection> projections = java.util.Collections.synchronizedMap(new HashMap<>());

	public Analyst(LogData data)
	{
		this.data = data;
		for (Activity activity : data.getActivities())
		{
			Map<String, Integer> votes = new HashMap<>();
			for (Activity.Drop drop : activity.getDrops())
			{
				ItemOdds odds = data.odds(drop.getItemId());
				if (odds != null)
				{
					votes.merge(odds.mainLabel(), 1, Integer::sum);
				}
			}
			votes.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.filter(e -> e.getValue() * 2 >= activity.getDrops().size())
				.ifPresent(e -> activityLabels.put(activity.getIndex(), e.getKey()));
		}
	}

	/**
	 * @param ownPace use the pace learned from your kill counts where there is enough of it
	 */
	public Report analyse(LogStructure structure, Progress progress, boolean iron, boolean ownPace, int officialOwned,
		int officialTotal)
	{
		return analyse(structure, progress, iron, ownPace, officialOwned, officialTotal, Unlocks.UNKNOWN);
	}

	/**
	 * @param unlocks what the account can do yet; activities it cannot do sink below the rest
	 */
	public Report analyse(LogStructure structure, Progress progress, boolean iron, boolean ownPace, int officialOwned,
		int officialTotal, Unlocks unlocks)
	{
		Rates rates = new Rates(progress, iron, ownPace);
		Set<Integer> owned = progress.getOwned();
		Map<String, Integer> counters = progress.getCounters();

		List<Report.PageReport> pages = new ArrayList<>();
		Set<Integer> unique = new LinkedHashSet<>();
		int complete = 0;
		for (LogStructure.Tab tab : structure.getTabs())
		{
			for (LogStructure.Page page : tab.getPages())
			{
				Report.PageReport report = page(structure, page, progress, rates, unlocks);
				pages.add(report);
				if (report.isComplete())
				{
					complete++;
				}
				unique.addAll(page.getItems());
			}
		}

		int knownOwned = 0;
		List<Integer> missing = new ArrayList<>();
		for (int id : unique)
		{
			if (owned.contains(id))
			{
				knownOwned++;
			}
			else
			{
				missing.add(id);
			}
		}

		Finish finish = finish(missing, owned, rates);
		Luck luck = luck(unique, owned, counters);
		List<Report.ItemReport> driest = driest(structure, missing, progress, rates);
		List<Prospect> prospects = new ArrayList<>();
		List<Report.Hunt> hunts = hunts(structure, owned, rates, unlocks, prospects);
		List<PetPlan> pets = pets(structure, unique, owned, rates, unlocks);

		return new Report(true, !structure.isEmpty(), officialOwned, officialTotal, knownOwned, unique.size(),
			progress.getFullReadAt(), progress.pagesReadCount(), pages.size(), complete, luck, finish.hours,
			finish.unrated, iron, pages, hunts, driest, progress.getFinds(), prospects, pets);
	}

	/** Builds the report for one slot, for the panel and for the drop plaque. */
	public Report.ItemReport item(LogStructure structure, int itemId, String page, Progress progress, boolean iron)
	{
		return item(structure, itemId, page, progress, new Rates(progress, iron, true));
	}

	private Report.PageReport page(LogStructure structure, LogStructure.Page page, Progress progress, Rates rates,
		Unlocks unlocks)
	{
		Set<Integer> owned = progress.getOwned();
		Map<String, Integer> counters = progress.getCounters();
		List<Report.ItemReport> items = new ArrayList<>();
		List<Integer> missing = new ArrayList<>();
		Map<String, Integer> labelVotes = new LinkedHashMap<>();
		int ownedCount = 0;
		for (int id : page.getItems())
		{
			items.add(item(structure, id, page.getName(), progress, rates));
			if (owned.contains(id))
			{
				ownedCount++;
			}
			else
			{
				missing.add(id);
			}
			ItemOdds odds = data.odds(id);
			if (odds != null)
			{
				for (ItemOdds.Roll roll : odds.getRolls())
				{
					labelVotes.merge(roll.getLabel(), roll.getLabel().equals(odds.mainLabel()) ? 2 : 1, Integer::sum);
				}
			}
		}

		String mainLabel = labelVotes.entrySet().stream()
			.max(Map.Entry.comparingByValue())
			.map(Map.Entry::getKey)
			.orElse(null);
		List<String> shownLabels = progress.pageLabels(page.getName());
		if (shownLabels == null)
		{
			shownLabels = new ArrayList<>(labelVotes.keySet());
		}
		List<String> lines = new ArrayList<>();
		for (String label : shownLabels)
		{
			Integer value = counters.get(label);
			if (value != null)
			{
				lines.add(label + ": " + String.format("%,d", value));
			}
		}
		if (mainLabel == null && !shownLabels.isEmpty())
		{
			mainLabel = shownLabels.get(0);
		}
		Integer count = mainLabel == null ? null : counters.get(mainLabel);

		Finish finish = finish(missing, owned, rates);
		Next next = next(missing, owned, rates);
		Luck luck = luck(page.getItems(), owned, counters);
		return new Report.PageReport(page.getTab(), page.getName(), ownedCount, page.getItems().size(), lines,
			count == null ? -1 : count, mainLabel, luck, finish.hours, next.hours, next.source, finish.unrated,
			progress.isPageRead(page.getName()), items, projection(structure, missing, owned, rates, unlocks));
	}

	private Report.ItemReport item(LogStructure structure, int id, String page, Progress progress, Rates rates)
	{
		ItemOdds odds = data.odds(id);
		double chance = 0;
		String label = null;
		int count = -1;
		double dry = -1;
		if (odds != null)
		{
			label = odds.mainLabel();
			chance = odds.mainChance();
			Integer value = progress.counter(label);
			count = value == null ? -1 : value;
			dry = odds.dryChance(progress.getCounters());
		}
		Activity.Drop fastest = rates.fastest(id);
		String source = null;
		double hours = Double.POSITIVE_INFINITY;
		if (fastest != null)
		{
			Activity activity = data.activity(fastest.getActivityIndex());
			source = activity.getName();
			hours = rates.hours(activity, fastest.getAttempts());
			if (chance == 0)
			{
				chance = 1 / fastest.getAttempts();
			}
		}
		return new Report.ItemReport(id, structure.name(id), page, progress.owns(id), data.isPet(id), chance, label,
			count, dry, source, hours);
	}

	private Luck luck(Collection<Integer> items, Set<Integer> owned, Map<String, Integer> counters)
	{
		List<Double> chances = new ArrayList<>();
		int have = 0;
		Set<Integer> seen = new HashSet<>();
		for (int id : items)
		{
			if (!seen.add(id))
			{
				continue;
			}
			ItemOdds odds = data.odds(id);
			if (odds == null)
			{
				continue;
			}
			double dry = odds.dryChance(counters);
			if (dry < 0)
			{
				continue;
			}
			chances.add(1 - dry);
			if (owned.contains(id))
			{
				have++;
			}
		}
		if (chances.isEmpty())
		{
			return null;
		}
		double[] array = new double[chances.size()];
		for (int i = 0; i < array.length; i++)
		{
			array[i] = chances.get(i);
		}
		return Luck.of(array, have);
	}

	private List<Report.ItemReport> driest(LogStructure structure, List<Integer> missing, Progress progress, Rates rates)
	{
		List<Report.ItemReport> dry = new ArrayList<>();
		for (int id : missing)
		{
			ItemOdds odds = data.odds(id);
			if (odds == null)
			{
				continue;
			}
			double chance = odds.dryChance(progress.getCounters());
			if (chance >= 0 && chance < DRY_SHOWN_BELOW)
			{
				List<LogStructure.Page> pages = structure.pagesOf(id);
				dry.add(item(structure, id, pages.isEmpty() ? null : pages.get(0).getName(), progress, rates));
			}
		}
		dry.sort(Comparator.comparingDouble(Report.ItemReport::getDryChance));
		List<Report.ItemReport> shown = new ArrayList<>();
		Map<String, Integer> perPage = new HashMap<>();
		for (Report.ItemReport item : dry)
		{
			if (perPage.merge(String.valueOf(item.getPage()), 1, Integer::sum) <= DRIEST_PER_PAGE)
			{
				shown.add(item);
			}
			if (shown.size() == DRIEST)
			{
				break;
			}
		}
		return shown;
	}

	private List<Report.Hunt> hunts(LogStructure structure, Set<Integer> owned, Rates rates, Unlocks unlocks,
		List<Prospect> prospects)
	{
		Set<Integer> inLog = new HashSet<>();
		for (LogStructure.Tab tab : structure.getTabs())
		{
			for (LogStructure.Page page : tab.getPages())
			{
				inLog.addAll(page.getItems());
			}
		}
		List<Report.Hunt> hunts = new ArrayList<>();
		for (Activity activity : data.getActivities())
		{
			double perHour = rates.perHour(activity);
			if (perHour <= 0)
			{
				continue;
			}
			Eval eval = evaluate(activity, owned, id -> inLog.isEmpty() || inLog.contains(id));
			if (eval.remaining == 0 || Double.isInfinite(eval.nextAttempts))
			{
				continue;
			}
			boolean pet = false;
			for (int id : eval.missing)
			{
				pet |= data.isPet(id);
			}
			double petHours = Double.POSITIVE_INFINITY;
			int petItem = -1;
			if (pet)
			{
				Eval pets = evaluate(activity, owned, id -> data.isPet(id) && (inLog.isEmpty() || inLog.contains(id)));
				petHours = rates.hoursFor(activity, pets.nextAttempts);
				petItem = pets.nextItem;
			}
			hunts.add(new Report.Hunt(activity.getName(), activity.getCategory(),
				rates.hoursFor(activity, eval.nextAttempts), rates.hoursFor(activity, eval.allAttempts), eval.remaining,
				eval.nextItem, eval.nextItem < 0 ? null : structure.name(eval.nextItem), eval.nextChance, eval.nextExact, perHour,
				rates.isOwnPace(activity), pet, eval.missing, petHours, petItem, petItem < 0 ? null : structure.name(petItem),
				unlocks.missing(activity)));
			prospects.add(prospect(structure, activity, eval, rates, unlocks.missing(activity)));
		}
		// What can be done now comes first, each part ranked by the wait for a new slot.
		hunts.sort(Comparator.comparing((Report.Hunt h) -> h.getLockedBy() != null)
			.thenComparingDouble(Report.Hunt::getHoursToNext));
		return hunts;
	}

	/**
	 * Every missing pet as a hunt at its quickest source the account can do, or its quickest
	 * source at all when it can do none yet. Pets it can hunt now come first, quickest first.
	 */
	private List<PetPlan> pets(LogStructure structure, Set<Integer> unique, Set<Integer> owned, Rates rates, Unlocks unlocks)
	{
		List<PetPlan> plans = new ArrayList<>();
		for (int id : unique)
		{
			if (!data.isPet(id) || owned.contains(id))
			{
				continue;
			}
			Activity.Drop best = null;
			boolean bestOpen = false;
			double bestHours = Double.POSITIVE_INFINITY;
			for (Activity.Drop drop : data.dropsOf(id))
			{
				Activity activity = data.activity(drop.getActivityIndex());
				double hours = rates.hoursFor(activity, drop.getAttempts());
				boolean open = unlocks.missing(activity) == null;
				if (Double.isFinite(hours) && (best == null || open && !bestOpen || open == bestOpen && hours < bestHours))
				{
					best = drop;
					bestOpen = open;
					bestHours = hours;
				}
			}
			if (best == null)
			{
				continue;
			}
			Activity activity = data.activity(best.getActivityIndex());
			String locked = unlocks.missing(activity);
			double attempts = Math.max(1, best.getAttempts());
			Prospect hunt = new Prospect(activity.getName(), activity.getCategory(), rates.perHour(activity),
				activity.getSetupHours(), rates.isOwnPace(activity), locked, id, structure.name(id),
				best.isExact() ? new double[0] : new double[]{attempts}, best.isExact() ? new int[0] : new int[]{1},
				best.isExact() ? new double[]{attempts} : new double[0], java.util.Collections.singletonList(id));
			Eval rest = evaluate(activity, owned, other -> other != id && unique.contains(other));
			Prospect alongside = prospect(structure, activity, rest, rates, locked);
			List<LogStructure.Page> pages = structure.pagesOf(id);
			plans.add(new PetPlan(id, structure.name(id), pages.isEmpty() ? null : pages.get(0).getName(), hunt, alongside,
				1 / attempts));
		}
		plans.sort(Comparator.comparing((PetPlan p) -> p.getLockedBy() != null).thenComparingDouble(PetPlan::getMedian));
		return plans;
	}

	/** When these missing slots of one page are likely all owned, each hunted at its fastest source in turn. */
	private Projection projection(LogStructure structure, List<Integer> missing, Set<Integer> owned, Rates rates,
		Unlocks unlocks)
	{
		if (missing.isEmpty())
		{
			return null;
		}
		Map<Integer, Set<Integer>> byActivity = new LinkedHashMap<>();
		int unrated = 0;
		for (int id : missing)
		{
			Activity.Drop fastest = fastestOpen(id, rates, unlocks);
			if (fastest == null)
			{
				unrated++;
				continue;
			}
			byActivity.computeIfAbsent(fastest.getActivityIndex(), k -> new HashSet<>()).add(id);
		}
		// Evaluations are shared between reports for the same shape, so the same evaluations at the same rates
		// give the same curve: a new kill count reuses it, and only a new slot or a new pace works it out again.
		List<Object> key = new ArrayList<>();
		key.add(unrated);
		List<Activity> activities = new ArrayList<>();
		List<Eval> evals = new ArrayList<>();
		for (Map.Entry<Integer, Set<Integer>> e : byActivity.entrySet())
		{
			Activity activity = data.activity(e.getKey());
			Eval eval = evaluate(activity, owned, e.getValue()::contains);
			activities.add(activity);
			evals.add(eval);
			key.add(eval);
			key.add(rates.perHour(activity));
			key.add(String.valueOf(unlocks.missing(activity)));
		}
		Projection cached = projections.get(key);
		if (cached != null || projections.containsKey(key))
		{
			return cached;
		}
		List<Prospect> parts = new ArrayList<>();
		for (int i = 0; i < activities.size(); i++)
		{
			parts.add(prospect(structure, activities.get(i), evals.get(i), rates, unlocks.missing(activities.get(i))));
		}
		Projection projection = Projection.of(parts, unrated);
		if (projections.size() > MAX_CURVES)
		{
			projections.clear();
		}
		projections.put(key, projection);
		return projection;
	}

	/**
	 * The quickest source of a slot that the account can do now, or its quickest source at all when it can do
	 * none yet, so a page is only shown as locked when every way to finish it is.
	 */
	private Activity.Drop fastestOpen(int itemId, Rates rates, Unlocks unlocks)
	{
		Activity.Drop fastest = rates.fastest(itemId);
		if (fastest == null || unlocks.missing(data.activity(fastest.getActivityIndex())) == null)
		{
			return fastest;
		}
		Activity.Drop best = null;
		double bestHours = Double.POSITIVE_INFINITY;
		for (Activity.Drop drop : data.dropsOf(itemId))
		{
			Activity activity = data.activity(drop.getActivityIndex());
			double hours = rates.hoursFor(activity, drop.getAttempts());
			if (hours < bestHours && unlocks.missing(activity) == null)
			{
				bestHours = hours;
				best = drop;
			}
		}
		return best == null ? fastest : best;
	}

	private Prospect prospect(LogStructure structure, Activity activity, Eval eval, Rates rates, String lockedBy)
	{
		return new Prospect(activity.getName(), activity.getCategory(), rates.perHour(activity), activity.getSetupHours(),
			rates.isOwnPace(activity), lockedBy, eval.nextItem, eval.nextItem < 0 ? null : structure.name(eval.nextItem),
			eval.means, eval.stages, eval.exact, eval.missing);
	}

	/** Hours to finish these slots, each hunted at its fastest source. */
	private Finish finish(List<Integer> missing, Set<Integer> owned, Rates rates)
	{
		Map<Integer, Set<Integer>> byActivity = new LinkedHashMap<>();
		int unrated = 0;
		for (int id : missing)
		{
			Activity.Drop fastest = rates.fastest(id);
			if (fastest == null)
			{
				unrated++;
				continue;
			}
			byActivity.computeIfAbsent(fastest.getActivityIndex(), k -> new HashSet<>()).add(id);
		}
		double hours = 0;
		for (Map.Entry<Integer, Set<Integer>> e : byActivity.entrySet())
		{
			Activity activity = data.activity(e.getKey());
			Eval eval = evaluate(activity, owned, e.getValue()::contains);
			hours += rates.hoursFor(activity, eval.allAttempts);
		}
		return new Finish(byActivity.isEmpty() ? (unrated > 0 ? Double.POSITIVE_INFINITY : 0) : hours, unrated);
	}

	/** The soonest new slot among these, from any activity that drops one. */
	private Next next(List<Integer> missing, Set<Integer> owned, Rates rates)
	{
		Set<Integer> wanted = new HashSet<>(missing);
		Set<Integer> activities = new LinkedHashSet<>();
		for (int id : missing)
		{
			for (Activity.Drop drop : data.dropsOf(id))
			{
				activities.add(drop.getActivityIndex());
			}
		}
		double best = Double.POSITIVE_INFINITY;
		String source = null;
		for (int index : activities)
		{
			Activity activity = data.activity(index);
			if (rates.perHour(activity) <= 0)
			{
				continue;
			}
			Eval eval = evaluate(activity, owned, wanted::contains);
			double hours = rates.hoursFor(activity, eval.nextAttempts);
			if (hours < best)
			{
				best = hours;
				source = activity.getName();
			}
		}
		return new Next(best, source);
	}

	/**
	 * Walks an activity's table in order. A slot can roll when it is missing and, if it
	 * only drops after the slot before it, that slot is owned. Slots earned outright are
	 * paid for one by one, so their counts add up; rolled slots wait side by side, and a
	 * run of rolled slots that only drop in order waits one piece after another.
	 */
	private Eval evaluate(Activity activity, Set<Integer> owned, java.util.function.IntPredicate include)
	{
		// The result depends only on which slots are wanted and which are owned, so it is kept between
		// reports: a new kill count costs nothing here, only a new slot does.
		StringBuilder key = new StringBuilder().append(activity.getIndex());
		boolean previousOwned = false;
		for (Activity.Drop drop : activity.getDrops())
		{
			int id = drop.getItemId();
			boolean has = owned.contains(id);
			if (!has && include.test(id))
			{
				key.append(drop.isSequential() && previousOwned ? ',' : ';').append(id);
			}
			previousOwned = has;
		}
		String cacheKey = key.toString();
		Eval cached = evaluations.get(cacheKey);
		if (cached != null)
		{
			return cached;
		}
		Eval eval = compute(activity, owned, include);
		if (evaluations.size() > MAX_CACHED)
		{
			// Curves hold on to evaluations by identity, so they go with them.
			evaluations.clear();
			projections.clear();
		}
		evaluations.put(cacheKey, eval);
		return eval;
	}

	private Eval compute(Activity activity, Set<Integer> owned, java.util.function.IntPredicate include)
	{
		Eval eval = new Eval();
		double chanceSum = 0;
		double exactNext = Double.POSITIVE_INFINITY;
		int exactItem = -1;
		double bestChance = 0;
		double fixed = 0;
		List<Double> exactCosts = new ArrayList<>();
		List<double[]> runs = new ArrayList<>();
		boolean previousOwned = false;
		boolean previousRolledOpen = false;
		for (Activity.Drop drop : activity.getDrops())
		{
			int id = drop.getItemId();
			boolean has = owned.contains(id);
			boolean rolledOpen = false;
			if (!has && include.test(id))
			{
				eval.missing.add(id);
				boolean canRoll = !drop.isSequential() || previousOwned;
				double attempts = Math.max(1, drop.getAttempts());
				if (drop.isExact())
				{
					fixed += attempts;
					exactCosts.add(attempts);
					if (canRoll && attempts < exactNext)
					{
						exactNext = attempts;
						exactItem = id;
					}
				}
				else
				{
					if (canRoll)
					{
						double chance = 1 / attempts;
						chanceSum += chance;
						if (chance > bestChance)
						{
							bestChance = chance;
							eval.nextItem = id;
						}
					}
					if (drop.isSequential() && previousRolledOpen && !runs.isEmpty())
					{
						double[] run = runs.get(runs.size() - 1);
						run[0] += attempts;
						run[1]++;
					}
					else
					{
						runs.add(new double[]{attempts, 1});
					}
					rolledOpen = true;
				}
			}
			previousRolledOpen = rolledOpen;
			previousOwned = has;
		}
		if (exactItem >= 0 && (eval.nextItem < 0 || exactNext < 1 / chanceSum))
		{
			eval.nextItem = exactItem;
			eval.nextExact = true;
			bestChance = 1 / exactNext;
		}
		double[] means = new double[runs.size()];
		int[] stages = new int[runs.size()];
		for (int i = 0; i < means.length; i++)
		{
			means[i] = runs.get(i)[0];
			stages[i] = (int) runs.get(i)[1];
		}
		eval.means = means;
		eval.stages = stages;
		eval.exact = new double[exactCosts.size()];
		for (int i = 0; i < eval.exact.length; i++)
		{
			eval.exact[i] = exactCosts.get(i);
		}
		eval.remaining = eval.missing.size();
		eval.nextChance = bestChance;
		eval.nextAttempts = eval.remaining == 0 ? Double.POSITIVE_INFINITY : Odds.attemptsToNext(chanceSum, exactNext);
		double all = Odds.attemptsToAll(fixed, means, stages);
		eval.allAttempts = eval.remaining == 0 ? 0
			: Double.isFinite(eval.nextAttempts) ? Math.max(eval.nextAttempts, all) : all;
		return eval;
	}

	/** Completions per hour for each activity: your own pace where it is known, the typical rate otherwise. */
	private final class Rates
	{
		private final Progress progress;
		private final boolean iron;
		private final boolean ownPace;
		private final Map<Integer, Activity.Drop> fastest = new HashMap<>();

		Rates(Progress progress, boolean iron, boolean ownPace)
		{
			this.progress = progress;
			this.iron = iron;
			this.ownPace = ownPace;
		}

		boolean isOwnPace(Activity activity)
		{
			if (!ownPace)
			{
				return false;
			}
			String label = activityLabels.get(activity.getIndex());
			Progress.Pace pace = label == null ? null : progress.pace(label);
			return pace != null && pace.getSamples() >= PACE_SAMPLES && pace.perHour() > 0;
		}

		double perHour(Activity activity)
		{
			if (isOwnPace(activity))
			{
				return progress.pace(activityLabels.get(activity.getIndex())).perHour();
			}
			return activity.perHour(iron);
		}

		double hours(Activity activity, double attempts)
		{
			return hoursFor(activity, attempts);
		}

		double hoursFor(Activity activity, double attempts)
		{
			double perHour = perHour(activity);
			if (perHour <= 0 || Double.isInfinite(attempts))
			{
				return Double.POSITIVE_INFINITY;
			}
			return attempts / perHour + activity.getSetupHours();
		}

		/** The source that gives this slot soonest on its own, or null. */
		Activity.Drop fastest(int itemId)
		{
			if (fastest.containsKey(itemId))
			{
				return fastest.get(itemId);
			}
			Activity.Drop best = null;
			double bestHours = Double.POSITIVE_INFINITY;
			for (Activity.Drop drop : data.dropsOf(itemId))
			{
				double hours = hoursFor(data.activity(drop.getActivityIndex()), drop.getAttempts());
				if (hours < bestHours)
				{
					bestHours = hours;
					best = drop;
				}
			}
			fastest.put(itemId, best);
			return best;
		}
	}

	private static final class Eval
	{
		final List<Integer> missing = new ArrayList<>();
		int remaining;
		int nextItem = -1;
		/** The next slot is earned after a fixed number of attempts rather than rolled. */
		boolean nextExact;
		double nextChance;
		double nextAttempts;
		double allAttempts;
		double[] means = new double[0];
		int[] stages = new int[0];
		double[] exact = new double[0];
	}

	private static final class Finish
	{
		final double hours;
		final int unrated;

		Finish(double hours, int unrated)
		{
			this.hours = hours;
			this.unrated = unrated;
		}
	}

	private static final class Next
	{
		final double hours;
		final String source;

		Next(double hours, String source)
		{
			this.hours = hours;
			this.source = source;
		}
	}
}
