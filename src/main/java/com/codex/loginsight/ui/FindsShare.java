package com.codex.loginsight.ui;

import com.codex.loginsight.core.Progress;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finest finds as a card to share: whose log it is and how full, then the podium as the Ledger draws it, fourth and
 * fifth beneath. It is drawn only once every picture is in (see {@link IconWait}), so no plinth stands empty.
 */
public final class FindsShare
{
	static final int WIDTH = 260;
	static final int SCALE = 2;
	private static final int PAD = 16;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

	private FindsShare()
	{
	}

	/** Everything the card shows, taken once so a saved card and a copied one read the same. */
	public static final class Spec
	{
		final String player;
		final List<Progress.Find> finds;
		final int owned;
		final int total;
		final long now;
		final ZoneId zone;

		private Spec(String player, List<Progress.Find> finds, int owned, int total, long now, ZoneId zone)
		{
			this.player = player;
			this.finds = finds;
			this.owned = owned;
			this.total = total;
			this.now = now;
			this.zone = zone;
		}

		/**
		 * @param player the name to print, or null to leave it off
		 * @param owned  slots logged, shown with the total when the log has been read (total above 0)
		 * @return the card, or null with no finds yet
		 */
		public static Spec of(List<Progress.Find> finds, String player, int owned, int total, long now, ZoneId zone)
		{
			if (finds == null || finds.isEmpty())
			{
				return null;
			}
			String name = player == null || player.trim().isEmpty() ? null : player.trim();
			return new Spec(name, Collections.unmodifiableList(new ArrayList<>(finds)), owned, total, now, zone);
		}

		/** The large line: the player's name, or "Finest finds" when it is left off. */
		String heading()
		{
			return player != null ? player : "Finest finds";
		}

		/** The line under it: what the card is (when the name heads it) and how full the log is. */
		String subheading()
		{
			String slots = total > 0 ? Format.count(owned) + " of " + Format.count(total) + " slots" : null;
			if (player == null)
			{
				return slots;
			}
			return slots == null ? "Finest finds" : "Finest finds  ·  " + slots;
		}

		/** The item ids whose pictures the card needs. */
		public List<Integer> itemIds()
		{
			return IconWait.ids(finds);
		}
	}

	/** "codex-finds-2026-10-19.png". */
	public static String fileName(long now, ZoneId zone)
	{
		return "codex-finds-" + Instant.ofEpochMilli(now).atZone(zone).toLocalDate() + ".png";
	}

	/**
	 * Waits for the finds' pictures, then draws the card and hands it over on the Swing thread.
	 */
	static IconWait whenReady(Spec spec, ItemIcons icons, java.util.function.Consumer<BufferedImage> done)
	{
		return IconWait.forItems(icons, spec.itemIds(), images -> done.accept(render(spec, images)));
	}

