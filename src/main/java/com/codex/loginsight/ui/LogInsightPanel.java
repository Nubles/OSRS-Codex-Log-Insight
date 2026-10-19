package com.codex.loginsight.ui;

import com.codex.loginsight.core.Report;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Graphics;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

/**
 * The Log Insight side panel: the crest, then three pages. Ledger is the log at a
 * glance, Folios is the log page by page, and Quarry ranks where the next slots are.
 */
public class LogInsightPanel extends PluginPanel
{
	private static final String[] TABS = {"ledger", "folios", "quarry", "plan"};

	private final ItemIcons icons;
	private final Crest crest = new Crest();
	private final Segmented tabs = new Segmented(true, "Ledger", "Folios", "Quarry", "Plan");
	private final Segmented quarryFilter = new Segmented(false, QuarryView.FILTERS);
	private final Segmented horizon = new Segmented(false, PlanView.HORIZONS);
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JScrollPane ledger = scroller();
	private final JScrollPane folios = scroller();
	private final JScrollPane quarry = scroller();
	private final JScrollPane plan = scroller();
	private final JPanel folioHolder = new JPanel(new BorderLayout());
	private final JPanel searchHolder = new JPanel(new BorderLayout());
	private final LuckDial dial = new LuckDial(112);

	/** Tabs whose view was built from an older report and is rebuilt when next shown. */
	private final boolean[] stale = new boolean[TABS.length];
	private int shown;
	private Report report = Report.loggedOut();
	private String playerName;
	private String filter = "";
	private String openPage;
	/** Marks a slot wanted or not; set by the plugin. */
	private java.util.function.IntConsumer onWant;
	private ShareActions share;
	private java.util.function.BooleanSupplier nameOnCards = () -> true;
	private String shareNote;
	/** True while a card waits for its pictures, so a second click does not start a second wait. */
	private boolean sharing;
	/** True while the panel is open in the sidebar; a closed panel only marks its tabs stale. */
	private boolean active;

	public LogInsightPanel(ItemIcons icons)
	{
		super(false);
		this.icons = icons;
		setLayout(new BorderLayout());
		setBackground(Theme.INK);

		JPanel north = new JPanel(new BorderLayout());
		north.setOpaque(false);
		north.add(crest, BorderLayout.NORTH);
		JPanel tabHolder = new JPanel(new BorderLayout());
		tabHolder.setOpaque(false);
		tabHolder.setBorder(new EmptyBorder(8, 8, 2, 8));
		tabHolder.add(tabs, BorderLayout.CENTER);
		north.add(tabHolder, BorderLayout.SOUTH);
		add(north, BorderLayout.NORTH);

		searchHolder.setOpaque(false);
		searchHolder.setBorder(new EmptyBorder(6, 8, 0, 8));
		searchHolder.add(new SearchField(text ->
		{
			filter = text;
			rebuildFolios();
		}), BorderLayout.CENTER);
		folioHolder.setOpaque(false);
		folioHolder.add(searchHolder, BorderLayout.NORTH);
		folioHolder.add(folios, BorderLayout.CENTER);

		JPanel quarryHolder = new JPanel(new BorderLayout());
		quarryHolder.setOpaque(false);
		JPanel filterHolder = new JPanel(new BorderLayout());
		filterHolder.setOpaque(false);
		filterHolder.setBorder(new EmptyBorder(6, 8, 0, 8));
		filterHolder.add(quarryFilter, BorderLayout.CENTER);
		quarryHolder.add(filterHolder, BorderLayout.NORTH);
		quarryHolder.add(quarry, BorderLayout.CENTER);

		JPanel planHolder = new JPanel(new BorderLayout());
		planHolder.setOpaque(false);
		JPanel horizonHolder = new JPanel(new BorderLayout());
		horizonHolder.setOpaque(false);
		horizonHolder.setBorder(new EmptyBorder(6, 8, 0, 8));
		horizonHolder.add(horizon, BorderLayout.CENTER);
		planHolder.add(horizonHolder, BorderLayout.NORTH);
		planHolder.add(plan, BorderLayout.CENTER);
		horizon.setSelected(1, false);

		content.setOpaque(false);
		content.add(ledger, TABS[0]);
		content.add(folioHolder, TABS[1]);
		content.add(quarryHolder, TABS[2]);
		content.add(planHolder, TABS[3]);
		add(content, BorderLayout.CENTER);

		tabs.onChange(this::showTab);
		quarryFilter.onChange(i -> rebuildQuarry());
		horizon.onChange(i -> rebuildPlan());
		for (int tab = 0; tab < TABS.length; tab++)
		{
			rebuildTab(tab);
		}
	}

	private static JScrollPane scroller()
	{
		JScrollPane scroll = new JScrollPane();
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		Scrollbars.style(scroll.getVerticalScrollBar());
		return scroll;
	}

	/** Lets missing slots on a page be clicked onto the wanted list; {@code onWant} runs on the Swing thread. */
	public void setOnWant(java.util.function.IntConsumer onWant)
	{
		this.onWant = onWant;
	}

	/** Lets the Ledger save and copy the finest finds card; names go on it while the option is on. */
	public void setShare(ShareActions share, java.util.function.BooleanSupplier nameOnCards)
	{
		this.share = share;
		this.nameOnCards = nameOnCards == null ? () -> true : nameOnCards;
		ledgerChanged();
	}

