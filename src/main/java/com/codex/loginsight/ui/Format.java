package com.codex.loginsight.ui;

/** Turns hours, odds and counts into the short phrases the panel uses. */
public final class Format
{
	private Format()
	{
	}

	/** "<1m", "45m", "3h 12m", "120h" or "8,420h"; "-" when it cannot be estimated. */
	public static String hours(double hours)
	{
		if (Double.isNaN(hours) || Double.isInfinite(hours) || hours < 0)
		{
			return "-";
		}
		double minutes = hours * 60;
		if (minutes < 1)
		{
			return "<1m";
		}
		if (minutes < 60)
		{
			return Math.round(minutes) + "m";
		}
		if (hours < 10)
		{
			long h = (long) hours;
			long m = Math.round((hours - h) * 60);
			if (m == 60)
			{
				h++;
				m = 0;
			}
			return m == 0 ? h + "h" : h + "h " + m + "m";
		}
		return String.format("%,dh", Math.round(hours));
	}

	/** "about 3h", or "under 1m" when it is that short. */
	public static String about(double hours)
	{
		String text = hours(hours);
		if (text.startsWith("<"))
		{
			return "under " + text.substring(1);
		}
		return "-".equals(text) ? text : "~" + text;
	}

	/** "1/512", or "1/3.4" for common drops; "-" when unknown. */
	public static String chance(double chance)
	{
		if (chance <= 0 || Double.isNaN(chance))
		{
			return "-";
		}
		double in = 1 / chance;
		if (in < 1.05)
		{
			return "Always";
		}
		if (in < 10)
		{
			return String.format("1/%.1f", in);
		}
		return String.format("1/%,d", Math.round(in));
	}

	public static String count(long value)
	{
		return String.format("%,d", value);
	}

	/** Whole percent, with "<1%" and ">99%" at the ends so rare cases never read as 0 or 100. */
	public static String percent(double share)
	{
		if (share <= 0)
		{
			return "0%";
		}
		if (share < 0.01)
		{
			return "<1%";
		}
		if (share > 0.99 && share < 1)
		{
			return ">99%";
		}
		return Math.round(share * 100) + "%";
	}

	/** "just now", "5m ago", "3h ago", "2d ago". */
	public static String ago(long then, long now)
	{
		long seconds = Math.max(0, (now - then) / 1000);
		if (seconds < 60)
		{
			return "just now";
		}
		if (seconds < 3600)
		{
			return seconds / 60 + "m ago";
		}
		if (seconds < 86400)
		{
			return seconds / 3600 + "h ago";
		}
		return seconds / 86400 + "d ago";
	}

	/** "Vorkath kills" to "Vorkath", for compact lines that already say "kc". */
	public static String counterName(String label)
	{
		if (label == null)
		{
			return "";
		}
		return label.replaceAll("(?i) (kills|completions|completion count|opened|completed)$", "");
	}

	/** An activity name for a card title: the parenthetical detail moves to the tooltip. */
	public static String activity(String name)
	{
		String shortName = name.replaceAll("\\s*\\([^)]*\\)", "").trim();
		return shortName.isEmpty() ? name : Character.toUpperCase(shortName.charAt(0)) + shortName.substring(1);
	}
}
