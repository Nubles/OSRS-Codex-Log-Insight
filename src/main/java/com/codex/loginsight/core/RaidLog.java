package com.codex.loginsight.core;

import java.util.ArrayList;
import java.util.List;

/**
 * This account's raids, saved per RuneScape profile: every completed Chambers of Xeric, Theatre of Blood and Tombs of
 * Amascut with the points, raid level and team size the game gave at its end, the purple chance they made, and
 * whether a purple came to you. Plain fields so Gson can save it as it is.
 *
 * <p>Dryness is kept in expected purples: the sum of each raid's chance since your last purple, so a run of big-team
 * raids at low points weighs what it is worth rather than counting like solo raids.
 *
 * <p>A raid is logged at its kill count line and stays open for a short while, since the Tombs of Amascut send your
 * points only as the raid ends and the chest is opened after; it is counted once its points arrive, its chest is
 * read, or the wait runs out.
 */
public final class RaidLog
{
	/** Raids kept per raid for the strip, the list and the luck read. */
	static final int MAX_RECENT = 200;
	/** A purple or late points this long after a raid's kill count line still belong to it. */
	static final long WINDOW_MS = 30 * 60_000L;

	private List<Track> tracks = new ArrayList<>();

	/** One raid's log. */
	public static final class Track
	{
		private String kind;
		private long since;
		private int runs;
		private int purples;
		/** Raids with a known chance, and the purples you would have from them on average. */
		private int counted;
		private double expected;
		/** Since your last purple: raids, the purples expected from them, and the log of the chance of none. */
		private int dryRuns;
		private double dryExpected;
		private double dryMissLog;
		private int dryUnknown;
		/** When your last purple came, and what. */
		private long lastPurpleAt;
		private int lastPurple;
		/** Purples expected since the one before the last purple, when it came: how long that one took. */
		private double lastTook = -1;
		/** Newest first. */
		private List<Run> recent = new ArrayList<>();

		Track(String kind)
		{
			this.kind = kind;
		}

		public Raids.Kind getKind()
		{
			return Raids.Kind.ofKey(kind);
		}

		public long getSince()
		{
			return since;
		}

		public int getRuns()
		{
			return runs;
		}

		public int getPurples()
		{
			return purples;
		}

		public int getCounted()
		{
			return counted;
		}

		public double getExpected()
		{
			return expected;
		}

		public int getDryRuns()
		{
			return dryRuns;
		}

		public double getDryExpected()
		{
			return dryExpected;
		}

		/** The share of players still without a purple after the raids since your last: 1 when none were counted. */
		public double getDryWaiting()
		{
			return Math.exp(dryMissLog);
		}

		public int getDryUnknown()
		{
			return dryUnknown;
		}

		public long getLastPurpleAt()
		{
			return lastPurpleAt;
		}

		public int getLastPurple()
		{
			return lastPurple;
		}

		public double getLastTook()
		{
			return lastTook;
		}

		public List<Run> getRecent()
		{
			return new ArrayList<>(recent);
		}

		Track repair()
		{
			if (recent == null)
			{
				recent = new ArrayList<>();
			}
			recent.removeIf(r -> r == null);
			if (!Double.isFinite(expected) || expected < 0)
			{
				expected = 0;
			}
			if (!Double.isFinite(dryExpected) || dryExpected < 0)
			{
				dryExpected = 0;
			}
			if (!Double.isFinite(dryMissLog) || dryMissLog > 0)
			{
				dryMissLog = 0;
			}
			return this;
		}
	}

	/** One completed raid. */
	public static final class Run
	{
		private long at;
		private String mode;
		private int kc;
		/** Players in the raid, or 0 when not read. */
		private int team;
		/** Your points and the team's, or -1 when the game did not give them. */
		private long personal = -1;
		private long total = -1;
		/** The Tombs of Amascut raid level, or -1. */
		private int level = -1;
		/** Your chance of a purple and the purples expected, or -1 when not known; the team's chance, or -1. */
		private double chance = -1;
		private double expected = -1;
		private double teamChance = -1;
		/** True when your share of the team's chance was assumed. */
		private boolean estimate;
		/** The purple that came to you from this raid, or 0. */
		private int purple;
		/** True once the raid is in the totals. */
		private boolean counted;