	/** @param images each find's picture by item id; a find without one stands on its plinth as a dark disc */
	public static BufferedImage render(Spec spec, Map<Integer, BufferedImage> images)
	{
		TrophyShelf shelf = new TrophyShelf(spec.finds, (itemId, onLoaded) -> images.get(itemId));
		int shelfW = WIDTH - 2 * PAD;
		int shelfH = shelf.getPreferredSize().height;
		shelf.setSize(shelfW, shelfH);

		String sub = spec.subheading();
		int headTop = PAD + 16;
		int shelfTop = headTop + (sub != null ? 52 : 40);
		int footTop = shelfTop + shelfH + 12;
		int height = footTop + 26;

		BufferedImage image = new BufferedImage(WIDTH * SCALE, height * SCALE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.scale(SCALE, SCALE);
		Theme.smooth(g);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		RoundRectangle2D outer = new RoundRectangle2D.Float(0.5f, 0.5f, WIDTH - 1, height - 1, 14, 14);
		g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.INK, Theme.GOLD_DIM, 0.16), 0, height, Theme.INK_DEEP));
		g.fill(outer);
		g.setColor(Theme.GOLD_DIM);
		g.draw(outer);
		g.setColor(Theme.alpha(Theme.GOLD, 60));
		g.draw(new RoundRectangle2D.Float(4.5f, 4.5f, WIDTH - 9, height - 9, 10, 10));

		// The masthead: the plugin's mark on the left, today's date on the right.
		g.setFont(Theme.small());
		FontMetrics sm = g.getFontMetrics();
		int mastY = PAD + 4 + sm.getAscent() / 2 - 1;
		diamond(g, PAD + 3, PAD + 4, 3, Theme.GOLD);
		g.setColor(Theme.GOLD);
		g.drawString("CODEX LOG INSIGHT", PAD + 11, mastY);
		String today = Instant.ofEpochMilli(spec.now).atZone(spec.zone).format(DATE);
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(today, WIDTH - PAD - sm.stringWidth(today), mastY);

		String heading = spec.heading();
		int textW = WIDTH - 2 * PAD;
		if (g.getFontMetrics(Theme.title()).stringWidth(heading) * 2 <= textW)
		{
			large(g, heading, PAD, headTop);
		}
		else
		{
			g.setFont(Theme.title());
			g.setColor(Theme.GOLD_BRIGHT);
			g.drawString(clip(heading, g.getFontMetrics(), textW), PAD, headTop + g.getFontMetrics().getAscent() + 6);
		}
		if (sub != null)
		{
			g.setFont(Theme.small());
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(clip(sub, g.getFontMetrics(), textW), PAD, headTop + 44);
		}

		Graphics2D sg = (Graphics2D) g.create(PAD, shelfTop, shelfW, shelfH);
		shelf.paint(sg);
		sg.dispose();

		int fy = footTop;
		g.setPaint(new GradientPaint(PAD, 0, Theme.alpha(Theme.GOLD, 0), WIDTH / 2f, 0, Theme.alpha(Theme.GOLD, 140), true));
		g.drawLine(PAD, fy, WIDTH - PAD, fy);
		diamond(g, WIDTH / 2, fy, 3, Theme.GOLD);
		g.setFont(Theme.small());
		String foot = "Read by Codex Log Insight";
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(foot, (WIDTH - g.getFontMetrics().stringWidth(foot)) / 2, fy + 16);
		g.dispose();
		return image;
	}

	/** The game's bold font doubled pixel for pixel, the way the game draws large lettering. */
	private static void large(Graphics2D g, String text, int x, int y)
	{
		Font font = Theme.title();
		FontMetrics fm = g.getFontMetrics(font);
		int w = Math.max(1, fm.stringWidth(text) + 1);
		int h = fm.getHeight() + 1;
		BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sg = small.createGraphics();
		sg.setFont(font);
		sg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		int base = fm.getAscent();
		sg.setColor(Color.BLACK);
		sg.drawString(text, 1, base + 1);
		sg.setPaint(new GradientPaint(0, 3, Theme.GOLD_BRIGHT, 0, base, Theme.GOLD));
		sg.drawString(text, 0, base);
		sg.dispose();
		Graphics2D lg = (Graphics2D) g.create();
		lg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		lg.drawImage(small, x, y, w * 2, h * 2, null);
		lg.dispose();
	}

	private static void diamond(Graphics2D g, int cx, int cy, int r, Color c)
	{
		Path2D d = new Path2D.Float();
		d.moveTo(cx, cy - r);
		d.lineTo(cx + r, cy);
		d.lineTo(cx, cy + r);
		d.lineTo(cx - r, cy);
		d.closePath();
		g.setColor(c);
		g.fill(d);
	}

	private static String clip(String s, FontMetrics fm, int width)
	{
		if (fm.stringWidth(s) <= width)
		{
			return s;
		}
		String out = s;
		while (out.length() > 1 && fm.stringWidth(out.trim() + "…") > width)
		{
			out = out.substring(0, out.length() - 1);
		}
		return out.trim() + "…";
	}
}
