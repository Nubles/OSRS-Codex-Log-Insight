package com.codex.loginsight.core;

import com.codex.loginsight.data.ItemOdds;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Works out which slot a "new item" announcement means. The game names the item but not the slot, and about twenty
 * names belong to several slots (Mysterious page, Graceful hood, Rum, the Castle Wars decorative pieces). Taking the
 * first one still missing would mark the wrong slot owned, and nothing would ever take it back.
 */
public final class SlotResolver
{
	private SlotResolver()
	{
	}

	public static final class Result
	{
		/** The slot to mark owned, or -1 when the announcement cannot say which slot it was. */
		public final int owned;
		/** The slot to show on the plaque and in recent finds: the owned one, or one of the candidates. */
		public final int shown;

		Result(int owned, int shown)
		{
			this.owned = owned;
			this.shown = shown;
		}

		public boolean ambiguous()
		{
			return owned < 0 && shown > 0;
		}
	}

	/**
	 * @param ids every slot with the announced name
	 * @param owned slots already owned
	 * @param odds a slot's odds, or null
	 * @param recentLabel the counter that rose just before the announcement, or null
	 */
	public static Result resolve(List<Integer> ids, Set<Integer> owned, IntFunction<ItemOdds> odds, String recentLabel)
	{
		if (ids.isEmpty())
		{
			return new Result(-1, -1);
		}
		List<Integer> missing = new ArrayList<>();
		for (int id : ids)
		{
			if (!owned.contains(id))
			{
				missing.add(id);
			}
		}
		if (missing.isEmpty())
		{
			// Every slot with this name is owned already: nothing new to mark, but the find is still shown.
			return new Result(ids.get(0), ids.get(0));
		}
		if (missing.size() == 1)
		{
			return new Result(missing.get(0), missing.get(0));
		}
		List<Integer> fromKill = new ArrayList<>();
		if (recentLabel != null)
		{
			for (int id : missing)
			{
				if (rolledBy(odds.apply(id), recentLabel))
				{
					fromKill.add(id);
				}
			}
			if (fromKill.size() == 1)
			{
				return new Result(fromKill.get(0), fromKill.get(0));
			}
		}
		// Still more than one: mark none. The game's own slot count then runs ahead of Log Insight's, and the Ledger
		// asks for the log to be read again, which settles it.
		return new Result(-1, fromKill.isEmpty() ? missing.get(0) : fromKill.get(0));
	}

	private static boolean rolledBy(ItemOdds odds, String label)
	{
		if (odds == null)
		{
			return false;
		}
		for (ItemOdds.Roll roll : odds.getRolls())
		{
			if (roll.getLabel().equals(label))
			{
				return true;
			}
		}
		return false;
	}
}
