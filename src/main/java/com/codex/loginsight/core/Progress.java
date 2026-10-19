package com.codex.loginsight.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What this account has done, saved per RuneScape profile: owned slots, the
 * collection log's counters, the finds Log Insight saw happen and your own pace.
 * Plain fields so Gson can save it as it is.
 */
public final class Progress
{
	private static final int MAX_FINDS = 40;
	/** Finest finds kept for the trophy shelf, however long ago they dropped. */
	static final int TROPHIES = 5;
	/** The most slots that can be wanted at once: a short list stays a list of goals. */
	public static final int MAX_WANTED = 6;

	/** Slots known to be owned. Only ever grows; the game never takes a slot back. */
	private Set<Integer> owned = new HashSet<>();
	/** Collection log counters, such as "Vorkath kills" to 312. */
	private Map<String, Integer> counters = new HashMap<>();
	/** Finds seen as they happened, newest first. */
	private List<Find> finds = new ArrayList<>();
	/** Your own pace per counter. */
	private Map<String, Pace> pace = new HashMap<>();
	/** When the whole log was last read from the game, or 0. */
	private long fullReadAt;
	/** Pages whose counters were read from the log itself. */
	private Map<String, Long> pagesRead = new LinkedHashMap<>();
	/** The counter names each read page shows, in its own order. */
	private Map<String, List<String>> pageLabels = new HashMap<>();
	/** Slots you marked as wanted, in the order you marked them. */
	private List<Integer> wanted = new ArrayList<>();
	/**
	 * The rarest finds ever seen, rarest first, kept apart from {@link #finds} so a great drop is not pushed off the
	 * shelf by forty ordinary ones. Missing from older saves, which start it from the finds they still have.
	 */
	private List<Find> trophies;

	public Progress copy()
	{
		Progress p = new Progress();
		p.owned = new HashSet<>(owned());
		p.counters = new HashMap<>(counters());
		p.finds = new ArrayList<>(finds());
		p.pace = new HashMap<>();
		for (Map.Entry<String, Pace> e : pace().entrySet())
		{
			p.pace.put(e.getKey(), e.getValue().copy());
		}
		p.fullReadAt = fullReadAt;
		p.pagesRead = new LinkedHashMap<>(pagesRead());
		p.pageLabels = new HashMap<>(pageLabels());
		p.wanted = new ArrayList<>(wanted());
		p.trophies = new ArrayList<>(trophies());
		return p;
	}

	// Gson leaves fields null when an older save lacks them, so every read goes through these.

	private Set<Integer> owned()
	{
		if (owned == null)
		{
			owned = new HashSet<>();
		}
		return owned;
	}

	private Map<String, Integer> counters()
	{
		if (counters == null)
		{
			counters = new HashMap<>();
		}
		return counters;
	}

	private List<Find> finds()
	{
		if (finds == null)
		{
			finds = new ArrayList<>();
		}
		return finds;
	}

	private List<Find> trophies()
	{
		if (trophies == null)
		{
			List<Find> seeded = new ArrayList<>();
			for (Find f : finds())
			{
				offer(seeded, f);
			}
			trophies = seeded;
		}
		return trophies;
	}

	/** Puts a find on the shelf if it is among the rarest; only finds with known odds compete. */
	private static void offer(List<Find> shelf, Find find)
	{
		if (find == null || find.getChance() <= 0)
		{
			return;
		}
		for (Find f : shelf)
		{
			if (f.getItemId() == find.getItemId())
			{
				return;
			}
		}
		shelf.add(find);
		shelf.sort(Progress::rarer);
		while (shelf.size() > TROPHIES)
		{
			shelf.remove(shelf.size() - 1);
		}
	}

	/** Rarer odds first; at the same odds, the luckier find (the one fewer players would have had by then). */
	private static int rarer(Find a, Find b)
	{
		int byOdds = Double.compare(a.getChance(), b.getChance());
		return byOdds != 0 ? byOdds : Double.compare(b.getLuckier(), a.getLuckier());
	}

	private Map<String, Pace> pace()
	{
		if (pace == null)
		{
			pace = new HashMap<>();
		}
		return pace;
	}

	private Map<String, Long> pagesRead()
	{
		if (pagesRead == null)
		{
			pagesRead = new LinkedHashMap<>();
		}
		return pagesRead;
	}

	private Map<String, List<String>> pageLabels()
	{
		if (pageLabels == null)
		{
			pageLabels = new HashMap<>();
		}
		return pageLabels;
	}

	private List<Integer> wanted()
	{
		if (wanted == null)
		{
			wanted = new ArrayList<>();
		}
		return wanted;
	}

	/** Slots marked as wanted, oldest mark first. */
	public List<Integer> getWanted()
	{
		return Collections.unmodifiableList(wanted());
	}

	public boolean isWanted(int itemId)
	{
		return wanted().contains(itemId);
	}

