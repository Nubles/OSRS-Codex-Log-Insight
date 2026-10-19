package com.codex.loginsight.core;

import lombok.Getter;

/**
 * One missing pet as a hunt: where it comes quickest, how long half and nine in ten players
 * take to see it there, and what else that activity is likely to give along the way.
 */
@Getter
public final class PetPlan
{
	private final int itemId;
	private final String name;
	private final String page;
	/** The pet alone at its quickest source. */
	private final Prospect hunt;
	/** Everything else still missing at that source. */
	private final Prospect alongside;
	/** Chance per attempt, for "1 in N". */
	private final double chance;
	private final double median;
	private final double likely;

	public PetPlan(int itemId, String name, String page, Prospect hunt, Prospect alongside, double chance)
	{
		this.itemId = itemId;
		this.name = name;
		this.page = page;
		this.hunt = hunt;
		this.alongside = alongside;
		this.chance = chance;
		this.median = hunt.hoursToAll(0.5);
		this.likely = hunt.hoursToAll(0.9);
	}

	public String getSource()
	{
		return hunt.getName();
	}

	public String getLockedBy()
	{
		return hunt.getLockedBy();
	}

	/** The chance of the pet within this many hours of hunting it. */
	public double chanceWithin(double hours)
	{
		return hunt.chanceOfAll(hours);
	}

	/** Other new slots an average player picks up in the hours it takes half of them to see the pet. */
	public double slotsAlongside()
	{
		return Double.isFinite(median) ? alongside.expectedSlots(median) : 0;
	}
}
