package com.codex.loginsight.core;

import lombok.Getter;

/**
 * A monster's drop table set beside what this account has actually had from it: the worth of its uniques a kill by
 * the table against your own, what all its loot has been worth a kill since Log Insight began watching, the uniques
 * still missing and how long you have gone without one.
 *
 * <p>A unique here is a collection log slot this monster rolls at 1 in 40 or rarer that is not a pet: the same line the
 * oracle draws between rare slots and common ones. Pets never land on the floor, so loot can never show one.
 */
@Getter
public final class LootLookup
{
	/** Where a dry streak was counted from. */
	public enum DrySince
	{
		/** The last unique Log Insight saw in this monster's loot. */
		SEEN,
		/** The last new slot Log Insight saw announced, at the kill count it came at. */
		FIND,
		/** Your whole kill count: you own none of its uniques. */
		NONE_OWNED,
		/** Only the kills watched so far, none with a unique: the streak is at least this long. */
		WATCHED,
		/** Nothing to count from yet. */
		UNKNOWN
	}

	/** The table's worth of uniques a kill at today's prices, or -1 when no unique has a price. */
	private final double tablePerKill;
	/** Your own: the copies the log counts of each unique over the kills it was read at, at today's prices; or -1. */
	private final double yoursPerKill;
	/** Uniques with a price, the ones both figures add up. */
	private final int valued;
	/** Uniques the game keeps no price for (untradeable): left out of both figures. */
	private final int unpriced;
	/** Every kill's loot seen since watching began: how many, and their average worth; -1 before any. */
	private final int watchedKills;
	private final double watchedPerKill;
	private final long watchedSince;
	/** Uniques on the table, and how many are still missing. */
	private final int uniques;
	private final int missing;
	/** Chance of any unique in one kill. */
	private final double uniqueChance;
	/** Kills since the last unique, or -1 when unknown. */
	private final int dryStreak;
	private final DrySince drySince;
	/** The unique the streak counts from, or null. */
	private final String lastUnique;

	LootLookup(double tablePerKill, double yoursPerKill, int valued, int unpriced, int watchedKills, double watchedPerKill,
		long watchedSince, int uniques, int missing, double uniqueChance, int dryStreak, DrySince drySince, String lastUnique)
	{
		this.tablePerKill = tablePerKill;
		this.yoursPerKill = yoursPerKill;
		this.valued = valued;
		this.unpriced = unpriced;
		this.watchedKills = watchedKills;
		this.watchedPerKill = watchedPerKill;
		this.watchedSince = watchedSince;
		this.uniques = uniques;
		this.missing = missing;
		this.uniqueChance = uniqueChance;
		this.dryStreak = dryStreak;
		this.drySince = drySince;
		this.lastUnique = lastUnique;
	}

	/**
	 * The share of players who would have had at least one unique in as many kills as your dry streak, or -1 when
	 * either is unknown.
	 */
	public double haveOneByNow()
	{
		double without = stillWithout();
		return without < 0 ? -1 : 1 - without;
	}

	/**
	 * The share of players who would still be without a unique after as many kills as your dry streak, or -1 when either
	 * is unknown. Kept apart from {@link #haveOneByNow} so a long streak never rounds to a certain 100%.
	 */
	public double stillWithout()
	{
		if (dryStreak < 0 || uniqueChance <= 0)
		{
			return -1;
		}
		return Math.max(Double.MIN_VALUE, Math.exp(dryStreak * Math.log1p(-Math.min(uniqueChance, 0.999999))));
	}

	/** Average kills between uniques, or infinity when none is rated. */
	public double killsPerUnique()
	{
		return uniqueChance <= 0 ? Double.POSITIVE_INFINITY : 1 / uniqueChance;
	}

	/** Your worth a kill over the table's, or -1 when either is unknown. */
	public double ratio()
	{
		return tablePerKill <= 0 || yoursPerKill < 0 ? -1 : yoursPerKill / tablePerKill;
	}
}
