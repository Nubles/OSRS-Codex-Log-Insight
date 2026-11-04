package com.codex.loginsight.core;

import java.util.ArrayList;
import java.util.List;

/**
 * This account's kills at the shared bosses (Nex, the Corporeal Beast, the Royal Titans), saved per RuneScape profile:
 * your damage and share of each kill, the chance of each unique it gave you, and the uniques that came. Plain fields
 * so Gson can save it as it is.
 *
 * <p>Odds are added up per unique (the uniques you would have on average from your kills, and the share of players
 * who would have had one by now) and for any unique, where dryness is kept in expected uniques since your last one, so
 * a run of low-share kills weighs what it was worth rather than counting like solo kills.
 */
public final class ContributionLog
{
	/** Kills kept per boss for the strip and the list. */
	static final int MAX_RECENT = 200;
	/** A unique this long after a kill still belongs to it (the loot, then the log's new item line). */
	static final long WINDOW_MS = 5 * 60_000L;

	private List<Track> tracks = new ArrayList<>();

	/** One boss's log. */
	public static final class Track
	{
		private String boss;
		private long since;
		private int kills;
		/** Kills the game gave you (a kill count line). */
		private int credited;
		/** Shares added up over the kills, for the average. */
		private double shareSum;
		/** The uniques you would have on average from the logged kills, and the chance of a unique a kill added up. */
		private double expected;
		private double chanceSum;
		private int uniques;
		/** Per unique, in the boss's order: expected copies, the log of the chance of none, and copies seen. */
		private double[] uniqueExpected;
		private double[] uniqueMissLog;
		private int[] uniqueGot;
		/** Since your last unique: kills, the uniques expected from them, and the log of the chance of none. */
		private int dryKills;
		private double dryExpected;
		private double dryMissLog;
		private long lastUniqueAt;
		private int lastUnique;
		/** Expected uniques the last unique took, or -1. */
		private double lastTook = -1;
		/** Newest first. */
		private List<Kill> recent = new ArrayList<>();

		Track(String boss)
		{
			this.boss = boss;
		}

		public Contribution.Boss getBoss()
		{
			return Contribution.Boss.ofKey(boss);
		}

		public long getSince()
		{
			return since;
		}

		public int getKills()
		{
			return kills;
		}

		public int getCredited()
		{
			return credited;
		}

		public double getShareSum()
		{
			return shareSum;
		}

		public double getExpected()
		{
			return expected;
		}

		public double getChanceSum()
		{
			return chanceSum;
		}

		public int getUniques()
		{
			return uniques;
		}

		/** Expected copies of the unique at this index from the logged kills. */
		public double getUniqueExpected(int i)
		{
			return i >= 0 && i < uniqueExpected.length ? uniqueExpected[i] : 0;
		}

		/** The share of players still without this unique after the logged kills. */
		public double getUniqueWaiting(int i)
		{
			return i >= 0 && i < uniqueMissLog.length ? Math.exp(uniqueMissLog[i]) : 1;
		}

		public int getUniqueGot(int i)
		{
			return i >= 0 && i < uniqueGot.length ? uniqueGot[i] : 0;
		}

		public int getDryKills()
		{
			return dryKills;
		}

		public double getDryExpected()
		{
			return dryExpected;
		}

		/** The share of players still without a unique after the kills since your last. */
		public double getDryWaiting()
		{
			return Math.exp(dryMissLog);
		}

		public long getLastUniqueAt()
		{
			return lastUniqueAt;
		}

		public int getLastUnique()
		{
			return lastUnique;
		}

		public double getLastTook()
		{
			return lastTook;
		}

		public List<Kill> getRecent()
		{
			return new ArrayList<>(recent);
		}

