package com.codex.loginsight;

import com.codex.loginsight.core.Analyst;
import com.codex.loginsight.core.Announcements;
import com.codex.loginsight.core.ChatReader;
import com.codex.loginsight.core.LogStructure;
import com.codex.loginsight.core.Luck;
import com.codex.loginsight.core.Progress;
import com.codex.loginsight.core.Report;
import com.codex.loginsight.core.Unlocks;
import com.codex.loginsight.data.Activity;
import com.codex.loginsight.data.ItemOdds;
import com.codex.loginsight.data.LogData;
import com.codex.loginsight.overlay.FindPlaque;
import com.codex.loginsight.ui.Format;
import com.codex.loginsight.ui.ItemIcons;
import com.codex.loginsight.ui.LogInsightPanel;
import com.codex.loginsight.ui.Sigil;
import com.codex.loginsight.ui.Theme;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.StructComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Codex Log Insight",
	description = "Collection log analytics: your luck at your kill counts, time left on each page and the fastest slots to chase",
	tags = {"collection", "log", "clog", "luck", "dry", "drop", "rate", "pet", "codex"}
)
public class CodexLogInsightPlugin extends Plugin
{
	private static final String PROGRESS_KEY = "progress";
	/** The script that sends the client every owned slot when the log's Search is opened. */
	private static final int COLLECTION_TRANSMIT = 4100;
	/** Enum of the log's tabs; each tab struct points to its pages, each page to its slots. */
	private static final int TAB_ENUM = 2102;
	private static final int TAB_PAGES_PARAM = 683;
	private static final int PAGE_NAME_PARAM = 689;
	private static final int PAGE_ITEMS_PARAM = 690;
	/** Slots whose id the log swaps for a stand-in, mapping the stand-in back. */
	private static final int REPLACEMENT_ENUM = 3721;
	private static final String[] TAB_NAMES = {"Bosses", "Raids", "Clues", "Minigames", "Other"};
	private static final int TRANSMIT_SETTLE_TICKS = 3;
	private static final int SAVE_TICKS = 100;
	private static final int ANALYSE_GAP_TICKS = 2;
	private static final String POPUP_PREFIX = "New item:";
	/** Skill levels and quests are read again this often, to notice what has opened up. */
	private static final int UNLOCKS_TICKS = 100;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private Gson gson;

	@Inject
	private CodexLogInsightConfig config;

	private final FindPlaque plaque = new FindPlaque();
	private final AtomicInteger generation = new AtomicInteger();
	private final Set<Integer> harvest = new HashSet<>();
	private final Map<Integer, Integer> replacements = new HashMap<>();

	private LogData data;
	private Analyst analyst;
	private ChatReader chat;
	private LogInsightPanel panel;
	private NavigationButton navButton;

	private LogStructure structure = LogStructure.empty();
	private Progress progress;
	private String profile;
	private boolean dirty;
	private boolean unsaved;
	private int ticksSinceSave;
	private int ticksSinceAnalysis;
	private int lastTransmitTick = -1;
	private int officialOwned = -1;
	private int officialTotal = -1;
	private final Map<String, Quest> questsNeeded = new HashMap<>();
	private Unlocks unlocks = Unlocks.UNKNOWN;
	private int unlocksReadAt = -1;
	private boolean notificationStarted;
	/** The chat line and the pop-up can both announce one slot; the second is ignored. */
	private final Announcements announcements = new Announcements();

