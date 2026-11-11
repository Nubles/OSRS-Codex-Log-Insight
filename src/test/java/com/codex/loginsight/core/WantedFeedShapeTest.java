package com.codex.loginsight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

/**
 * Pins {@code codexloginsight/wantedV1} (contract N6) and {@code codexloginsight/goalOffersV1} (N5), which Navigator,
 * Autopilot, Home, Boss Pilot and Session Chronicle read without a compile-time link: renaming or reshaping a field
 * breaks them silently, so it fails here first. {@code wantedV1.json} and {@code goalOffersV1.json} beside this test are
 * the writer's exact bytes; readers keep byte copies of them for their own fixture tests.
 */
public class WantedFeedShapeTest
{
	private static final long SAVED_AT = 1_790_000_000_000L;
	/** Zulrah rolls the tanzanite fang twice at 1/1,024 (the bundled odds), so about 1/512 a kill. */
	private static final double FANG = 1 - Math.pow(1 - 1 / 1024.0, 2);

	private static Report.ItemReport fang()
	{
		double dry = Math.pow(1 - 1 / 1024.0, 2 * 612);
		return new Report.ItemReport(12922, "Tanzanite fang", "Zulrah", false, false, FANG, "Zulrah kills", 612, dry,
			"Killing zulrah", 1 / FANG / 40);
	}

	/** A slot the log keeps no counter for: timed by the activity it comes quickest from. */
	private static Report.ItemReport uncounted()
	{
		return new Report.ItemReport(23336, "3rd age druidic robe top", "Master Treasure Trails", false, false, 1 / 100.0,
			null, -1, -1, "Opening master caskets (rare table)", 250);
	}

	/** A slot with no odds and no timed source at all. */
	private static Report.ItemReport unrated()
	{
		return new Report.ItemReport(4567, "Mystery slot", null, false, false, 0, null, -1, -1, null,
			Double.POSITIVE_INFINITY);
	}

	private static List<Report.ItemReport> wanted()
	{
		return Arrays.asList(fang(), uncounted(), unrated());
	}

	@Test
	public void theWantedListIsWrittenByteForByte() throws IOException
	{
		String json = new Gson().toJson(WantedFeed.of(wanted(), SAVED_AT));
		assertEquals(fixture("wantedV1.json"), json);
	}

	@Test
	public void theGoalOffersAreWrittenByteForByte() throws IOException
	{
		String json = new Gson().toJson(WantedFeed.of(wanted(), SAVED_AT).offers());
		assertEquals(fixture("goalOffersV1.json"), json);
	}

	@Test
	public void everyContractFieldIsThereWithItsType()
	{
		JsonObject root = new JsonParser().parse(new Gson().toJson(WantedFeed.of(wanted(), SAVED_AT))).getAsJsonObject();
		assertEquals(1, root.get("version").getAsInt());
		assertEquals(SAVED_AT, root.get("savedAt").getAsLong());
		JsonArray list = root.getAsJsonArray("wanted");
		assertEquals(3, list.size());
		JsonObject fang = list.get(0).getAsJsonObject();
		assertEquals(12922, fang.get("itemId").getAsInt());
		assertEquals("Tanzanite fang", fang.get("name").getAsString());
		assertEquals("the counter's own name, so a boss plugin can match it", "Zulrah", fang.get("source").getAsString());
		assertEquals(612, fang.get("kc").getAsInt());
		assertEquals("1/512", fang.get("rate").getAsString());
		// ceil(ln 0.5 / ln(1 - c)) and ceil(ln 0.1 / ln(1 - c)) at the two-roll chance c: counts on the same counter,
		// not kills from now.
		assertEquals(355, fang.get("p50Kills").getAsInt());
		assertEquals(1179, fang.get("p90Kills").getAsInt());
		double soFar = fang.get("chanceSoFar").getAsDouble();
		assertEquals(1 - Math.pow(1 - 1 / 1024.0, 2 * 612), soFar, 1e-4);
		assertEquals("Zulrah", fang.get("page").getAsString());
		assertEquals(1 / FANG / 40, fang.get("hours").getAsDouble(), 0.05);

		JsonObject robe = list.get(1).getAsJsonObject();
		assertEquals("no counter: the activity, without its verb or bracket", "Master caskets", robe.get("source").getAsString());
		assertEquals(-1, robe.get("kc").getAsInt());
		assertEquals("1/100", robe.get("rate").getAsString());
		assertEquals(-1, robe.get("p50Kills").getAsInt());
		assertEquals(-1, robe.get("p90Kills").getAsInt());
		assertEquals(-1, robe.get("chanceSoFar").getAsDouble(), 0);

		JsonObject mystery = list.get(2).getAsJsonObject();
		assertFalse("an unknown rate is left out, never written as 1/0", mystery.has("rate"));
		assertFalse(mystery.has("source"));
		assertFalse("an endless wait is left out, never written as Infinity", mystery.has("hours"));
	}

	@Test
	public void anOwnedSlotIsNeverShared()
	{
		Report.ItemReport owned = new Report.ItemReport(12922, "Tanzanite fang", "Zulrah", true, false, FANG,
			"Zulrah kills", 612, -1, "Killing zulrah", 12);
		assertTrue(WantedFeed.of(Collections.singletonList(owned), SAVED_AT).getWanted().isEmpty());
		assertEquals("{\"version\":1,\"savedAt\":" + SAVED_AT + ",\"wanted\":[]}",
			new Gson().toJson(WantedFeed.of(Collections.emptyList(), SAVED_AT)));
	}

	@Test
	public void anOfferIsOneSlotWithItsAverageHours()
	{
		WantedFeed.Offer offer = WantedFeed.of(wanted(), SAVED_AT).offers().getOffers().get(0);
		assertEquals("slot:12922", offer.getKey());
		assertEquals("Tanzanite fang (Zulrah)", offer.getTitle());
		assertEquals(1 / FANG / 40, offer.getHoursLeft(), 0.05);
		assertNull(WantedFeed.of(wanted(), SAVED_AT).offers().getOffers().get(2).getHoursLeft());
	}

	@Test
	public void onlyTheTimeChangingLeavesTheBodyAlone()
	{
		Gson gson = new Gson();
		WantedFeed a = WantedFeed.of(wanted(), SAVED_AT);
		WantedFeed b = WantedFeed.of(wanted(), SAVED_AT + 60_000);
		assertEquals(gson.toJson(a.at(0)), gson.toJson(b.at(0)));
		assertFalse(gson.toJson(a).equals(gson.toJson(b)));
	}

	@Test
	public void ratesReadTheSameInEveryLocale()
	{
		Locale before = Locale.getDefault();
		try
		{
			Locale.setDefault(Locale.GERMANY);
			assertEquals("1/3000", WantedFeed.rate(1 / 3000.0));
			assertEquals("1/7.7", WantedFeed.rate(1 / 7.7));
			assertEquals("1/2", WantedFeed.rate(0.5));
			assertNull(WantedFeed.rate(0));
			assertNull(WantedFeed.rate(Double.NaN));
		}
		finally
		{
			Locale.setDefault(before);
		}
	}

	private static String fixture(String name) throws IOException
	{
		try (InputStream in = WantedFeedShapeTest.class.getResourceAsStream(name))
		{
			if (in == null)
			{
				throw new IOException("No fixture " + name);
			}
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int n;
			while ((n = in.read(buffer)) > 0)
			{
				out.write(buffer, 0, n);
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
		}
	}
}
