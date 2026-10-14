package com.codex.loginsight.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * The shape of the collection log as the game defines it: tabs, their pages and the
 * slots on each page, with item names. Read from the game cache once per session, so
 * new pages and slots appear without a plugin update.
 */
public final class LogStructure
{
	@Getter
	private final List<Tab> tabs;
	private final Map<Integer, String> names;
	private final Map<String, List<Integer>> idsByName;
	private final Map<Integer, List<Page>> pagesByItem;
	@Getter
	private final int uniqueSlots;

	public LogStructure(List<Tab> tabs, Map<Integer, String> names)
	{
		this.tabs = Collections.unmodifiableList(new ArrayList<>(tabs));
		this.names = Collections.unmodifiableMap(new HashMap<>(names));
		Map<String, List<Integer>> byName = new HashMap<>();
		Map<Integer, List<Page>> byItem = new HashMap<>();
		Set<Integer> unique = new LinkedHashSet<>();
		for (Tab tab : this.tabs)
		{
			for (Page page : tab.getPages())
			{
				for (int id : page.getItems())
				{
					unique.add(id);
					byItem.computeIfAbsent(id, k -> new ArrayList<>()).add(page);
				}
			}
		}
		for (int id : unique)
		{
			byName.computeIfAbsent(key(name(id)), k -> new ArrayList<>()).add(id);
		}
		this.idsByName = byName;
		this.pagesByItem = byItem;
		this.uniqueSlots = unique.size();
	}

	public static LogStructure empty()
	{
		return new LogStructure(Collections.emptyList(), Collections.emptyMap());
	}

	public boolean isEmpty()
	{
		return tabs.isEmpty();
	}

	public String name(int itemId)
	{
		String name = names.get(itemId);
		return name == null ? "Item " + itemId : name;
	}

	/** Slots with this exact name; several when variants share one, such as coloured ornaments. */
	public List<Integer> idsNamed(String name)
	{
		List<Integer> ids = idsByName.get(key(name));
		return ids == null ? Collections.emptyList() : ids;
	}

	public List<Page> pagesOf(int itemId)
	{
		List<Page> pages = pagesByItem.get(itemId);
		return pages == null ? Collections.emptyList() : pages;
	}

	public Page page(String name)
	{
		for (Tab tab : tabs)
		{
			for (Page page : tab.getPages())
			{
				if (page.getName().equalsIgnoreCase(name))
				{
					return page;
				}
			}
		}
		return null;
	}

	private static String key(String name)
	{
		return name.trim().toLowerCase(Locale.ENGLISH);
	}

	@Getter
	public static final class Tab
	{
		private final String name;
		private final List<Page> pages;

		public Tab(String name, List<Page> pages)
		{
			this.name = name;
			this.pages = Collections.unmodifiableList(new ArrayList<>(pages));
		}
	}

	@Getter
	public static final class Page
	{
		private final String tab;
		private final String name;
		private final List<Integer> items;

		public Page(String tab, String name, List<Integer> items)
		{
			this.tab = tab;
			this.name = name;
			this.items = Collections.unmodifiableList(new ArrayList<>(items));
		}
	}
}