	/** Shows what became of a save or copy under the podium. Swing thread only. */
	public void shareNote(String note)
	{
		shareNote = note;
		ledgerChanged();
	}

	private void ledgerChanged()
	{
		stale[0] = true;
		if ((active || isShowing()) && shown == 0)
		{
			rebuildTab(0);
		}
	}

	/** Save and copy under the podium, with what became of the last one; null without sharing or finds. */
	private JComponent shareRow()
	{
		if (share == null || report.getTrophies().isEmpty())
		{
			return null;
		}
		JPanel row = new JPanel(new BorderLayout(0, 4));
		row.setOpaque(false);
		JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
		buttons.setOpaque(false);
		buttons.add(new CodexButton("Save finds card", false, () -> shareFinds(true)));
		buttons.add(javax.swing.Box.createHorizontalStrut(6));
		buttons.add(new CodexButton("Copy", false, () -> shareFinds(false)));
		row.add(buttons, BorderLayout.NORTH);
		if (shareNote != null)
		{
			row.add(Ui.wrapped(shareNote, Theme.small(), Theme.TEAL, 200), BorderLayout.CENTER);
		}
		return row;
	}

	private void shareFinds(boolean save)
	{
		if (sharing || share == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		java.time.ZoneId zone = java.time.ZoneId.systemDefault();
		String name = nameOnCards.getAsBoolean() && report.isLoggedIn() ? playerName : null;
		FindsShare.Spec spec = FindsShare.Spec.of(report.getTrophies(), name, report.owned(), report.total(), now, zone);
		if (spec == null)
		{
			return;
		}
		// The card waits for every picture, so it never shows an empty plinth.
		sharing = true;
		FindsShare.whenReady(spec, icons, card ->
		{
			sharing = false;
			ShareActions to = share;
			if (to == null)
			{
				return;
			}
			if (save)
			{
				to.save(card, FindsShare.fileName(now, zone));
			}
			else
			{
				to.copy(card);
			}
		});
	}

	/** Called on the Swing thread with each new report. */
	public void update(Report report, String playerName)
	{
		this.report = report;
		this.playerName = playerName;
		rebuild();
	}

	/** Opens the Folios tab at one page, for example the page a new slot landed on. */
	public void showPage(String page)
	{
		openPage = page;
		tabs.setSelected(1, false);
		shown = 1;
		cards.show(content, TABS[1]);
		rebuildFolios();
		SwingUtilities.invokeLater(() -> folios.getVerticalScrollBar().setValue(0));
	}

	/** Switches to a tab, rebuilding it first if a newer report arrived while it was hidden. */
	public void showTab(int tab)
	{
		tabs.setSelected(tab, false);
		shown = tab;
		if (stale[tab])
		{
			rebuildTab(tab);
		}
		cards.show(content, TABS[tab]);
	}

	/**
	 * Only the tab in view is rebuilt for each report, and only while the panel is open; the others, or all of them
	 * while it is closed, wait until they are seen. Reports come with every kill count, so a closed panel would
	 * otherwise rebuild a page nobody looks at after each kill.
	 */
	private void rebuild()
	{
		crest.update(report, playerName);
		java.util.Arrays.fill(stale, true);
		if (active || isShowing())
		{
			rebuildTab(shown);
		}
	}

	@Override
	public void onActivate()
	{
		active = true;
		if (stale[shown])
		{
			rebuildTab(shown);
		}
	}

	@Override
	public void onDeactivate()
	{
		active = false;
	}

	/** True when a tab was built from an older report and waits to be rebuilt. */
	boolean isStale(int tab)
	{
		return stale[tab];
	}

	private void rebuildTab(int tab)
	{
		stale[tab] = false;
		if (tab == 0)
		{
			replace(ledger, LedgerView.build(report, dial, icons, this::showPage, () -> showTab(2),
				System.currentTimeMillis(), shareRow()));
		}
		else if (tab == 1)
		{
			rebuildFolios();
		}
		else if (tab == 2)
		{
			rebuildQuarry();
		}
		else
		{
			rebuildPlan();
		}
	}

	private void rebuildPlan()
	{
		replace(plan, PlanView.build(report, horizon.getSelected(), icons, this::showPage));
	}

	private void rebuildFolios()
	{
		Report.PageReport page = null;
		if (openPage != null)
		{
			for (Report.PageReport p : report.getPages())
			{
				if (p.getName().equals(openPage))
				{
					page = p;
					break;
				}
			}
		}
		searchHolder.setVisible(page == null);
		if (page != null)
		{
			replace(folios, PageView.build(page, icons, () ->
			{
				openPage = null;
				rebuildFolios();
			}, report::isWanted, onWant, report.getWanted().size() >= com.codex.loginsight.core.Progress.MAX_WANTED));
		}
		else
		{
			openPage = null;
			replace(folios, FoliosView.build(report, filter, name ->
			{
				openPage = name;
				rebuildFolios();
				SwingUtilities.invokeLater(() -> folios.getVerticalScrollBar().setValue(0));
			}));
		}
		folioHolder.revalidate();
	}

	private void rebuildQuarry()
	{
		replace(quarry, QuarryView.build(report, quarryFilter.getSelected(), icons));
	}

	private static void replace(JScrollPane scroll, JComponent view)
	{
		int position = scroll.getVerticalScrollBar().getValue();
		scroll.setViewportView(view);
		scroll.revalidate();
		SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(position));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		g.setColor(Theme.INK);
		g.fillRect(0, 0, getWidth(), getHeight());
	}
}
