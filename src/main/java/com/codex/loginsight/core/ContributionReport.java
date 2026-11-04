package com.codex.loginsight.core;

import java.util.ArrayList;
import java.util.List;

/** One shared boss's contribution log read for its page: dryness in expected uniques, each unique, and averages. */
public final class ContributionReport
{
	private final Contribution.Boss boss;
	private final ContributionLog.Track track;
	private final List<ContributionLog.Kill> recent;
	private final Luck luck;
	private final boolean estimates;

	/** One unique's odds over the logged kills. */
	public static final class Odds
	{
		private final Contribution.Unique unique;
		private final double expected;
		private final double waiting;
		private final int got;

		Odds(Contribution.Unique unique, double expected, double waiting, int got)
		{
			this.unique = unique;
			this.expected = expected;
			this.waiting = waiting;
			this.got = got;
		}

		public Contribution.Unique getUnique()
		{
			return unique;
		}

		/** Copies you would have on average from your logged kills. */
		public double getExpected()
		{
			return expected;
		}

		/** The share of players still without it after your logged kills. */
		public double getWaiting()
		{
			return waiting;
		}

		/** Copies seen since the log began. */
		public int getGot()
		{
			return got;
		}

		/** Copies got over the copies expected: 1 is par, above is lucky; -1 with nothing expected. */
		public double getRatio()
		{
			return expected <= 0 ? -1 : got / expected;
		}
	}

	private ContributionReport(Contribution.Boss boss, ContributionLog.Track track)
	{
		this.boss = boss;
		this.track = track;
		this.recent = track == null ? new ArrayList<>() : track.getRecent();
		List<Double> chances = new ArrayList<>();
		int owned = 0;
		boolean assumed = false;
		for (ContributionLog.Kill kill : recent)
		{
			chances.add(kill.getChance());
			if (kill.getUnique() != 0)
			{
				owned++;
			}
			assumed |= kill.isEstimate();
		}
		double[] array = new double[chances.size()];
		for (int i = 0; i < array.length; i++)
		{
			array[i] = chances.get(i);
		}
		this.luck = array.length >= 5 ? Luck.of(array, owned) : null;
		this.estimates = assumed;
	}

	public static ContributionReport of(ContributionLog log, Contribution.Boss boss)
	{
		return new ContributionReport(boss, log == null ? null : log.track(boss));
	}

	public Contribution.Boss getBoss()
	{
		return boss;
	}

	public boolean isEmpty()
	{
		return track == null || track.getKills() == 0 && track.getUniques() == 0;
	}

	public int getKills()
	{
		return track == null ? 0 : track.getKills();
	}

	public int getCredited()
	{
		return track == null ? 0 : track.getCredited();
	}

	public int getUniques()
	{
		return track == null ? 0 : track.getUniques();
	}

	public double getExpected()
	{
		return track == null ? 0 : track.getExpected();
	}

	/** Your average share of a kill, or -1 with no kills. */
	public double getAverageShare()
	{
		return track == null || track.getKills() == 0 ? -1 : track.getShareSum() / track.getKills();
	}

	/** Your average chance of a unique a kill, or -1 with no kills. */
	public double getAverageChance()
	{
		return track == null || track.getKills() == 0 ? -1 : track.getChanceSum() / track.getKills();
	}

	public int getDryKills()
	{
		return track == null ? 0 : track.getDryKills();
	}

	public double getDryExpected()
	{
		return track == null ? 0 : track.getDryExpected();
	}

	public double getDryWaiting()
	{
		return track == null ? 1 : track.getDryWaiting();
	}

	public int getLastUnique()
	{
		return track == null ? 0 : track.getLastUnique();
	}

	public long getLastUniqueAt()
	{
		return track == null ? 0 : track.getLastUniqueAt();
	}

	public double getLastTook()
	{
		return track == null ? -1 : track.getLastTook();
	}

	/** Each unique's odds over the logged kills, in the boss's order. */
	public List<Odds> getOdds()
	{
		List<Odds> out = new ArrayList<>();
		Contribution.Unique[] uniques = boss.getUniques();
		for (int i = 0; i < uniques.length; i++)
		{
			out.add(new Odds(uniques[i], track == null ? 0 : track.getUniqueExpected(i),
				track == null ? 1 : track.getUniqueWaiting(i), track == null ? 0 : track.getUniqueGot(i)));
		}
		return out;
	}

	/** Newest first. */
	public List<ContributionLog.Kill> getRecent()
	{
		return new ArrayList<>(recent);
	}

	/** Your luck over the recent kills, or null with fewer than five. */
	public Luck getLuck()
	{
		return luck;
	}

	public boolean hasEstimates()
	{
		return estimates;
	}

	public ContributionLog.Kill latest()
	{
		return recent.isEmpty() ? null : recent.get(0);
	}
}
