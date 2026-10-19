package com.codex.loginsight.core;

import com.codex.loginsight.data.ItemOdds;
import com.codex.loginsight.data.LogData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * This play session: the kill counts you have added since logging in, the new slots they gave, and how that
 * compares with an average player doing the same kills. A long dry evening and a lucky hour both show up here,
 * where the whole log's luck barely moves.
 *
 * <p>Only slots you were missing when the session began count, and only the slots that dropped as you played are
 * counted as found, so a log read in mid-session never reads as a lucky night. A slot's chance this session is the
 * chance of it dropping within the kills you added; one guaranteed by a count you passed this session is certain.
 *
 * <p>A saved count can be stale (it was last read weeks ago), so a counter's first update in a session only sets
 * where it starts: a kill message that jumps from 100 to 301 counts as the one kill it is, and a page that shows
 * a higher count than was saved counts as none.
 */
public final class Session
{
	/** Where each counter stood when the session first saw it move. */
	private final Map<String, Integer> startCounters = new HashMap<>();
	private final Set<Integer> startOwned;
	private final long startedAt;

	public Session(Set<Integer> owned, long startedAt)
	{
		this.startOwned = new HashSet<>(owned);
		this.startedAt = startedAt;
	}

	/**
	 * Records a counter's new value. The first one in a session sets its starting point: one below the value for a
	 * kill message, the value itself for a page read or anything else.
	 */
	public void observe(String label, int value, boolean fromKill)
	{
		if (label != null && value >= 0)
		{
			startCounters.putIfAbsent(label, fromKill ? Math.max(0, value - 1) : value);
		}
	}

	public long getStartedAt()
	{
		return startedAt;
	}

	/** Kill counts added this session, by counter, largest first. */
	public List<Gain> gains(Map<String, Integer> counters)
	{
		List<Gain> gains = new ArrayList<>();
		for (Map.Entry<String, Integer> e : counters.entrySet())
		{
			Integer before = startCounters.get(e.getKey());
			if (before != null && e.getValue() != null && e.getValue() > before)
			{
				gains.add(new Gain(e.getKey(), e.getValue() - before));
			}
		}
		gains.sort((a, b) -> b.added != a.added ? Integer.compare(b.added, a.added) : a.label.compareTo(b.label));
		return gains;
	}

	/** The session so far, against the slots and counters as they are now. */
	public Summary read(LogData data, Progress now, long nowMs)
	{
		Map<String, Integer> counters = now.getCounters();
		Map<String, Integer> added = new HashMap<>();
		for (Gain gain : gains(counters))
		{
			added.put(gain.label, gain.added);
		}
		List<Progress.Find> finds = new ArrayList<>();
		Set<Integer> dropped = new HashSet<>();
		for (Progress.Find find : now.getFinds())
		{
			if (find.getAt() >= startedAt)
			{
				finds.add(find);
				dropped.add(find.getItemId());
			}
		}
		List<Double> chances = new ArrayList<>();
		int found = 0;
		for (int id : rated(data, added))
		{
			if (now.owns(id) && !dropped.contains(id))
			{
				// Owned, but it never dropped tonight: read in from the log, so owned before the session began.
				continue;
			}
			chances.add(chance(data.odds(id), added));
			found += dropped.contains(id) ? 1 : 0;
		}
		double[] array = new double[chances.size()];
		for (int i = 0; i < array.length; i++)
		{
			array[i] = chances.get(i);
		}
		Luck luck = array.length == 0 ? null : Luck.of(array, found);
		return new Summary(Math.max(0, nowMs - startedAt), gains(counters), finds, luck);
	}

	/** Slots missing at the start with odds on a counter that moved this session. */
	private List<Integer> rated(LogData data, Map<String, Integer> added)
	{
		List<Integer> ids = new ArrayList<>();
		if (added.isEmpty())
		{
			return ids;
		}
		for (Map.Entry<Integer, ItemOdds> e : data.allOdds().entrySet())
		{
			if (startOwned.contains(e.getKey()))
			{
				continue;
			}
			for (ItemOdds.Roll roll : e.getValue().getRolls())
			{
				if (added.containsKey(roll.getLabel()))
				{
					ids.add(e.getKey());
					break;
				}
			}
		}
		Collections.sort(ids);
		return ids;
	}

	/** The chance of a slot dropping within the kills added this session. */
	private double chance(ItemOdds odds, Map<String, Integer> added)
	{
		int g = odds.getGuaranteedBy();
		if (g > 0)
		{
			String main = odds.mainLabel();
			Integer before = startCounters.get(main);
			Integer more = added.get(main);
			if (before != null && more != null && before < g && before + more >= g)
			{
				return 1;
			}
		}
		double logMiss = 0;
		for (ItemOdds.Roll roll : odds.getRolls())
		{
			Integer more = added.get(roll.getLabel());
			if (more != null)
			{
				logMiss += roll.getRolls() * (double) more * Math.log1p(-Math.min(roll.getChance(), 0.999999));
			}
		}
		return 1 - Math.exp(logMiss);
	}

	/** Kill count added to one counter. */
	@Getter
	public static final class Gain
	{
		private final String label;
		private final int added;

		Gain(String label, int added)
		{
			this.label = label;
			this.added = added;
		}
	}

	/** What the panel shows of the session. */
	@Getter
	public static final class Summary
	{
		private final long millis;
		private final List<Gain> gains;
		/** New slots that dropped this session, newest first. */
		private final List<Progress.Find> finds;
		/** Luck over the slots this session's kills could have given, or null before any kills with known odds. */
		private final Luck luck;

		public Summary(long millis, List<Gain> gains, List<Progress.Find> finds, Luck luck)
		{
			this.millis = millis;
			this.gains = Collections.unmodifiableList(gains);
			this.finds = Collections.unmodifiableList(finds);
			this.luck = luck;
		}

		public int addedTotal()
		{
			int total = 0;
			for (Gain gain : gains)
			{
				total += gain.added;
			}
			return total;
		}

		public boolean isEmpty()
		{
			return gains.isEmpty() && finds.isEmpty();
		}
	}
}