		Run(long at, String mode, int kc, int team)
		{
			this.at = at;
			this.mode = mode;
			this.kc = kc;
			this.team = team;
		}

		public long getAt()
		{
			return at;
		}

		public String getMode()
		{
			return mode;
		}

		public int getKc()
		{
			return kc;
		}

		public int getTeam()
		{
			return team;
		}

		public long getPersonal()
		{
			return personal;
		}

		public long getTotal()
		{
			return total;
		}

		public int getLevel()
		{
			return level;
		}

		public double getChance()
		{
			return chance;
		}

		public double getExpected()
		{
			return expected;
		}

		public double getTeamChance()
		{
			return teamChance;
		}

		public boolean isEstimate()
		{
			return estimate;
		}

		public int getPurple()
		{
			return purple;
		}

		public boolean isCounted()
		{
			return counted;
		}
	}

	/** A purple put down to a raid: the chance that raid gave and how dry you were before it. */
	public static final class Find
	{
		private final Raids.Kind kind;
		private final double chance;
		/** Purples expected from the raids before it since the last one. */
		private final double took;
		/** The share of players who would have had one sooner. */
		private final double sooner;

		Find(Raids.Kind kind, double chance, double took, double sooner)
		{
			this.kind = kind;
			this.chance = chance;
			this.took = took;
			this.sooner = sooner;
		}

		public Raids.Kind getKind()
		{
			return kind;
		}

		/** That raid's chance for you, or -1 when unknown. */
		public double getChance()
		{
			return chance;
		}

		public double getTook()
		{
			return took;
		}

		public double getSooner()
		{
			return sooner;
		}
	}

	public RaidLog repair()
	{
		if (tracks == null)
		{
			tracks = new ArrayList<>();
		}
		tracks.removeIf(t -> t == null || t.getKind() == null);
		for (Track t : tracks)
		{
			t.repair();
		}
		return this;
	}

	public static SavedJson.Read<RaidLog> load(com.google.gson.Gson gson, String json)
	{
		SavedJson.Read<RaidLog> read = SavedJson.read(gson, json, RaidLog.class);
		RaidLog value = read.getValue();
		return new SavedJson.Read<>(value == null ? null : value.repair(), read.isDamaged());
	}

	/** This raid's log, or null when none has been logged. */
	public Track track(Raids.Kind kind)
	{
		for (Track t : tracks)
		{
			if (t.getKind() == kind)
			{
				return t;
			}
		}
		return null;
	}

	private Track trackFor(Raids.Kind kind, long now)
	{
		Track t = track(kind);
		if (t == null)
		{
			t = new Track(kind.getKey());
			t.since = now;
			tracks.add(t);
		}
		return t;
	}

