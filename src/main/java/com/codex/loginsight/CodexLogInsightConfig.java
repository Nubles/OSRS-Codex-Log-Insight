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
		name = "Estimates",
		description = "How Log Insight estimates time",
		position = 1
	)
	String estimates = "estimates";

	@ConfigItem(
		keyName = "showPlaque",
		name = "Show plaque",
		description = "Show a plaque in the game view with the slot's odds and how lucky it was",
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
		description = "How long the plaque stays up",
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
		description = "Add a line to the chat box saying how lucky each new slot was",
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
		description = "Add a line to the chat box when a kill takes you past the count by which 9 in 10, then 99 in 100, players would have a slot you are missing",
		position = 4,
		section = finds
	)
	default boolean dryMilestones()
	{
		return true;
	}

	@ConfigItem(
		keyName = "grindCard",
		name = "Grind card",
		description = "While you add kill counts to a page, show a small card in the game view with the count, the wait for the page's next slot and the slot most worth watching. It hides five minutes after the last kill count",
		position = 5,
		section = finds
	)
	default boolean grindCard()
	{
		return true;
	}

	@ConfigItem(
		keyName = "rates",
		name = "Rates",
		description = "Typical kills per hour for a normal account or an ironman",
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
		description = "Time your kill counts and use your own pace once Log Insight has seen enough of them",
		position = 2,
		section = estimates
	)
	default boolean learnPace()
	{
		return true;
	}
}
