package com.codex.loginsight.core;

import lombok.Getter;

/** How a set of slots compares with everyone else at the same counts. */
@Getter
public final class Luck
{
	public enum Verdict
	{
		PARCHED("Parched"),
		DRY("Dry"),
		EVEN("Even"),
		FORTUNATE("Fortunate"),
		BLESSED("Blessed");

		@Getter
		private final String label;

		Verdict(String label)
		{
			this.label = label;
		}
	}

	/** 0 is the driest account possible, 1 the luckiest. */
	private final double percentile;
	/** Slots an average player would own by now. */
	private final double expected;
	private final int owned;
	/** How many slots the read covers. */
	private final int slots;

	public Luck(double percentile, double expected, int owned, int slots)
	{
		this.percentile = percentile;
		this.expected = expected;
		this.owned = owned;
		this.slots = slots;
	}

	public static Luck of(double[] chances, int owned)
	{
		double expected = 0;
		for (double c : chances)
		{
			expected += c;
		}
		return new Luck(Odds.luckPercentile(chances, owned), expected, owned, chances.length);
	}

	public Verdict verdict()
	{
		return verdict(percentile);
	}

	public static Verdict verdict(double percentile)
	{
		if (percentile >= 0.85)
		{
			return Verdict.BLESSED;
		}
		if (percentile >= 0.65)
		{
			return Verdict.FORTUNATE;
		}
		if (percentile > 0.35)
		{
			return Verdict.EVEN;
		}
		return percentile > 0.15 ? Verdict.DRY : Verdict.PARCHED;
	}

	/** Whole percent of players this account is luckier than. */
	public int luckierThan()
	{
		return (int) Math.round(percentile * 100);
	}
}
