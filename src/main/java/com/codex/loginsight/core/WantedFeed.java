package com.codex.loginsight.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The wanted list as the rest of the Codex suite reads it: Navigator pins each slot as a goal with its 50% and 90%
 * kill marks, Autopilot and Session Chronicle can plan an hour towards one, Home watches how dry you are, and Boss
 * Pilot aims a trip at it. Saved per account as {@code codexloginsight/wantedV1}:
 *
 * <pre>
 * {"version":1,"savedAt":ms,"wanted":[{"itemId":12922,"name":"Tanzanite fang","source":"Zulrah","kc":612,
 *   "rate":"1/512","p50Kills":355,"p90Kills":1178,"chanceSoFar":0.6981,"page":"Zulrah","hours":12.8}]}
 * </pre>
 *
 * <ul>
 * <li>{@code source}: where {@code kc} is counted (the log's counter, "Zulrah" for "Zulrah kills"); for a slot the log
 * keeps no counter for, the activity it comes quickest from.</li>
 * <li>{@code kc}: your count on that counter, or -1 before it has been read.</li>
 * <li>{@code rate}: the chance per count, "1/512", or absent when unknown.</li>
 * <li>{@code p50Kills}, {@code p90Kills}: the counts (on the same counter, not "kills from now") by which half and nine
 * in ten players would have it, or -1 when the slot has no counter.</li>
 * <li>{@code chanceSoFar}: the share of players who would have had it by your count, 0 to 1 (0.9 is "90% dry"), or -1
 * when unknown.</li>
 * <li>Optional, added beside the contract: {@code page}, the log page it is on, and {@code hours}, the average hours to
 * it from its quickest source at your pace (a fresh roll each time, so the dry streak does not shorten it).</li>
 * </ul>
 *
 * A breaking change gets a new key, never a new version under this one. Field names and order are pinned by
 * {@code WantedFeedShapeTest}, whose fixture is the byte copy readers test against.
 */
public final class WantedFeed
{
	public static final String KEY = "wantedV1";
	/** Navigator's Goal Exchange reads offered goals from each app under this key. */
	public static final String OFFERS_KEY = "goalOffersV1";
	static final String APP = "Log Insight";
	/** The goal exchange takes at most this many offers from one app. */
	static final int MAX_OFFERS = 20;

	private final int version = 1;
	private final long savedAt;
	private final List<Entry> wanted;

	private WantedFeed(long savedAt, List<Entry> wanted)
	{
		this.savedAt = savedAt;
		this.wanted = wanted;
	}

	/** The wanted slots as the report reads them, oldest mark first. */
	public static WantedFeed of(List<Report.ItemReport> items, long savedAt)
	{
		List<Entry> entries = new ArrayList<>();
		for (Report.ItemReport item : items)
		{
			if (item != null && item.getItemId() > 0 && !item.isOwned())
			{
				entries.add(Entry.of(item));
			}
		}
		return new WantedFeed(savedAt, Collections.unmodifiableList(entries));
	}

	public List<Entry> getWanted()
	{
		return wanted;
	}

	public long getSavedAt()
	{
		return savedAt;
	}

	/** The same list at another time, to tell whether anything but the time has changed. */
	public WantedFeed at(long when)
	{
		return new WantedFeed(when, wanted);
	}

	/** Each wanted slot offered to Navigator's Goal Exchange as a goal of one slot. */
	public Offers offers()
	{
		List<Offer> offers = new ArrayList<>();
		for (Entry e : wanted)
		{
			if (offers.size() >= MAX_OFFERS)
			{
				break;
			}
			offers.add(new Offer("slot:" + e.itemId, e.source == null ? e.name : e.name + " (" + e.source + ")",
				e.hours));
		}
		return new Offers(savedAt, Collections.unmodifiableList(offers));
	}

	/** One wanted slot. */
	public static final class Entry
	{
		private final int itemId;
		private final String name;
		private final String source;
		private final int kc;
		private final String rate;
		private final int p50Kills;
		private final int p90Kills;
		private final double chanceSoFar;
		private final String page;
		private final Double hours;

		private Entry(int itemId, String name, String source, int kc, String rate, int p50Kills, int p90Kills,
			double chanceSoFar, String page, Double hours)
		{
			this.itemId = itemId;
			this.name = name;
			this.source = source;
			this.kc = kc;
			this.rate = rate;
			this.p50Kills = p50Kills;
			this.p90Kills = p90Kills;
			this.chanceSoFar = chanceSoFar;
			this.page = page;
			this.hours = hours;
		}

		static Entry of(Report.ItemReport item)
		{
			String source = item.getLabel() != null ? counterName(item.getLabel()) : sourceName(item.getSource());
			double dry = item.getDryChance();
			double soFar = dry >= 0 && dry <= 1 ? round(1 - dry, 4) : -1;
			double hours = item.getHours();
			return new Entry(item.getItemId(), item.getName() == null ? "Item " + item.getItemId() : item.getName(),
				source, item.getLabel() == null ? -1 : item.getCount(), rate(item.getChance()),
				Wanted.countFor(item, 0.5), Wanted.countFor(item, 0.9), soFar, item.getPage(),
				hours > 0 && Double.isFinite(hours) ? round(hours, 1) : null);
		}

		public int getItemId()
		{
			return itemId;
		}

		public String getName()
		{
			return name;
		}

		public String getSource()
		{
			return source;
		}

		public int getKc()
		{
			return kc;
		}

		public String getRate()
		{
			return rate;
		}

		public int getP50Kills()
		{
			return p50Kills;
		}

		public int getP90Kills()
		{
			return p90Kills;
		}

		public double getChanceSoFar()
		{
			return chanceSoFar;
		}

		public String getPage()
		{
			return page;
		}

		public Double getHours()
		{
			return hours;
		}
	}

	/** Navigator's Goal Exchange: {@code {"version":1,"savedAt":ms,"offers":[...]}}, at most twenty. */
	public static final class Offers
	{
		private final int version = 1;
		private final long savedAt;
		private final List<Offer> offers;

		Offers(long savedAt, List<Offer> offers)
		{
			this.savedAt = savedAt;
			this.offers = offers;
		}

		public List<Offer> getOffers()
		{
			return offers;
		}
	}

	/**
	 * A wanted slot as a goal: one slot to get, none yet, with the average hours to it at your pace. Its key,
	 * "slot:" and the item id, stays the same while the slot is wanted.
	 */
	public static final class Offer
	{
		private final String key;
		private final String title;
		private final String kind = "slot";
		private final int have = 0;
		private final int need = 1;
		private final String unit = "slot";
		private final Double hoursLeft;
		private final String app = APP;

		Offer(String key, String title, Double hoursLeft)
		{
			this.key = key;
			this.title = title;
			this.hoursLeft = hoursLeft;
		}

		public String getKey()
		{
			return key;
		}

		public String getTitle()
		{
			return title;
		}

		public Double getHoursLeft()
		{
			return hoursLeft;
		}
	}

	/** "1/512", "1/7.7" below ten, or null when unknown: plain digits whatever the locale, for readers to parse. */
	static String rate(double chance)
	{
		if (!(chance > 0) || chance > 1)
		{
			return null;
		}
		double in = 1 / chance;
		if (in < 10 && Math.abs(in - Math.round(in)) >= 0.05)
		{
			return String.format(Locale.ROOT, "1/%.1f", in);
		}
		return "1/" + Math.round(in);
	}

	/** "Zulrah" for "Zulrah kills", "Chambers of Xeric" for "Chambers of Xeric completions". */
	static String counterName(String label)
	{
		return label.replaceAll("(?i) (kills|completions|completion count|opened|completed)$", "");
	}

	/** "Killing alchemical hydra (on task)" reads "Alchemical hydra"; null stays null. */
	static String sourceName(String activity)
	{
		if (activity == null)
		{
			return null;
		}
		String text = activity.replaceAll("\\s*\\(.*\\)\\s*$", "")
			.replaceFirst("(?i)^(killing|defeating|looting|opening|finishing|completing) (the )?", "").trim();
		return text.isEmpty() ? activity : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private static double round(double value, int places)
	{
		double scale = Math.pow(10, places);
		return Math.round(value * scale) / scale;
	}
}
