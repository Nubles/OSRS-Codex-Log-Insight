package com.codex.loginsight.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import com.google.gson.Gson;
import org.junit.Test;

/**
 * The bundled rates the luck oracle quotes, held to the OSRS Wiki's drop tables. Item ids are RuneLite's gameval
 * ItemID names in the comments.
 *
 * <ul>
 * <li>https://oldschool.runescape.wiki/w/Zulrah: uniques "2 x 1/1,024", mutagens "2 x 1/13,107", Jar of swamp
 * "1/3,000", Pet snakeling "1/4,000" (tertiary, one roll).</li>
 * <li>https://oldschool.runescape.wiki/w/Vorkath: Vorkath's head "1/50" and "always" on the 50th kill, Skeletal visage
 * "1/5,000", Dragonbone necklace "1/1,000", Jar of decay "1/3,000", Vorki "1/3,000".</li>
 * <li>https://oldschool.runescape.wiki/w/Alchemical_Hydra: Hydra's claw "1/1,001", Hydra leather "1/514";
 * https://oldschool.runescape.wiki/w/Jar_of_chemicals "1/2,000"; https://oldschool.runescape.wiki/w/Ikkle_Hydra
 * "1/3,000".</li>
 * </ul>
 */
public class WikiRatesTest
{
	private final LogData data;

	public WikiRatesTest() throws Exception
	{
		data = LogData.load(new Gson());
	}

	private void rate(int itemId, String label, double chance, int rolls)
	{
		ItemOdds odds = data.odds(itemId);
		assertNotNull("odds for " + itemId, odds);
		ItemOdds.Roll roll = null;
		for (ItemOdds.Roll r : odds.getRolls())
		{
			if (r.getLabel().equals(label))
			{
				roll = r;
			}
		}
		assertNotNull(label + " roll for " + itemId, roll);
		assertEquals("chance of " + itemId, chance, roll.getChance(), chance * 0.005);
		assertEquals("rolls of " + itemId, rolls, roll.getRolls());
	}

	@Test
	public void zulrah()
	{
		rate(12922, "Zulrah kills", 1 / 1024.0, 2); // BLOWPIPE_FANG (Tanzanite fang)
		rate(12932, "Zulrah kills", 1 / 1024.0, 2); // MAGIC_FANG
		rate(12927, "Zulrah kills", 1 / 1024.0, 2); // SERPENTINE_VISAGE
		rate(13200, "Zulrah kills", 1 / 13107.0, 2); // CYAN_MUTAGEN (Tanzanite mutagen)
		rate(13201, "Zulrah kills", 1 / 13107.0, 2); // RED_MUTAGEN (Magma mutagen)
		rate(12936, "Zulrah kills", 1 / 3000.0, 1); // JAR_OF_SWAMP
		rate(12921, "Zulrah kills", 1 / 4000.0, 1); // SNAKEPET (Pet snakeling)
	}

	@Test
	public void vorkath()
	{
		rate(21907, "Vorkath kills", 1 / 50.0, 1); // VORKATH_HEAD
		assertEquals(50, data.odds(21907).getGuaranteedBy());
		rate(22006, "Vorkath kills", 1 / 5000.0, 1); // SKELETAL_VISAGE
		rate(22111, "Vorkath kills", 1 / 1000.0, 1); // DRAGONBONE_NECKLACE
		rate(22106, "Vorkath kills", 1 / 3000.0, 1); // JAR_OF_DECAY
		rate(21992, "Vorkath kills", 1 / 3000.0, 1); // VORKATHPET (Vorki)
	}

	@Test
	public void alchemicalHydra()
	{
		rate(22966, "Alchemical Hydra kills", 1 / 1001.0, 1); // HYDRA_CLAW
		rate(22983, "Alchemical Hydra kills", 1 / 514.0, 1); // HYDRA_LEATHER
		rate(23064, "Alchemical Hydra kills", 1 / 2000.0, 1); // JAR_OF_CHEMICALS
		rate(22746, "Alchemical Hydra kills", 1 / 3000.0, 1); // HYDRAPET (Ikkle hydra)
	}
}
