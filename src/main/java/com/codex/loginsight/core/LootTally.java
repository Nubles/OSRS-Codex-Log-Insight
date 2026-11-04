package com.codex.loginsight.core;

/**
 * What one monster has actually dropped for this account since Log Insight started watching its kills: how many kills
 * left loot, what that loot was worth at the price of the day it dropped, and how many kills have passed since the
 * last unique. Plain fields so Gson can save it inside {@link Progress} as it is; short names keep the save small.
 */
public final class LootTally
{
	/** Kills whose loot was seen. */
	private int k;
	/** The loot's worth, in coins, at the prices of the day each drop came. */
	private long v;
	/** Kills since the last unique, counted from the first kill seen. */
	private int s;
	/** The last unique seen, or 0 before one. */
	private int u;
	/** When the first kill was seen, and when the last unique was, in epoch millis. */
	private long t;
	private long ut;

	/**
	 * Adds one kill's loot.
	 *
	 * @param value    its worth in coins
	 * @param unique   a unique in it, or 0 when there was none
	 * @param now      epoch millis
	 */
	public void record(long value, int unique, long now)
	{
		if (k == 0 && t == 0)
		{
			t = now;
		}
		k++;
		v += Math.max(0, value);
		if (unique > 0)
		{
			u = unique;
			ut = now;
			s = 0;
		}
		else
		{
			s++;
		}
	}

	public int getKills()
	{
		return k;
	}

	public long getValue()
	{
		return v;
	}

	/** Average worth of a kill's loot, or -1 before any kill. */
	public double perKill()
	{
		return k <= 0 ? -1 : v / (double) k;
	}

	/** Kills since the last unique seen, or since the first kill seen when none has been. */
	public int getSinceUnique()
	{
		return s;
	}

	/** The last unique seen, or 0. */
	public int getLastUnique()
	{
		return u;
	}

	public long getSince()
	{
		return t;
	}

	public long getLastUniqueAt()
	{
		return ut;
	}

	/** False for a tally no game could have saved. */
	boolean sane()
	{
		return k > 0 && v >= 0 && s >= 0 && s <= k && u >= 0;
	}

	LootTally copy()
	{
		LootTally c = new LootTally();
		c.k = k;
		c.v = v;
		c.s = s;
		c.u = u;
		c.t = t;
		c.ut = ut;
		return c;
	}
}
