package com.codex.loginsight.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import java.util.function.Function;

/**
 * Reads saved JSON so that damage costs only the damaged part. A value Gson cannot read as a whole is taken apart
 * field by field, entry by entry, and every piece that reads on its own is kept; the rest is dropped and the result
 * says so, so the caller can keep the original text aside rather than save over it. Nothing here throws.
 */
public final class SavedJson
{
	/** How deep into lists and objects a damaged value is taken apart. */
	private static final int MAX_DEPTH = 4;
	/** A number written with more characters than this is not a time, a count or an amount. */
	private static final int MAX_NUMBER_LENGTH = 40;

	/** What a read gave: the value (null when nothing could be read) and whether anything was left out. */
	public static final class Read<T>
	{
		private final T value;
		private final boolean damaged;

		Read(T value, boolean damaged)
		{
			this.value = value;
			this.damaged = damaged;
		}

		/** The value read, or null when there was nothing saved or nothing in it could be read. */
		public T getValue()
		{
			return value;
		}

		/** True when something saved could not be read and was left out, so the saved text should be kept aside. */
		public boolean isDamaged()
		{
			return damaged;
		}
	}

	private SavedJson()
	{
	}

	/** Parses saved JSON into a tree, or null when it is empty or not JSON at all. */
	public static JsonElement tree(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			return gson.fromJson(json, JsonElement.class);
		}
		catch (JsonParseException | IllegalStateException | NumberFormatException | StackOverflowError e)
		{
			// StackOverflowError: a value nested thousands deep is damage, and Gson reads trees recursively.
			return null;
		}
	}

	/**
	 * Reads a saved object of {@code type}, keeping every field and entry that reads on its own. Empty or missing
	 * text gives no value and no damage; text that is not a JSON object gives no value and is damaged.
	 */
	public static <T> Read<T> read(Gson gson, String json, Class<T> type)
	{
		if (json == null || json.trim().isEmpty())
		{
			return new Read<>(null, false);
		}
		T whole = null;
		try
		{
			// The usual case, read straight from the text: Gson's text reader never expands a huge exponent.
			whole = gson.fromJson(json, type);
		}
		catch (RuntimeException | StackOverflowError e)
		{
			// Taken apart below.
		}
		JsonElement root = tree(gson, json);
		if (whole != null)
		{
			// Read without an error, but a newer version's fields (or a kind of entry this one does not know) are
			// passed over in silence and would be lost at the next save: flagged, so the text is kept aside.
			return new Read<>(whole, root == null || !covers(root, toTree(gson, whole)));
		}
		if (root == null || !root.isJsonObject())
		{
			return new Read<>(null, true);
		}
		boolean[] damaged = {false};
		JsonElement kept = salvage(gson, type, root, x -> x, 0, damaged);
		T value = kept == null ? null : parse(gson, kept, type);
		return new Read<>(value, true);
	}

	/**
	 * The part of {@code value} that reads where {@code wrap} puts it in the whole: all of it when it reads as it
	 * is, else each list entry or object field that reads on its own; null when none of it does.
	 */
	private static <T> JsonElement salvage(Gson gson, Class<T> type, JsonElement value,
		Function<JsonElement, JsonElement> wrap, int depth, boolean[] damaged)
	{
		if (parse(gson, wrap.apply(value), type) != null)
		{
			return value;
		}
		damaged[0] = true;
		if (depth >= MAX_DEPTH)
		{
			return null;
		}
		if (value.isJsonArray())
		{
			JsonArray out = new JsonArray();
			for (JsonElement item : value.getAsJsonArray())
			{
				JsonElement kept = salvage(gson, type, item, x ->
				{
					JsonArray one = new JsonArray();
					one.add(x);
					return wrap.apply(one);
				}, depth + 1, damaged);
				if (kept != null)
				{
					out.add(kept);
				}
			}
			return parse(gson, wrap.apply(out), type) != null ? out : null;
		}
		if (value.isJsonObject())
		{
			JsonObject out = new JsonObject();
			for (Map.Entry<String, JsonElement> field : value.getAsJsonObject().entrySet())
			{
				String name = field.getKey();
				JsonElement kept = salvage(gson, type, field.getValue(), x ->
				{
					JsonObject one = new JsonObject();
					one.add(name, x);
					return wrap.apply(one);
				}, depth + 1, damaged);
				if (kept != null)
				{
					out.add(name, kept);
				}
			}
			return parse(gson, wrap.apply(out), type) != null ? out : null;
		}
		return null;
	}

	/** {@code value} as Gson would save it, or null when it cannot be written. */
	private static JsonElement toTree(Gson gson, Object value)
	{
		try
		{
			return gson.toJsonTree(value);
		}
		catch (RuntimeException | StackOverflowError e)
		{
			return null;
		}
	}

	/**
	 * True when every field and list entry saved in {@code saved} is still there in {@code reread}, the value read
	 * from it written out again: nothing was passed over. Only the shape is compared, not the figures, which the
	 * reader may round or bring back to sense. A field saved as null was nothing to lose.
	 */
	static boolean covers(JsonElement saved, JsonElement reread)
	{
		if (saved == null || saved.isJsonNull())
		{
			return true;
		}
		if (reread == null || reread.isJsonNull())
		{
			return false;
		}
		if (saved.isJsonObject())
		{
			if (!reread.isJsonObject())
			{
				return false;
			}
			JsonObject now = reread.getAsJsonObject();
			for (Map.Entry<String, JsonElement> field : saved.getAsJsonObject().entrySet())
			{
				if (!field.getValue().isJsonNull() && !covers(field.getValue(), now.get(field.getKey())))
				{
					return false;
				}
			}
			return true;
		}
		if (saved.isJsonArray())
		{
			if (!reread.isJsonArray() || reread.getAsJsonArray().size() < saved.getAsJsonArray().size())
			{
				return false;
			}
			JsonArray then = saved.getAsJsonArray();
			JsonArray now = reread.getAsJsonArray();
			for (int i = 0; i < then.size(); i++)
			{
				if (!covers(then.get(i), now.get(i)))
				{
					return false;
				}
			}
			return true;
		}
		return true;
	}

	private static <T> T parse(Gson gson, JsonElement tree, Class<T> type)
	{
		try
		{
			return gson.fromJson(tree, type);
		}
		catch (RuntimeException | StackOverflowError e)
		{
			return null;
		}
	}

	/** A field of an object as a whole number, or -1 when it is missing, not a number, or beyond a long. */
	public static long number(JsonObject o, String key)
	{
		return o == null ? -1 : number(o.get(key));
	}

	/**
	 * A saved whole number, or -1 when it is missing, not a number, or beyond a long. Read from its text: Gson's own
	 * conversion of a number like {@code 1e100000000} expands every digit on older Java, which can freeze the client.
	 */
	public static long number(JsonElement e)
	{
		double d = decimal(e);
		if (Double.isNaN(d))
		{
			return -1;
		}
		try
		{
			return Long.parseLong(e.getAsString());
		}
		catch (NumberFormatException ex)
		{
			// Written with a point or an exponent ("1.5e9"): whole numbers within a long still count.
			return Double.isInfinite(d) || d < Long.MIN_VALUE || d >= Long.MAX_VALUE ? -1 : (long) d;
		}
	}

	/** A saved number as a double, or NaN when it is missing or not a number; Infinity for one too big to hold. */
	public static double decimal(JsonElement e)
	{
		if (e == null || !e.isJsonPrimitive() || !((JsonPrimitive) e).isNumber())
		{
			return Double.NaN;
		}
		String text = e.getAsString();
		if (text.length() > MAX_NUMBER_LENGTH)
		{
			return Double.NaN;
		}
		try
		{
			return Double.parseDouble(text);
		}
		catch (NumberFormatException ex)
		{
			return Double.NaN;
		}
	}
}