		Track repair()
		{
			if (recent == null)
			{
				recent = new ArrayList<>();
			}
			recent.removeIf(k -> k == null);
			int n = getBoss() == null ? 0 : getBoss().getUniques().length;
			uniqueExpected = fit(uniqueExpected, n);
			uniqueMissLog = fit(uniqueMissLog, n);
			if (uniqueGot == null || uniqueGot.length != n)
			{
				int[] got = new int[n];
				if (uniqueGot != null)
				{
					System.arraycopy(uniqueGot, 0, got, 0, Math.min(n, uniqueGot.length));
				}
				uniqueGot = got;
			}
			for (int i = 0; i < n; i++)
			{
				if (!Double.isFinite(uniqueExpected[i]) || uniqueExpected[i] < 0)
				{
					uniqueExpected[i] = 0;
				}
				if (!Double.isFinite(uniqueMissLog[i]) || uniqueMissLog[i] > 0)
				{
					uniqueMissLog[i] = 0;
				}
			}
			expected = clean(expected);
			chanceSum = clean(chanceSum);
			shareSum = clean(shareSum);
			dryExpected = clean(dryExpected);
			if (!Double.isFinite(dryMissLog) || dryMissLog > 0)
			{
				dryMissLog = 0;
			}
			return this;
		}

		private static double clean(double v)
		{
			return Double.isFinite(v) && v >= 0 ? v : 0;
		}

		private static double[] fit(double[] values, int n)
		{
			if (values != null && values.length == n)
			{
				return values;
			}
			double[] out = new double[n];
			if (values != null)
			{
				System.arraycopy(values, 0, out, 0, Math.min(n, values.length));
			}
			return out;
		}
	}

	/** One kill. */
	public static final class Kill
	{
		private long at;
		private int kc;
		/** Your damage and other players' seen damage. */
		private long mine;
		private long others;
		/** The kill's whole damage the share was worked against. */
		private long total;
		private double share;
		/** True when the game gave you the kill. */
		private boolean credit;
		/** Your chance of any unique, and the uniques expected. */
		private double chance;
		private double expected;
		private boolean estimate;
		/** The unique that came from this kill, or 0. */
		private int unique;

		Kill(long at, int kc)
		{
			this.at = at;
			this.kc = kc;
		}

		public long getAt()
		{
			return at;
		}

		public int getKc()
		{
			return kc;
		}

		public long getMine()
		{
			return mine;
		}

		public long getOthers()
		{
			return others;
		}

		public long getTotal()
		{
			return total;
		}

		public double getShare()
		{
			return share;
		}

		public boolean isCredit()
		{
			return credit;
		}

		public double getChance()
		{
			return chance;
		}

		public double getExpected()
		{
			return expected;
		}

		public boolean isEstimate()
		{
			return estimate;
		}

		public int getUnique()
		{
			return unique;
		}

		/** True when other players' hits were seen: a team kill. */
		public boolean isTeam()
		{
			return others > 0;
		}
	}

	/** A unique put down to a kill: that kill's chance and how dry you were before it. */
	public static final class Find
	{
		private final Contribution.Boss boss;
		private final double chance;
		private final double took;
		private final double sooner;

		Find(Contribution.Boss boss, double chance, double took, double sooner)
		{
			this.boss = boss;
			this.chance = chance;
			this.took = took;
			this.sooner = sooner;
		}

		public Contribution.Boss getBoss()
		{
			return boss;
		}

		/** That kill's chance of a unique for you, or -1 when no kill was open. */
		public double getChance()
		{
			return chance;
		}

		/** Expected uniques from the kills since the last one, this one's included. */
		public double getTook()
		{
			return took;
		}

		/** The share of players who would have had one sooner. */
		public double getSooner()
		{
			return sooner;
		}
	}

	public ContributionLog repair()
	{
		if (tracks == null)
		{
			tracks = new ArrayList<>();
		}
		tracks.removeIf(t -> t == null || t.getBoss() == null);
		for (Track t : tracks)
		{
			t.repair();
		}
		return this;
	}

	public static SavedJson.Read<ContributionLog> load(com.google.gson.Gson gson, String json)
	{
		SavedJson.Read<ContributionLog> read = SavedJson.read(gson, json, ContributionLog.class);
		ContributionLog value = read.getValue();
		return new SavedJson.Read<>(value == null ? null : value.repair(), read.isDamaged());
	}

