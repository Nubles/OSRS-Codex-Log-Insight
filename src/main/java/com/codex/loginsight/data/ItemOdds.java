package com.codex.loginsight.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * A slot's chance per kill count, possibly from several counters at once
 * (a gilded item can come from hard, elite or master caskets).
 */
@Getter
public final class ItemOdds
{
	private final List<Roll> rolls;
	/** Kill count at which the slot is guaranteed, or 0. */
	private final int guaranteedBy;
	/** Each successful roll gives one of the item, so the log's count of it can be held against these odds. */
	private final boolean countable;

	public ItemOdds(List<Roll> rolls, int guaranteedBy)
	{
		this(rolls, guaranteedBy, false);
	}

	public ItemOdds(List<Roll> rolls, int guaranteedBy, boolean countable)
	{
		this.rolls = Collections.unmodifiableList(rolls);
		this.guaranteedBy = guaranteedBy;
		this.countable = countable;
	}

	/**
	 * How many of the item an average player would have had by these counts, or -1 when none of its counters is
	 * known. Counters that are unknown count as zero.
	 */
	public double expectedCount(Map<String, Integer> counters)
	{
		boolean known = false;
		double expected = 0;
		for (Roll roll : rolls)
		{
			Integer count = counters.get(roll.label);
			if (count != null)
			{
				known = true;
				expected += roll.chance * roll.rolls * count;
			}
		}
		return known ? expected : -1;
	}

	/** The counter most of this slot's chance comes from, used to label it. */
	public String mainLabel()
	{
		Roll best = rolls.get(0);
		for (Roll roll : rolls)
		{
			if (roll.chancePerCount() > best.chancePerCount())
			{
				best = roll;
			}
		}
		return best.label;
	}

	/** Chance in one kill count of the main counter, for showing "1 in N". */
	public double mainChance()
	{
		String label = mainLabel();
		double miss = 1;
		for (Roll roll : rolls)
		{
			if (roll.label.equals(label))
			{
				miss *= Math.pow(1 - roll.chance, roll.rolls);
			}
		}
		return 1 - miss;
	}

	/**
	 * The chance of still not having this slot at these counts, or -1 when none of its
	 * counters is known. Counters that are unknown count as zero.
	 */
	public double dryChance(Map<String, Integer> counters)
	{
		boolean known = false;
		double logMiss = 0;
		int main = 0;
		for (Roll roll : rolls)
		{
			Integer count = counters.get(roll.label);
			if (count == null)
			{
				continue;
			}
			known = true;
			logMiss += roll.rolls * (double) count * Math.log1p(-Math.min(roll.chance, 0.999999));
			if (roll.label.equals(mainLabel()))
			{
				main = Math.max(main, count);
			}
		}
		if (!known)
		{
			return -1;
		}
		if (guaranteedBy > 0 && main >= guaranteedBy)
		{
			return 0;
		}
		// Never exactly 0 unless guaranteed: a dry streak of hundreds of thousands of kills underflows a double.
		return Math.max(Double.MIN_VALUE, Math.exp(logMiss));
	}

	@Getter
	public static final class Roll
	{
		private final String label;
		private final double chance;
		private final int rolls;

		public Roll(String label, double chance, int rolls)
		{
			this.label = label;
			this.chance = chance;
			this.rolls = rolls;
		}

		double chancePerCount()
		{
			return 1 - Math.pow(1 - chance, rolls);
		}
	}
}
