package com.codex.loginsight.core;

import java.util.Locale;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;

/**
 * Shared bosses where your drop chance depends on your part in the kill, as the OSRS Wiki states the rule, worked from
 * the damage you were seen to deal. Kept free of client types. Where the wiki gives no formula for your share, or the
 * share had to be measured from hitsplats, {@link Chance#isEstimate()} says so.
 *
 * <ul>
 * <li>Nex (wiki, "Nex"): 3,400 hitpoints; "the share players receive from it is based on the player's total damage to
 * Nex, her minions, and any reavers during the encounter"; "There is an effective 1 in 43 chance of rolling the unique
 * drop table per kill"; multiple uniques per kill are possible, "calculated based on the number of players and their
 * individual contributions towards the kill", with no formula given; "Below lists the effective drop rates, which are
 * for each kill, rather than for each player." Her four generals (Fumus, Umbra, Cruor, Glacies) have 500 hitpoints
 * each. "In order to obtain drops from Nex, the player must deal a set amount of minimum damage", with no amount
 * given, and "Getting a drop from Nex is considered a kill in the log", so the kill count line is taken as the sign
 * that you earned a drop. Your chance is assumed to be your share of the per-kill rate: an estimate.</li>
 * <li>Corporeal Beast (wiki): 2,000 hitpoints; "only the player who dealt the most damage will receive kill credit and
 * loot." So a kill with your kill count line rolls the whole table for you, and one without gives you nothing.</li>
 * <li>Royal Titans (wiki): Branda and Eldric have 600 hitpoints each; "the drop rates of unique items scale linearly,
 * proportional to the damage dealt by that player"; "Contribution is calculated as a whole number percent of damage
 * dealt to the bosses (including recoil damage)"; "Damage to the elementals does not contribute towards reward
 * contribution"; a player "can choose to loot either Eldric or Branda" (or sacrifice, or take pages).</li>
 * </ul>
 */
public final class Contribution
{
	/** One unique (or a pair that share a rate, like the two staff crowns) and its rate per kill for a whole kill. */
	public static final class Unique
	{
		private final String name;
		private final int[] ids;
		private final double rate;

		Unique(String name, double rate, int... ids)
		{
			this.name = name;
			this.rate = rate;
			this.ids = ids;
		}

		public String getName()
		{
			return name;
		}

		/** The item ids that count as this unique; the first is the one shown. */
		public int[] getIds()
		{
			return ids.clone();
		}

		public int getIcon()
		{
			return ids[0];
		}

		/** The chance a kill gives it, with the whole kill's contribution. */
		public double getRate()
		{
			return rate;
		}

		boolean is(int itemId)
		{
			for (int id : ids)
			{
				if (id == itemId)
				{
					return true;
				}
			}
			return false;
		}
	}

	public enum Boss
	{
		NEX("nex", "Nex", 3400 + 4 * 500,
			new int[]{NpcID.NEX, NpcID.NEX_SPAWNING, NpcID.NEX_SOULSPLIT, NpcID.NEX_DEFLECT, NpcID.NEX_DYING,
				NpcID.NEX_SMOKEMAGE, NpcID.NEX_SHADOWMAGE, NpcID.NEX_BLOODMAGE, NpcID.NEX_ICEMAGE,
				NpcID.NEX_PRISON_BLOOD_REAVER, NpcID.NEX_PRISON_BLOOD_REAVER_BOSS},
			new int[]{NpcID.NEX, NpcID.NEX_SPAWNING, NpcID.NEX_SOULSPLIT, NpcID.NEX_DEFLECT, NpcID.NEX_DYING}, 1,
			new Unique[]{
				new Unique("Zaryte vambraces", 1 / 172.0, ItemID.ZARYTE_VAMBRACES),
				new Unique("Nihil horn", 1 / 258.0, ItemID.NIHIL_HORN),
				new Unique("Torva full helm", 1 / 258.0, ItemID.BROKEN_TORVA_HELM),
				new Unique("Torva platebody", 1 / 258.0, ItemID.BROKEN_TORVA_CHEST),
				new Unique("Torva platelegs", 1 / 258.0, ItemID.BROKEN_TORVA_LEGS),
				new Unique("Ancient hilt", 1 / 516.0, ItemID.GODWARS_GODSWORD_HILT_ANCIENT),
			}),
		CORP("corp", "Corporeal Beast", 2000,
			new int[]{NpcID.CORP_BEAST}, new int[]{NpcID.CORP_BEAST}, 1,
			new Unique[]{
				new Unique("Spirit shield", 8 / 512.0, ItemID.SPIRIT_SHIELD),
				new Unique("Holy elixir", 3 / 512.0, ItemID.HOLY_ELIXIR),
				new Unique("Jar of spirits", 1 / 1000.0, ItemID.JAR_OF_SPIRITS),
				new Unique("Spectral sigil", 1 / 1365.0, ItemID.SPECTRAL_SIGIL),
				new Unique("Arcane sigil", 1 / 1365.0, ItemID.ARCANE_SIGIL),
				new Unique("Elysian sigil", 1 / 4095.0, ItemID.ELYSIAN_SIGIL),
			}),
		TITANS("titans", "Royal Titans", 2 * 600,
			new int[]{NpcID.RT_FIRE_QUEEN, NpcID.RT_ICE_KING}, new int[]{NpcID.RT_FIRE_QUEEN, NpcID.RT_ICE_KING}, 2,
			new Unique[]{
				new Unique("Giantsoul amulet", 1 / 16.0, ItemID.GIANTSOUL_AMULET_UNCHARGED),
				new Unique("Staff crown", 1 / 75.0, ItemID.TWINFLAME_PIECE_2, ItemID.TWINFLAME_PIECE_1),
			});

