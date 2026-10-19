package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.List;
import org.junit.Test;

public class TrophiesTest
{
	private static Progress.Find find(int id, double chance, double luckier)
	{
		return new Progress.Find(id, "Item " + id, "Page", "Page kills", 100, chance, luckier, id);
	}

	@Test
	public void theRarestStayOnTheShelfRarestFirst()
	{
		Progress p = new Progress();
		p.addFind(find(1, 1 / 512.0, 0.4));
		p.addFind(find(2, 1 / 5000.0, 0.97));
		p.addFind(find(3, 1 / 10_000.0, -1));
		p.addFind(find(4, 0, -1));
		List<Progress.Find> shelf = p.getTrophies();
		assertEquals(3, shelf.size());
		assertEquals(3, shelf.get(0).getItemId());
		assertEquals(2, shelf.get(1).getItemId());
		assertEquals(1, shelf.get(2).getItemId());
	}

	@Test
	public void aGreatDropIsNotPushedOffByOrdinaryOnes()
	{
		Progress p = new Progress();
		p.addFind(find(99, 1 / 5000.0, 0.9));
		for (int i = 0; i < 60; i++)
		{
			p.addFind(find(1000 + i, 1 / 20.0, 0.5));
		}
		// Forty recent finds are kept; the visage dropped out of them long ago but keeps its place on the shelf.
		assertTrue(p.getFinds().stream().noneMatch(f -> f.getItemId() == 99));
		assertEquals(99, p.getTrophies().get(0).getItemId());
		assertEquals(Progress.TROPHIES, p.getTrophies().size());
	}

	@Test
	public void sameOddsGoToTheLuckierFind()
	{
		Progress p = new Progress();
		p.addFind(find(1, 1 / 1000.0, 0.2));
		p.addFind(find(2, 1 / 1000.0, 0.9));
		assertEquals(2, p.getTrophies().get(0).getItemId());
	}

	@Test
	public void anOlderSaveStartsItsShelfFromItsFinds()
	{
		Gson gson = new Gson();
		// Saved before trophies existed: the field is absent.
		String old = "{\"owned\":[1],\"finds\":[{\"itemId\":7,\"name\":\"Visage\",\"chance\":2.0E-4,\"luckier\":0.9,\"count\":5,\"at\":1}]}";
		Progress p = gson.fromJson(old, Progress.class);
		assertEquals(7, p.getTrophies().get(0).getItemId());
		// And once saved again the shelf round-trips on its own.
		Progress back = gson.fromJson(gson.toJson(p), Progress.class);
		assertEquals(1, back.getTrophies().size());
		assertEquals(1, p.copy().getTrophies().size());
	}
}
