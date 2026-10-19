package com.codex.loginsight;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(CodexLogInsightConfig.GROUP)
public interface CodexLogInsightConfig extends Config
{
	String GROUP = "codexloginsight";

	enum Rates
	{
		AUTO("Match my account"),
		MAIN("Normal account"),
		IRON("Ironman");

		private final String label;

		Rates(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigSection(
		name = "New slots",
		description = "What Log Insight does when a new collection log slot drops",
		position = 0
	)
	String finds = "finds";

	@ConfigSection(
		name = "While you grind",
		description = "What Log Insight shows while you add kill counts",
		position = 1
	)
	String grind = "grind";

	@ConfigSection(
		name = "Estimates",
		description = "How Log Insight estimates time",
		position = 2
	)
	String estimates = "estimates";

	@ConfigSection(
		name = "Cards",
		description = "What goes on the finest finds card you save or copy",
		position = 3
	)
	String cards = "cards";

	@ConfigItem(
		keyName = "showPlaque",
		name = "Show plaque",
		description = "Shows a plaque in the game view with the new slot's odds and how lucky it was",
		position = 1,
		section = finds
	)
	default boolean showPlaque()
	{
		return true;
	}

	@Range(min = 3, max = 30)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "plaqueSeconds",
		name = "Plaque time",
		description = "How long the new slot plaque stays up",
		position = 2,
		section = finds
	)
	default int plaqueSeconds()
	{
		return 8;
	}

	@ConfigItem(
		keyName = "chatLine",
		name = "Luck in chat",
		description = "Adds a line to your chatbox saying how lucky each new slot was",
		position = 3,
		section = finds
	)
	default boolean chatLine()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dryMilestones",
		name = "Dry milestones in chat",
		description = "Adds a chatbox line when a kill takes you past the count by which 9 in 10, then 99 in 100, players would have a missing slot",
		position = 2,
		section = grind
	)
	default boolean dryMilestones()
	{
		return true;
	}

	@ConfigItem(
		keyName = "grindCard",
		name = "Grind card",
		description = "While you add kill counts to a page, shows a small card with the count and the wait for its next slot; it hides five minutes after your last kill",
		position = 1,
		section = grind
	)
	default boolean grindCard()
	{
		return true;
	}

	@ConfigItem(
		keyName = "nameOnCard",
		name = "Name on cards",
		description = "Write your character's name on the finest finds card; turn off to share it without it",
		position = 1,
		section = cards
	)
	default boolean nameOnCard()
	{
		return true;
	}

	@ConfigItem(
		keyName = "rates",
		name = "Kill rates",
		description = "Which typical kills per hour to estimate with: a normal account's or an ironman's",
		position = 1,
		section = estimates
	)
	default Rates rates()
	{
		return Rates.AUTO;
	}

	@ConfigItem(
		keyName = "learnPace",
		name = "Learn my pace",
		description = "Times your kill counts and uses your own pace once Log Insight has seen enough of them",
		position = 2,
		section = estimates
	)
	default boolean learnPace()
	{
		return true;
	}
}