		private final String key;
		private final String title;
		private final int hitpoints;
		private final int[] damaged;
		private final int[] dying;
		private final int deaths;
		private final Unique[] uniques;

		Boss(String key, String title, int hitpoints, int[] damaged, int[] dying, int deaths, Unique[] uniques)
		{
			this.key = key;
			this.title = title;
			this.hitpoints = hitpoints;
			this.damaged = damaged;
			this.dying = dying;
			this.deaths = deaths;
			this.uniques = uniques;
		}

		public String getKey()
		{
			return key;
		}

		public String getTitle()
		{
			return title;
		}

		/** The hitpoints whose damage makes up a kill, from the wiki: the least the whole team deals. */
		public int getHitpoints()
		{
			return hitpoints;
		}

		public Unique[] getUniques()
		{
			return uniques.clone();
		}

		/** How many of its NPCs die in one kill (both Royal Titans). */
		public int getDeaths()
		{
			return deaths;
		}

		public static Boss ofKey(String key)
		{
			for (Boss b : values())
			{
				if (b.key.equals(key))
				{
					return b;
				}
			}
			return null;
		}

		/** The boss whose kill damage to this NPC counts towards, or null. */
		public static Boss ofDamaged(int npcId)
		{
			for (Boss b : values())
			{
				for (int id : b.damaged)
				{
					if (id == npcId)
					{
						return b;
					}
				}
			}
			return null;
		}

		/** The boss this NPC's death ends a kill of, or null. */
		public static Boss ofDying(int npcId)
		{
			for (Boss b : values())
			{
				for (int id : b.dying)
				{
					if (id == npcId)
					{
						return b;
					}
				}
			}
			return null;
		}

		/** The boss a kill count line names, or null: "Nex", "Corporeal Beast", "Royal Titans". */
		public static Boss ofChatName(String name)
		{
			if (name == null)
			{
				return null;
			}
			String n = name.trim().toLowerCase(Locale.ENGLISH);
			switch (n)
			{
				case "nex":
					return NEX;
				case "corporeal beast":
					return CORP;
				case "royal titans":
				case "royal titan":
					return TITANS;
				default:
					return null;
			}
		}

		/** The boss whose collection log page this is, or null. */
		public static Boss ofPage(String page)
		{
			if (page == null)
			{
				return null;
			}
			String p = page.trim().toLowerCase(Locale.ENGLISH);
			if (p.equals("nex"))
			{
				return NEX;
			}
			if (p.contains("corporeal beast"))
			{
				return CORP;
			}
			if (p.contains("royal titan"))
			{
				return TITANS;
			}
			return null;
		}

		/** The index of the unique this item is, or -1. */
		public int uniqueIndex(int itemId)
		{
			for (int i = 0; i < uniques.length; i++)
			{
				if (uniques[i].is(itemId))
				{
					return i;
				}
			}
			return -1;
		}
	}

	private Contribution()
	{
	}

	/** The boss this item is a unique of, or null. */
	public static Boss uniqueOf(int itemId)
	{
		for (Boss b : Boss.values())
		{
			if (b.uniqueIndex(itemId) >= 0)
			{
				return b;
			}
		}
		return null;
	}

