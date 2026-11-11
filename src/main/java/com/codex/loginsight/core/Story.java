package com.codex.loginsight.core;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Log Insight's moments for the suite's one account story (contract N1): new slots as they drop, wanted slots found,
 * pages finished and every hundredth slot. Annals and Home read it to tell the account's story; Log Insight only
 * writes it. Saved per account as {@code codexloginsight/storyV1}:
 *
 * <pre>
 * {"version":1,"savedAt":ms,"moments":[{"t":ms,"kind":"drop","text":"Tanzanite fang at 612 Zulrah kc",
 *   "figure":"1/512","ref":"item:12922"}]}
 * </pre>
 *
 * Newest last, at most {@link #MAX} moments, each text at most {@link #MAX_TEXT} characters. {@code kind} is one of
 * "drop" (a new slot), "goal" (a wanted slot found) or "milestone" (a page finished, a hundredth slot); {@code figure}
 * is the odds it came at, or the slots owned, and {@code ref} names the item ("item:12922") or page ("page:Zulrah").
 * Pinned by {@code StoryShapeTest}.
 */
public final class Story
{
	public static final String KEY = "storyV1";
	static final int MAX = 200;
	static final int MAX_TEXT = 120;
	static final String DROP = "drop";
	static final String GOAL = "goal";
	static final String MILESTONE = "milestone";

	private int version = 1;
	private long savedAt;
	private List<Moment> moments = new ArrayList<>();

	/**
	 * The saved story, keeping every moment that reads; a fresh one when nothing is saved or nothing reads. Null when it
	 * was written by a newer Log Insight (a later version under this key), which this one must not write over.
	 */
	public static Story load(Gson gson, String json)
	{
		SavedJson.Read<Story> read = SavedJson.read(gson, json, Story.class);
		Story story = read.getValue();
		if (story == null)
		{
			return new Story();
		}
		if (story.version > 1)
		{
			return null;
		}
		story.version = 1;
		if (story.moments == null)
		{
			story.moments = new ArrayList<>();
		}
		story.moments.removeIf(m -> m == null || m.t <= 0 || m.kind == null || m.text == null || m.text.isEmpty());
		for (Moment m : story.moments)
		{
			m.text = clip(m.text);
		}
		story.moments.sort((a, b) -> Long.compare(a.t, b.t));
		story.trim();
		return story;
	}

	/** A new slot dropped: "Tanzanite fang at 612 Zulrah kc, luckier than 30% of players". */
	public static Moment slot(long t, int itemId, String name, String counter, int count, double chance,
		double luckier, boolean wanted)
	{
		StringBuilder text = new StringBuilder(wanted ? "Wanted, and found: " : "New slot: ").append(name);
		if (count > 0 && counter != null && !counter.isEmpty())
		{
			text.append(" at ").append(String.format(java.util.Locale.ENGLISH, "%,d", count)).append(' ')
				.append(WantedFeed.counterName(counter)).append(" kc");
		}
		if (luckier >= 0 && luckier <= 1)
		{
			text.append(", luckier than ").append(Math.round(luckier * 100)).append("% of players");
		}
		return new Moment(t, wanted ? GOAL : DROP, text.toString(), WantedFeed.rate(chance),
			itemId > 0 ? "item:" + itemId : null);
	}

	/** Every slot of a page owned: "Zulrah page complete, 8 of 8 slots". */
	public static Moment pageDone(long t, String page, int slots)
	{
		return new Moment(t, MILESTONE, page + " page complete, " + slots + " of " + slots + " slots", null,
			"page:" + page);
	}

	/** A hundredth slot: "500 collection log slots". */
	public static Moment slots(long t, int owned)
	{
		return new Moment(t, MILESTONE, String.format(java.util.Locale.ENGLISH, "%,d collection log slots", owned),
			String.valueOf(owned), null);
	}

	/** The latest multiple of a hundred passed going from {@code before} to {@code after} slots, or 0. */
	public static int hundredPassed(int before, int after)
	{
		if (before < 0 || after <= before || after / 100 == before / 100)
		{
			return 0;
		}
		return after / 100 * 100;
	}

	/** Adds a moment, newest last, dropping the oldest past {@link #MAX}. The same moment twice is kept once. */
	public boolean add(Moment moment, long now)
	{
		if (moment == null || moment.text == null || moment.text.isEmpty())
		{
			return false;
		}
		moment.text = clip(moment.text);
		for (Moment m : moments)
		{
			if (m.t == moment.t && m.text.equals(moment.text))
			{
				return false;
			}
		}
		moments.add(moment);
		moments.sort((a, b) -> Long.compare(a.t, b.t));
		trim();
		savedAt = now;
		return true;
	}

	public List<Moment> getMoments()
	{
		return Collections.unmodifiableList(moments);
	}

	private void trim()
	{
		while (moments.size() > MAX)
		{
			moments.remove(0);
		}
	}

	private static String clip(String text)
	{
		return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT - 1).trim() + "…";
	}

	/** One moment of the story. */
	public static final class Moment
	{
		private long t;
		private String kind;
		private String text;
		private String figure;
		private String ref;

		Moment(long t, String kind, String text, String figure, String ref)
		{
			this.t = t;
			this.kind = kind;
			this.text = text;
			this.figure = figure;
			this.ref = ref;
		}

		public long getT()
		{
			return t;
		}

		public String getKind()
		{
			return kind;
		}

		public String getText()
		{
			return text;
		}

		public String getFigure()
		{
			return figure;
		}

		public String getRef()
		{
			return ref;
		}
	}
}