	/** This boss's log, or null when nothing has been logged. */
	public Track track(Contribution.Boss boss)
	{
		for (Track t : tracks)
		{
			if (t.getBoss() == boss)
			{
				return t;
			}
		}
		return null;
	}

	private Track trackFor(Contribution.Boss boss, long now)
	{
		Track t = track(boss);
		if (t == null)
		{
			t = new Track(boss.getKey()).repair();
			t.since = now;
			tracks.add(t);
		}
		return t;
	}

	public boolean isEmpty()
	{
		for (Track t : tracks)
		{
			if (t.kills > 0 || t.uniques > 0)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * A kill ended: logged with your damage and what it made of your odds.
	 *
	 * @param kc the kill count the line gave, or -1
	 * @param credit true when the game gave you the kill
	 */
	public Kill killed(Contribution.Boss boss, int kc, long mine, long others, boolean credit, long now)
	{
		Track t = trackFor(boss, now);
		Contribution.Chance c = Contribution.chance(boss, mine, others, credit);
		Kill kill = new Kill(now, kc);
		kill.mine = mine;
		kill.others = Math.max(0, others);
		kill.total = c.getTotal();
		kill.share = c.getShare();
		kill.credit = credit;
		kill.chance = c.getAny();
		kill.expected = c.getExpected();
		kill.estimate = c.isEstimate();
		t.kills++;
		if (credit)
		{
			t.credited++;
		}
		t.shareSum += kill.share;
		t.expected += kill.expected;
		t.chanceSum += kill.chance;
		double[] each = c.getEach();
		for (int i = 0; i < each.length; i++)
		{
			t.uniqueExpected[i] += each[i];
			t.uniqueMissLog[i] += Math.log1p(-Math.min(0.999999, each[i]));
		}
		t.dryKills++;
		t.dryExpected += kill.expected;
		t.dryMissLog += Math.log1p(-Math.min(0.999999, kill.chance));
		t.recent.add(0, kill);
		while (t.recent.size() > MAX_RECENT)
		{
			t.recent.remove(t.recent.size() - 1);
		}
		return kill;
	}

	/**
	 * A unique came to you (in the loot, or as a new collection log slot). It is put down to the latest kill of its
	 * boss within the window; the same unique seen twice counts once.
	 *
	 * @return the find, or null when the item is not one of these uniques or was already counted
	 */
	public Find found(int itemId, long now)
	{
		Contribution.Boss boss = Contribution.uniqueOf(itemId);
		if (boss == null)
		{
			return null;
		}
		Track t = trackFor(boss, now);
		Kill kill = t.recent.isEmpty() ? null : t.recent.get(0);
		if (kill != null && now - kill.at > WINDOW_MS)
		{
			kill = null;
		}
		if (kill != null && kill.unique != 0)
		{
			// Already seen: the loot, then the log's new item line.
			return null;
		}
		if (kill == null && t.lastUnique == itemId && now - t.lastUniqueAt <= WINDOW_MS)
		{
			return null;
		}
		double took = t.dryExpected;
		double sooner = 1 - Math.exp(t.dryMissLog);
		if (kill != null)
		{
			kill.unique = itemId;
			// The kill that ended the dry spell belongs to it: those before it made the players who had one sooner.
			sooner = 1 - Math.exp(Math.min(0, t.dryMissLog - Math.log1p(-Math.min(0.999999, kill.chance))));
		}
		int i = boss.uniqueIndex(itemId);
		t.uniqueGot[i]++;
		t.uniques++;
		t.lastUnique = itemId;
		t.lastUniqueAt = now;
		t.lastTook = took;
		t.dryKills = 0;
		t.dryExpected = 0;
		t.dryMissLog = 0;
		return new Find(boss, kill == null ? -1 : kill.chance, took, Math.max(0, sooner));
	}
}
