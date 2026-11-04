package com.codex.loginsight.core;

import java.util.ArrayList;
import java.util.List;

/** One raid's log read for its page: the dry spell in expected purples, your luck and the latest raids. */
public final class RaidReport
{
	private final Raids.Kind kind;
	private final RaidLog.Track track;
	private final List<RaidLog.Run> recent;
	private final Luck luck;
	/** Your average chance per raid over the recent raids with a known chance, or -1. */
	private final double average;
	/** True when any recent raid's share was assumed. */
	private final boolean estimates;

	private RaidReport(Raids.Kind kind, RaidLog.Track track)
	{
		this.kind = kind;
		this.track = track;
		this.recent = track == null ? new ArrayList<>() : track.getRecent();
		List<Double> chances = new ArrayList<>();
		int owned = 0;
		boolean assumed = false;
		for (RaidLog.Run run : recent)
		{
			if (run.getChance() >= 0)
			{
				chances.add(run.getChance());
				if (run.getPurple() != 0)
				{
					owned++;
				}
				assumed |= run.isEstimate();
			}
		}
		double[] array = new double[chances.size()];
		double sum = 0;
		for (int i = 0; i < array.length; i++)
		{
			array[i] = chances.get(i);
			sum += array[i];
		}
		this.average = array.length == 0 ? -1 : sum / array.length;
		this.luck = array.length >= 5 ? Luck.of(array, owned) : null;
		this.estimates = assumed;
	}

	public static RaidReport of(RaidLog log, Raids.Kind kind)
	{
		return new RaidReport(kind, log == null ? null : log.track(kind));
	}

	public Raids.Kind getKind()
	{
		return kind;
	}

	public boolean isEmpty()
	{
		return track == null || track.getRuns() == 0 && track.getPurples() == 0;
	}

	public int getRuns()
	{
		return track == null ? 0 : track.getRuns();
	}

	public int getPurples()
	{
		return track == null ? 0 : track.getPurples();
	}

	public int getCounted()
	{
		return track == null ? 0 : track.getCounted();
	}

	public double getExpected()
	{
		return track == null ? 0 : track.getExpected();
	}

	public int getDryRuns()
	{
		return track == null ? 0 : track.getDryRuns();
	}

	public double getDryExpected()
	{
		return track == null ? 0 : track.getDryExpected();
	}

	public double getDryWaiting()
	{
		return track == null ? 1 : track.getDryWaiting();
	}

	public int getDryUnknown()
	{
		return track == null ? 0 : track.getDryUnknown();
	}

	public int getLastPurple()
	{
		return track == null ? 0 : track.getLastPurple();
	}

	public long getLastPurpleAt()
	{
		return track == null ? 0 : track.getLastPurpleAt();
	}

	public double getLastTook()
	{
		return track == null ? -1 : track.getLastTook();
	}

	/** Newest first. */
	public List<RaidLog.Run> getRecent()
	{
		return new ArrayList<>(recent);
	}

	/** Your luck over the recent raids with a known chance, or null with fewer than five. */
	public Luck getLuck()
	{
		return luck;
	}

	public double getAverage()
	{
		return average;
	}

	public boolean hasEstimates()
	{
		return estimates;
	}

	/** The latest raid, or null. */
	public RaidLog.Run latest()
	{
		return recent.isEmpty() ? null : recent.get(0);
	}
}