	/** A kill's damage and the share it made. */
	public static final class Share
	{
		private final long total;
		private final double share;
		private final boolean measured;

		Share(long total, double share, boolean measured)
		{
			this.total = total;
			this.share = share;
			this.measured = measured;
		}

		/** The kill's whole damage: the wiki's hitpoints, or all the damage seen when more. */
		public long getTotal()
		{
			return total;
		}

		/** Your share of the kill, 0 to 1. */
		public double getShare()
		{
			return share;
		}

		/** True when the share was measured against other players' hits (a team kill), so it is an estimate. */
		public boolean isMeasured()
		{
			return measured;
		}
	}

	/**
	 * Your share of a kill from the damage you were seen to deal and the damage other players were seen to deal. With
	 * no other player's hit seen the kill is yours alone. Otherwise the whole is the boss's hitpoints from the wiki, or
	 * every hit seen when that is more (healing, reavers), and the Royal Titans' share is cut to a whole percent.
	 */
	public static Share share(Boss boss, long mine, long others)
	{
		long seen = Math.max(0, mine) + Math.max(0, others);
		long total = Math.max(boss.hitpoints, seen);
		if (mine <= 0)
		{
			return new Share(total, 0, others > 0);
		}
		if (others <= 0)
		{
			return new Share(total, 1, false);
		}
		double share = Math.min(1, mine / (double) total);
		if (boss == Boss.TITANS)
		{
			share = Math.floor(share * 100 + 1e-9) / 100;
		}
		return new Share(total, share, true);
	}

	/** One kill's chance of each unique for you. */
	public static final class Chance
	{
		private final double share;
		private final long total;
		private final double[] each;
		private final boolean estimate;
		private final String why;

		Chance(double share, long total, double[] each, boolean estimate, String why)
		{
			this.share = share;
			this.total = total;
			this.each = each;
			this.estimate = estimate;
			this.why = why;
		}

		public double getShare()
		{
			return share;
		}

		public long getTotal()
		{
			return total;
		}

		/** Your chance of each of the boss's uniques this kill, in {@link Boss#getUniques()} order. */
		public double[] getEach()
		{
			return each.clone();
		}

		/** The chance of at least one unique this kill. */
		public double getAny()
		{
			double none = 1;
			for (double c : each)
			{
				none *= 1 - c;
			}
			return 1 - none;
		}

		/** The uniques this kill gives you on average. */
		public double getExpected()
		{
			double sum = 0;
			for (double c : each)
			{
				sum += c;
			}
			return sum;
		}

		public boolean isEstimate()
		{
			return estimate;
		}

		/** Why the chance is an estimate or nothing, or null. */
		public String getWhy()
		{
			return why;
		}
	}

	/**
	 * A kill's chance of each unique for you.
	 *
	 * @param credit true when the game gave you the kill (its kill count line)
	 */
	public static Chance chance(Boss boss, long mine, long others, boolean credit)
	{
		Share s = share(boss, mine, others);
		Unique[] u = boss.uniques;
		double[] each = new double[u.length];
		switch (boss)
		{
			case CORP:
				if (credit)
				{
					for (int i = 0; i < u.length; i++)
					{
						each[i] = u[i].rate;
					}
					return new Chance(s.share, s.total, each, false, null);
				}
				return new Chance(s.share, s.total, each, false, "another player dealt the most damage");
			case NEX:
				if (!credit)
				{
					return new Chance(s.share, s.total, each, false, "no kill count line, so no drop (minimum damage)");
				}
				for (int i = 0; i < u.length; i++)
				{
					each[i] = u[i].rate * s.share;
				}
				return new Chance(s.share, s.total, each, s.measured,
					s.measured ? "your share of the per-kill rate is assumed; the wiki gives no formula" : null);
			default:
				for (int i = 0; i < u.length; i++)
				{
					each[i] = u[i].rate * s.share;
				}
				return new Chance(s.share, s.total, each, s.measured,
					s.measured ? "your share is measured from hitsplats and cut to a whole percent" : null);
		}
	}

	/**
	 * The damage seen in the boss fight under way. Nothing here is shown until a kill ends: it is only read when the
	 * kill count line comes, or a while after the boss died without one.
	 */
	public static final class Fight
	{
		/** A death with no kill count line this long after is a kill without credit. */
		public static final long CREDIT_WAIT_MS = 20_000L;
		/** Damage this old with no new hit belongs to no kill. */
		public static final long IDLE_MS = 10 * 60_000L;