	/**
	 * Marks a missing slot wanted, or unmarks it. An owned slot cannot be wanted, and a full list refuses a new one.
	 *
	 * @return true if the list changed
	 */
	public boolean toggleWanted(int itemId)
	{
		if (wanted().remove((Integer) itemId))
		{
			return true;
		}
		if (itemId <= 0 || owns(itemId) || wanted().size() >= MAX_WANTED)
		{
			return false;
		}
		wanted().add(itemId);
		return true;
	}

	public boolean owns(int itemId)
	{
		return owned().contains(itemId);
	}

	public Set<Integer> getOwned()
	{
		return Collections.unmodifiableSet(owned());
	}

	/** @return true if this slot is new */
	public boolean addOwned(int itemId)
	{
		// A slot found is no longer wanted.
		wanted().remove((Integer) itemId);
		return owned().add(itemId);
	}

	public Map<String, Integer> getCounters()
	{
		return Collections.unmodifiableMap(counters());
	}

	public Integer counter(String label)
	{
		return counters().get(label);
	}

	/** @return true if the value changed */
	public boolean setCounter(String label, int value)
	{
		Integer old = counters().put(label, value);
		return old == null || old != value;
	}

	public List<Find> getFinds()
	{
		return Collections.unmodifiableList(finds());
	}

	public void addFind(Find find)
	{
		offer(trophies(), find);
		finds().add(0, find);
		while (finds().size() > MAX_FINDS)
		{
			finds().remove(finds().size() - 1);
		}
	}

	/** The finest finds, rarest first: at most {@link #TROPHIES}. */
	public List<Find> getTrophies()
	{
		return Collections.unmodifiableList(new ArrayList<>(trophies()));
	}

	public Pace pace(String label)
	{
		return pace().get(label);
	}

	public Pace paceFor(String label)
	{
		return pace().computeIfAbsent(label, k -> new Pace());
	}

	public long getFullReadAt()
	{
		return fullReadAt;
	}

	public void setFullReadAt(long fullReadAt)
	{
		this.fullReadAt = fullReadAt;
	}

	public void markPageRead(String page, long at)
	{
		pagesRead().put(page, at);
	}

	public void setPageLabels(String page, List<String> labels)
	{
		pageLabels().put(page, new ArrayList<>(labels));
	}

	/** Counter names the page showed when it was last read, or null. */
	public List<String> pageLabels(String page)
	{
		List<String> labels = pageLabels().get(page);
		return labels == null ? null : Collections.unmodifiableList(labels);
	}

	public int pagesReadCount()
	{
		return pagesRead().size();
	}

	public boolean isPageRead(String page)
	{
		return pagesRead().containsKey(page);
	}

	/** A slot that arrived while Log Insight was watching. */
	public static final class Find
	{
		private int itemId;
		private String name;
		private String page;
		private String label;
		private int count;
		private double chance;
		/** Share of players who would still be waiting at this count: higher is luckier. */
		private double luckier;
		private long at;

		public Find(int itemId, String name, String page, String label, int count, double chance, double luckier, long at)
		{
			this.itemId = itemId;
			this.name = name;
			this.page = page;
			this.label = label;
			this.count = count;
			this.chance = chance;
			this.luckier = luckier;
			this.at = at;
		}

		public int getItemId()
		{
			return itemId;
		}

		public String getName()
		{
			return name;
		}

		public String getPage()
		{
			return page;
		}

		public String getLabel()
		{
			return label;
		}

		/** Kill count when it dropped, or 0 if unknown. */
		public int getCount()
		{
			return count;
		}

		/** Chance per count, or 0 if unknown. */
		public double getChance()
		{
			return chance;
		}

		/** 0 to 1, or -1 if unknown. */
		public double getLuckier()
		{
			return luckier;
		}

		public long getAt()
		{
			return at;
		}
	}

	/**
	 * Your own speed at one counter, learned from the gaps between kill count messages.
	 * Gaps longer than a break are ignored so banking trips count but logging off does not.
	 */
	public static final class Pace
	{
		static final long MAX_GAP_MS = 12 * 60_000L;
		private static final double WEIGHT = 0.12;

		private double secondsPerCount;
		private int samples;
		private long lastAt;
		private int lastCount;

		Pace copy()
		{
			Pace p = new Pace();
			p.secondsPerCount = secondsPerCount;
			p.samples = samples;
			p.lastAt = lastAt;
			p.lastCount = lastCount;
			return p;
		}

		public void record(int count, long at)
		{
			if (lastAt > 0 && count > lastCount && at > lastAt && at - lastAt <= MAX_GAP_MS * (count - lastCount))
			{
				double seconds = (at - lastAt) / 1000.0 / (count - lastCount);
				secondsPerCount = samples == 0 ? seconds : secondsPerCount + (seconds - secondsPerCount) * WEIGHT;
				samples++;
			}
			lastAt = at;
			lastCount = count;
		}

		public int getSamples()
		{
			return samples;
		}

		/** Completions per hour, or 0 if not learned yet. */
		public double perHour()
		{
			return samples == 0 || secondsPerCount <= 0 ? 0 : 3600 / secondsPerCount;
		}
	}
}