	@Provides
	CodexLogInsightConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CodexLogInsightConfig.class);
	}

	@Override
	protected void startUp() throws IOException
	{
		data = LogData.load(gson);
		analyst = new Analyst(data);
		chat = new ChatReader(data.getLabels());
		Map<String, Quest> quests = new HashMap<>();
		for (Quest quest : Quest.values())
		{
			quests.putIfAbsent(Unlocks.normalise(quest.name()), quest);
			quests.putIfAbsent(Unlocks.normalise(quest.getName()), quest);
		}
		for (String name : data.questsNeeded())
		{
			Quest quest = quests.get(Unlocks.normalise(name));
			if (quest != null)
			{
				questsNeeded.put(name, quest);
			}
		}
		ItemIcons icons = (itemId, onLoaded) ->
		{
			AsyncBufferedImage image = itemManager.getImage(itemId);
			image.onLoaded(onLoaded);
			return image;
		};
		panel = new LogInsightPanel(icons);
		navButton = NavigationButton.builder()
			.tooltip("Codex Log Insight")
			.icon(Sigil.icon(16))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(plaque);
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadProgress();
				buildStructure();
				dirty = true;
			}
		});
	}

	@Override
	protected void shutDown()
	{
		clientThread.invokeLater(() ->
		{
			save();
			progress = null;
			profile = null;
		});
		overlayManager.remove(plaque);
		clientToolbar.removeNavigation(navButton);
		plaque.clear();
		generation.incrementAndGet();
		structure = LogStructure.empty();
		harvest.clear();
		lastTransmitTick = -1;
		panel = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			harvest.clear();
			lastTransmitTick = -1;
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			save();
			progress = null;
			profile = null;
			officialOwned = -1;
			officialTotal = -1;
			unlocks = Unlocks.UNKNOWN;
			unlocksReadAt = -1;
			announcements.clear();
			plaque.clear();
			publish(Report.loggedOut(), null);
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		save();
		progress = null;
		loadProgress();
		dirty = true;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CodexLogInsightConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if (!config.showPlaque())
		{
			plaque.clear();
		}
		dirty = true;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		if (progress == null)
		{
			loadProgress();
			dirty |= progress != null;
		}
		if (structure.isEmpty())
		{
			buildStructure();
			dirty |= !structure.isEmpty();
		}
		if (lastTransmitTick >= 0 && client.getTickCount() - lastTransmitTick > TRANSMIT_SETTLE_TICKS)
		{
			finishFullRead();
		}

		int owned = client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
		int total = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);
		if (total > 0 && (owned != officialOwned || total != officialTotal))
		{
			officialOwned = owned;
			officialTotal = total;
			dirty = true;
		}

		if (progress != null && (unlocksReadAt < 0 || client.getTickCount() - unlocksReadAt >= UNLOCKS_TICKS))
		{
			readUnlocks();
		}

		ticksSinceAnalysis++;
		if (dirty && ticksSinceAnalysis >= ANALYSE_GAP_TICKS)
		{
			dirty = false;
			ticksSinceAnalysis = 0;
			analyse();
		}
		if (unsaved && ++ticksSinceSave >= SAVE_TICKS)
		{
			save();
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.NOTIFICATION_START)
		{
			notificationStarted = true;
			return;
		}
		if (event.getScriptId() == ScriptID.NOTIFICATION_DELAY)
		{
			readNotification();
			return;
		}
		if (event.getScriptId() != COLLECTION_TRANSMIT || progress == null || isAnotherPlayersLog())
		{
			return;
		}
		ScriptEvent script = event.getScriptEvent();
		Object[] args = script == null ? null : script.getArguments();
		if (args == null || args.length < 3 || !(args[1] instanceof Integer) || !(args[2] instanceof Integer))
		{
			return;
		}
		int itemId = (Integer) args[1];
		int quantity = (Integer) args[2];
		if (itemId > 0 && quantity > 0)
		{
			harvest.add(replacements.getOrDefault(itemId, itemId));
			lastTransmitTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.COLLECTION_DRAW_LIST)
		{
			// The page's header and slots are filled in after the script returns.
			clientThread.invokeLater(this::readOpenPage);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		if (progress == null || type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM
			&& type != ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			return;
		}
		String message = event.getMessage();
		ChatReader.Count count = chat.count(message);
		if (count != null && count.value >= 0)
		{
			progress.setCounter(count.label, count.value);
			if (config.learnPace())
			{
				progress.paceFor(count.label).record(count.value, System.currentTimeMillis());
			}
			changed();
			return;
		}
		String slot = ChatReader.newSlot(message);
		if (slot != null)
		{
			announced(slot, Announcements.Channel.CHAT);
		}
	}

	/** The game's pop-up for a new slot, for players who turned the chat line off. */
	private void readNotification()
	{
		if (!notificationStarted)
		{
			return;
		}
		notificationStarted = false;
		String title = client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE);
		String body = client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN);
		if (progress == null || title == null || body == null || !"Collection log".equalsIgnoreCase(title.trim()))
		{
			return;
		}
		String text = Text.removeTags(body).trim();
		if (text.startsWith(POPUP_PREFIX))
		{
			announced(text.substring(POPUP_PREFIX.length()).trim(), Announcements.Channel.POPUP);
		}
	}

	private void announced(String slot, Announcements.Channel channel)
	{
		if (!announcements.fresh(slot, channel, System.currentTimeMillis()))
		{
			return;
		}
		onNewSlot(slot.trim());
	}

	/** Reads the account's real skill levels and which needed quests are still unfinished. */
	private void readUnlocks()
	{
		unlocksReadAt = client.getTickCount();
		Map<String, Integer> levels = new HashMap<>();
		for (Skill skill : Skill.values())
		{
			levels.put(skill.name(), client.getRealSkillLevel(skill));
		}
		Set<String> unfinished = new HashSet<>();
		for (Map.Entry<String, Quest> quest : questsNeeded.entrySet())
		{
			if (quest.getValue().getState(client) != QuestState.FINISHED)
			{
				unfinished.add(quest.getKey());
			}
		}
		Unlocks read = new Unlocks(levels, unfinished);
		if (!read.equals(unlocks))
		{
			unlocks = read;
			dirty = true;
		}
	}

	/** Reads the page open in the collection log: its counters and which slots are owned. */
	private void readOpenPage()
	{
		if (progress == null || isAnotherPlayersLog())
		{
			return;
		}
		Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		Widget[] lines = header == null ? null : header.getDynamicChildren();
		if (lines == null || lines.length == 0 || lines[0] == null)
		{
			return;
		}
		String page = Text.removeTags(lines[0].getText()).trim();
		if (page.isEmpty())
		{
			return;
		}
		List<String> labels = new ArrayList<>();
		for (int i = 1; i < lines.length; i++)
		{
			ChatReader.Count counter = lines[i] == null ? null : ChatReader.pageCounter(lines[i].getText());
			if (counter != null)
			{
				labels.add(counter.label);
				progress.setCounter(counter.label, counter.value);
			}
		}
		Widget items = client.getWidget(InterfaceID.Collection.ITEMS);
		Widget[] slots = items == null ? null : items.getDynamicChildren();
		if (slots != null)
		{
			for (Widget slot : slots)
			{
				// The log draws owned slots solid and missing ones faded.
				if (slot != null && slot.getItemId() > 0 && slot.getOpacity() == 0)
				{
					progress.addOwned(replacements.getOrDefault(slot.getItemId(), slot.getItemId()));
				}
			}
		}
		progress.setPageLabels(page, labels);
		progress.markPageRead(page, System.currentTimeMillis());
		changed();
	}

	private void finishFullRead()
	{
		lastTransmitTick = -1;
		if (progress == null || harvest.isEmpty())
		{
			harvest.clear();
			return;
		}
		for (int id : harvest)
		{
			progress.addOwned(id);
		}
		harvest.clear();
		progress.setFullReadAt(System.currentTimeMillis());
		changed();
		save();
	}

	private void onNewSlot(String name)
	{
		List<Integer> ids = structure.idsNamed(name);
		int itemId = -1;
		for (int id : ids)
		{
			if (!progress.owns(id))
			{
				itemId = id;
				break;
			}
		}
		if (itemId < 0 && !ids.isEmpty())
		{
			itemId = ids.get(0);
		}

		ItemOdds odds = itemId > 0 ? data.odds(itemId) : null;
		String label = null;
		int count = 0;
		double chance = 0;
		double luckier = -1;
		if (odds != null)
		{
			label = odds.mainLabel();
			Integer value = progress.counter(label);
			count = value == null ? 0 : value;
			chance = odds.mainChance();
			luckier = odds.dryChance(progress.getCounters());
		}
		else if (itemId > 0)
		{
			for (Activity.Drop drop : data.dropsOf(itemId))
			{
				chance = Math.max(chance, 1 / Math.max(1, drop.getAttempts()));
			}
		}
		List<LogStructure.Page> pages = itemId > 0 ? structure.pagesOf(itemId) : java.util.Collections.emptyList();
		String page = pages.isEmpty() ? null : pages.get(0).getName();
		if (itemId > 0)
		{
			progress.addOwned(itemId);
		}
		progress.addFind(new Progress.Find(itemId, name, page, label, count, chance, luckier, System.currentTimeMillis()));
		changed();
		save();

		String odd = chance > 0 ? Format.chance(chance) + (count > 0 ? " at " + Format.count(count) + " " + Format.counterName(label) + " kc" : "") : page;
		String verdict = luckier >= 0 ? "Luckier than " + Math.round(luckier * 100) + "% of players" : null;
		Color color = luckier >= 0 ? Theme.luck(Luck.verdict(luckier)) : Theme.GOLD;
		if (config.showPlaque())
		{
			plaque.show(new FindPlaque.View(name, page, odd, verdict, color, itemId > 0 && data.isPet(itemId),
				itemId > 0 ? itemManager.getImage(itemId) : null, System.currentTimeMillis(), config.plaqueSeconds() * 1000L));
		}
		if (config.chatLine() && (chance > 0 || verdict != null))
		{
			ChatMessageBuilder line = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT).append("Log Insight: ")
				.append(ChatColorType.NORMAL).append(name);
			if (odd != null)
			{
				line.append(", " + odd);
			}
			if (verdict != null)
			{
				line.append(". ").append(ChatColorType.HIGHLIGHT).append(verdict).append(ChatColorType.NORMAL);
			}
			line.append(".");
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(line.build())
				.build());
		}
	}

	private void changed()
	{
		dirty = true;
		unsaved = true;
	}

	private boolean isAnotherPlayersLog()
	{
		return client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
	}

	private boolean ironRates()
	{
		switch (config.rates())
		{
			case IRON:
				return true;
			case MAIN:
				return false;
			default:
				return client.getVarbitValue(VarbitID.IRONMAN) != 0;
		}
	}

	/** Reads the log's tabs, pages and slots from the game cache. */
	private void buildStructure()
	{
		EnumComposition tabEnum = client.getEnum(TAB_ENUM);
		int[] tabStructs = tabEnum == null ? null : tabEnum.getIntVals();
		if (tabStructs == null || tabStructs.length == 0)
		{
			return;
		}
		replacements.clear();
		EnumComposition swap = client.getEnum(REPLACEMENT_ENUM);
		if (swap != null)
		{
			int[] keys = swap.getKeys();
			int[] values = swap.getIntVals();
			for (int i = 0; i < Math.min(keys.length, values.length); i++)
			{
				replacements.put(keys[i], values[i]);
			}
		}

		List<LogStructure.Tab> tabs = new ArrayList<>();
		Map<Integer, String> names = new HashMap<>();
		for (int t = 0; t < tabStructs.length; t++)
		{
			StructComposition tabStruct = client.getStructComposition(tabStructs[t]);
			EnumComposition pageEnum = tabStruct == null ? null : client.getEnum(tabStruct.getIntValue(TAB_PAGES_PARAM));
			if (pageEnum == null)
			{
				continue;
			}
			String tabName = t < TAB_NAMES.length ? TAB_NAMES[t] : "Tab " + (t + 1);
			List<LogStructure.Page> pages = new ArrayList<>();
			for (int pageStructId : pageEnum.getIntVals())
			{
				StructComposition pageStruct = client.getStructComposition(pageStructId);
				EnumComposition itemEnum = pageStruct == null ? null : client.getEnum(pageStruct.getIntValue(PAGE_ITEMS_PARAM));
				if (itemEnum == null)
				{
					continue;
				}
				List<Integer> items = new ArrayList<>();
				for (int raw : itemEnum.getIntVals())
				{
					int id = replacements.getOrDefault(raw, raw);
					items.add(id);
					names.computeIfAbsent(id, k -> Text.removeTags(itemManager.getItemComposition(k).getName()));
				}
				pages.add(new LogStructure.Page(tabName, pageStruct.getStringValue(PAGE_NAME_PARAM), items));
			}
			tabs.add(new LogStructure.Tab(tabName, pages));
		}
		structure = new LogStructure(tabs, names);
		log.debug("Collection log read: {} tabs, {} slots", tabs.size(), structure.getUniqueSlots());
	}

	private void loadProgress()
	{
		String key = configManager.getRSProfileKey();
		if (key == null)
		{
			return;
		}
		profile = key;
		Progress loaded = null;
		String json = configManager.getRSProfileConfiguration(CodexLogInsightConfig.GROUP, PROGRESS_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				loaded = gson.fromJson(json, Progress.class);
			}
			catch (JsonParseException e)
			{
				log.warn("Could not read saved collection log progress, starting fresh", e);
			}
		}
		progress = loaded == null ? new Progress() : loaded;
		seedFromChatCommands();
	}

	/**
	 * Brings counters up to the kill counts RuneLite's chat commands remember, which can be
	 * newer than the last time a log page was opened. Counts only ever go up.
	 */
	private void seedFromChatCommands()
	{
		for (String label : data.getLabels())
		{
			Integer known = progress.counter(label);
			for (String key : ChatReader.savedKeys(label))
			{
				Integer count = configManager.getRSProfileConfiguration("killcount", key, Integer.class);
				if (count != null && count > 0)
				{
					if (known == null || count > known)
					{
						progress.setCounter(label, count);
					}
					break;
				}
			}
		}
	}

	private void save()
	{
		ticksSinceSave = 0;
		if (progress == null || profile == null || !unsaved)
		{
			return;
		}
		unsaved = false;
		configManager.setConfiguration(CodexLogInsightConfig.GROUP, profile, PROGRESS_KEY, gson.toJson(progress));
	}

	private void analyse()
	{
		if (progress == null)
		{
			publish(Report.loggedOut(), null);
			return;
		}
		Progress snapshot = progress.copy();
		LogStructure shape = structure;
		boolean iron = ironRates();
		boolean pace = config.learnPace();
		Unlocks can = unlocks;
		int owned = officialOwned;
		int total = officialTotal;
		Player player = client.getLocalPlayer();
		String name = player == null ? null : player.getName();
		int ticket = generation.incrementAndGet();
		executor.execute(() ->
		{
			try
			{
				Report report = analyst.analyse(shape, snapshot, iron, pace, owned, total, can);
				if (ticket == generation.get())
				{
					publish(report, name);
				}
			}
			catch (RuntimeException e)
			{
				log.warn("Log Insight analysis failed", e);
			}
		});
	}

	private void publish(Report report, String name)
	{
		LogInsightPanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.update(report, name));
		}
	}
}
