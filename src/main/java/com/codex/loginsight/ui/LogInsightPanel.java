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
	private static final String[] TABS = {"ledger", "folios", "quarry"};

	private final ItemIcons icons;
	private final Crest crest = new Crest();
	private final Segmented tabs = new Segmented(true, "Ledger", "Folios", "Quarry");
	private final Segmented quarryFilter = new Segmented(false, QuarryView.FILTERS);
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JScrollPane ledger = scroller();
	private final JScrollPane folios = scroller();
	private final JScrollPane quarry = scroller();
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

		content.setOpaque(false);
		content.add(ledger, TABS[0]);
		content.add(folioHolder, TABS[1]);
		content.add(quarryHolder, TABS[2]);
		add(content, BorderLayout.CENTER);

		tabs.onChange(this::showTab);
		quarryFilter.onChange(i -> rebuildQuarry());
		rebuildTab(0);
		rebuildTab(1);
		rebuildTab(2);
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

	/** Only the tab in view is rebuilt for each report; the others wait until they are opened. */
	private void rebuild()
	{
		crest.update(report, playerName);
		java.util.Arrays.fill(stale, true);
		rebuildTab(shown);
	}

	private void rebuildTab(int tab)
	{
		stale[tab] = false;
		if (tab == 0)
		{
			replace(ledger, LedgerView.build(report, dial, icons, this::showPage, () -> showTab(2),
				System.currentTimeMillis()));
		}
		else if (tab == 1)
		{
			rebuildFolios();
		}
		else
		{
			rebuildQuarry();
		}
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
			}));
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