	public boolean isEmpty()
	{
		for (Track t : tracks)
		{
			if (t.runs > 0 || t.purples > 0)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * A raid's kill count line: the raid is logged with what the game gave at its end. A raid of the same kind still
	 * open is counted first.
	 *
	 * @param personal your points, or -1
	 * @param total the team's points, or -1
	 * @param level the Tombs of Amascut raid level, or -1
	 */
	public Run completed(Raids.Kind kind, String mode, int kc, int team, long personal, long total, int level, long now)
	{
		Track t = trackFor(kind, now);
		Run open = t.recent.isEmpty() ? null : t.recent.get(0);
		if (open != null && !open.counted)
		{
			if (kc > 0 && open.kc == kc && mode != null && mode.equals(open.mode))
			{
				// The same line twice.
				return open;
			}
			count(t, open);
		}
		Run run = new Run(now, mode, kc, team);
		run.personal = personal;
		run.total = total;
		run.level = level;
		price(kind, run);
		t.runs++;
		t.recent.add(0, run);
		while (t.recent.size() > MAX_RECENT)
		{
			t.recent.remove(t.recent.size() - 1);
		}
		if (run.chance >= 0 || kind != Raids.Kind.TOA)
		{
			// Only the Tombs send points late; every other raid has all it will get.
			count(t, run);
		}
		return run;
	}

	/** Your Tombs of Amascut points arrived as the raid ended: the open raid is priced and counted. */
	public boolean points(Raids.Kind kind, long personal, long now)
	{
		Track t = track(kind);
		Run run = t == null || t.recent.isEmpty() ? null : t.recent.get(0);
		if (run == null || run.counted || personal < 0 || now - run.at > WINDOW_MS)
		{
			return false;
		}
		run.personal = personal;
		if (run.team == 1)
		{
			run.total = personal;
		}
		price(kind, run);
		count(t, run);
		return true;
	}

	/** Raids left open past the window are counted with what they have. */
	public boolean settle(long now)
	{
		boolean changed = false;
		for (Track t : tracks)
		{
			Run run = t.recent.isEmpty() ? null : t.recent.get(0);
			if (run != null && !run.counted && now - run.at > WINDOW_MS)
			{
				count(t, run);
				changed = true;
			}
		}
		return changed;
	}

	private static void price(Raids.Kind kind, Run run)
	{
		Raids.Chance c;
		switch (kind)
		{
			case COX:
				c = Raids.cox(run.personal, run.total);
				break;
			case TOB:
				c = Raids.tob(run.mode, run.team);
				break;
			default:
				c = Raids.toa(run.personal, run.level, run.team);
				break;
		}
		run.chance = c.getYours();
		run.expected = c.getExpected();
		run.teamChance = c.getTeam();
		run.estimate = c.isEstimate();
	}

	private static void count(Track t, Run run)
	{
		if (run.counted)
		{
			return;
		}
		run.counted = true;
		if (run.chance >= 0)
		{
			t.counted++;
			t.expected += run.expected;
		}
		if (run.purple != 0)
		{
			// Its purple already reset the dry spell; the raid itself is part of the one it ended.
			return;
		}
		t.dryRuns++;
		if (run.chance >= 0)
		{
			t.dryExpected += run.expected;
			t.dryMissLog += Math.log1p(-Math.min(0.999999, run.chance));
		}
		else
		{
			t.dryUnknown++;
		}
	}

	/**
	 * A purple came to you: from the raid's reward chest, or a new collection log slot. It is put down to the latest
	 * raid of its kind within the window; the same purple seen twice counts once.
	 *
	 * @return the find, or null when the item is not a raid's purple or was already counted
	 */
	public Find found(int itemId, long now)
	{
		Raids.Kind kind = Raids.uniqueOf(itemId);
		if (kind == null)
		{
			return null;
		}
		Track t = trackFor(kind, now);
		Run run = t.recent.isEmpty() ? null : t.recent.get(0);
		if (run != null && now - run.at > WINDOW_MS)
		{
			run = null;
		}
		if (run != null && run.purple != 0)
		{
			// Already seen (the chest, then the log's "new item" line).
			return null;
		}
		if (run == null && t.lastPurple == itemId && now - t.lastPurpleAt <= WINDOW_MS)
		{
			return null;
		}
		double took = t.dryExpected;
		double sooner = -Math.expm1(t.dryMissLog);
		double chance = -1;
		if (run != null)
		{
			run.purple = itemId;
			if (run.counted)
			{
				// Counted as dry a moment ago: it is the raid that ended the spell instead.
				t.dryRuns--;
				if (run.chance >= 0)
				{
					t.dryExpected -= run.expected;
					t.dryMissLog -= Math.log1p(-Math.min(0.999999, run.chance));
					took = t.dryExpected;
					sooner = -Math.expm1(Math.min(0, t.dryMissLog));
				}
				else
				{
					t.dryUnknown--;
				}
			}
			else
			{
				count(t, run);
			}
			chance = run.chance;
			if (run.chance >= 0)
			{
				took += run.expected;
			}
		}
		t.purples++;
		t.lastPurple = itemId;
		t.lastPurpleAt = now;
		t.lastTook = took;
		t.dryRuns = 0;
		t.dryExpected = 0;
		t.dryMissLog = 0;
		t.dryUnknown = 0;
		return new Find(kind, chance, took, Math.max(0, sooner));
	}
}