		private final long[] mine = new long[Boss.values().length];
		private final long[] others = new long[Boss.values().length];
		private final long[] lastHit = new long[Boss.values().length];
		private final long[] diedAt = new long[Boss.values().length];
		private final int[] deaths = new int[Boss.values().length];

		/** A finished kill's damage. */
		public static final class Done
		{
			private final Boss boss;
			private final long mine;
			private final long others;

			Done(Boss boss, long mine, long others)
			{
				this.boss = boss;
				this.mine = mine;
				this.others = others;
			}

			public Boss getBoss()
			{
				return boss;
			}

			public long getMine()
			{
				return mine;
			}

			public long getOthers()
			{
				return others;
			}
		}

		public void hit(Boss boss, boolean yours, int amount, long now)
		{
			if (amount <= 0)
			{
				return;
			}
			int i = boss.ordinal();
			if ((mine[i] > 0 || others[i] > 0) && now - lastHit[i] > IDLE_MS)
			{
				reset(i);
			}
			if (yours)
			{
				mine[i] += amount;
			}
			else
			{
				others[i] += amount;
			}
			lastHit[i] = now;
		}

		/** One of the boss's NPCs died. */
		public void died(Boss boss, long now)
		{
			int i = boss.ordinal();
			if (mine[i] <= 0)
			{
				return;
			}
			deaths[i]++;
			if (deaths[i] >= boss.deaths && diedAt[i] == 0)
			{
				diedAt[i] = now;
			}
		}

		/** The kill count line came: the kill is yours, with the damage seen. Null when you dealt none. */
		public Done credited(Boss boss)
		{
			int i = boss.ordinal();
			if (mine[i] <= 0)
			{
				reset(i);
				return null;
			}
			Done done = new Done(boss, mine[i], others[i]);
			reset(i);
			return done;
		}

		/** Kills whose boss died a while ago with no kill count line: kills without credit. */
		public java.util.List<Done> expired(long now)
		{
			java.util.List<Done> out = new java.util.ArrayList<>();
			for (Boss boss : Boss.values())
			{
				int i = boss.ordinal();
				if (diedAt[i] > 0 && now - diedAt[i] > CREDIT_WAIT_MS)
				{
					if (mine[i] > 0)
					{
						out.add(new Done(boss, mine[i], others[i]));
					}
					reset(i);
				}
				else if (diedAt[i] == 0 && (mine[i] > 0 || others[i] > 0) && now - lastHit[i] > IDLE_MS)
				{
					reset(i);
				}
			}
			return out;
		}

		/** Your damage so far, for tests only: nothing shows it during a fight. */
		long mine(Boss boss)
		{
			return mine[boss.ordinal()];
		}

		/** True while a kill you have dealt damage in has not been logged yet. */
		public boolean isOpen(Boss boss)
		{
			return mine[boss.ordinal()] > 0;
		}

		public void clear()
		{
			for (int i = 0; i < mine.length; i++)
			{
				reset(i);
			}
		}

		private void reset(int i)
		{
			mine[i] = 0;
			others[i] = 0;
			lastHit[i] = 0;
			diedAt[i] = 0;
			deaths[i] = 0;
		}
	}

	/** Reads "Your Nex kill count is: 12." and the like for the three bosses. */
	public static final class KillLine
	{
		private static final java.util.regex.Pattern COUNT = java.util.regex.Pattern.compile(
			"^Your (?<name>.+?) kill count is: ?(?<kc>[0-9,]+)");

		private final Boss boss;
		private final int count;

		KillLine(Boss boss, int count)
		{
			this.boss = boss;
			this.count = count;
		}

		public Boss getBoss()
		{
			return boss;
		}

		public int getCount()
		{
			return count;
		}

		/** The kill count line of one of these bosses, or null. */
		public static KillLine read(String message)
		{
			if (message == null)
			{
				return null;
			}
			String text = net.runelite.client.util.Text.removeTags(message).replaceAll("@[a-z]{2,6}@", "").trim();
			java.util.regex.Matcher m = COUNT.matcher(text);
			if (!m.find())
			{
				return null;
			}
			Boss boss = Boss.ofChatName(m.group("name"));
			if (boss == null)
			{
				return null;
			}
			int kc;
			try
			{
				kc = Integer.parseInt(m.group("kc").replace(",", ""));
			}
			catch (NumberFormatException e)
			{
				kc = -1;
			}
			return new KillLine(boss, kc);
		}
	}
}
