package com.codex.loginsight.core;

import lombok.Getter;

/**
 * Every copy of a set of slots the log counts, held against the odds at your counts: luck that keeps meaning
 * something after a page is green, where owning a slot or not says nothing more.
 */
@Getter
public final class Drops
{
	/** Copies the log counts. */
	private final int got;
	/** Copies an average player would have had by these counts. */
	private final double expected;
	/** 0 is the driest account possible, 1 the luckiest. */
	private final double percentile;
	/** How many slots are counted. */
	private final int slots;

	public Drops(int got, double expected, int slots)
	{
		this.got = got;
		this.expected = expected;
		this.slots = slots;
		this.percentile = Odds.dropPercentile(expected, got);
	}

	public Luck.Verdict verdict()
	{
		return Luck.verdict(percentile);
	}

	/** Whole percent of players this account is luckier than, 1 to 99: no count of drops beats every player. */
	public int luckierThan()
	{
		return (int) Math.max(1, Math.min(99, Math.round(percentile * 100)));
	}

	/** The same reading as a luck dial shows it. */
	public Luck asLuck()
	{
		return new Luck(percentile, expected, got, slots);
	}
}
