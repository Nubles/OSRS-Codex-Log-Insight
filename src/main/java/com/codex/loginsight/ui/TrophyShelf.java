package com.codex.loginsight.ui;

import com.codex.loginsight.core.Progress;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * The trophy shelf: the rarest drops Log Insight has seen you get, on a podium, the rarest in the middle. Each
 * stands on a gilt plinth with its odds and how far ahead of other players it came. Drawn once per report; the
 * pictures arrive from the item manager and only repaint this card.
 */
final class TrophyShelf extends JComponent
{
	static final int HEIGHT = 158;
	/** Plinth heights for first, second and third. */
	private static final int[] PLINTH = {30, 21, 14};
	private static final Color[] MEDAL = {Theme.GOLD_BRIGHT, new Color(196, 198, 202), new Color(196, 132, 84)};

	/** Each find below the podium, fourth and fifth, gets a slim row. */
	static final int ROW = 20;

	private final List<Progress.Find> finds;
	private final BufferedImage[] images;
	/** The finds after the podium's three. */
	private final List<Progress.Find> rest;
	private final BufferedImage[] restImages;

	TrophyShelf(List<Progress.Find> trophies, ItemIcons icons)
	{
		finds = new ArrayList<>(trophies.subList(0, Math.min(3, trophies.size())));
		rest = new ArrayList<>(trophies.subList(finds.size(), trophies.size()));
		images = images(finds, icons);
		restImages = images(rest, icons);
		setOpaque(false);
		int h = height(rest.size());
		setPreferredSize(new Dimension(10, h));
		setMinimumSize(new Dimension(10, h));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		StringBuilder tip = new StringBuilder("<html>The rarest drops Log Insight has seen you get:");
		List<Progress.Find> all = new ArrayList<>(finds);
		all.addAll(rest);
		for (int i = 0; i < all.size(); i++)
		{
			Progress.Find f = all.get(i);
			tip.append("<br>").append(i + 1).append(". ").append(Ui.escape(f.getName())).append(", ").append(Format.chance(f.getChance()));
			if (f.getCount() > 0)
			{
				tip.append(" at ").append(Format.count(f.getCount())).append(" kc");
			}
			if (f.getPage() != null)
			{
				tip.append(" (").append(Ui.escape(f.getPage())).append(")");
			}
		}
		setToolTipText(tip.append("</html>").toString());
	}

	private BufferedImage[] images(List<Progress.Find> list, ItemIcons icons)
	{
		BufferedImage[] out = new BufferedImage[list.size()];
		for (int i = 0; i < list.size(); i++)
		{
			int id = list.get(i).getItemId();
			out[i] = icons == null || id <= 0 ? null : icons.icon(id, this::repaint);
		}
		return out;
	}

	/** The card's height: the podium, and a row for each find after it. */
	static int height(int rest)
	{
		return rest == 0 ? HEIGHT : HEIGHT + 8 + rest * ROW;
	}

	/** Which find stands in each column, left to right: the rarest in the middle once there are three. */
	static int[] order(int count)
	{
		switch (count)
		{
			case 1:
				return new int[]{0};
			case 2:
				return new int[]{0, 1};
			default:
				return new int[]{1, 0, 2};
		}
	}

	/**
	 * "top 3%" for a find fewer players would have had by then, "a late one" for one most would have had sooner,
	 * so every plinth carries a line; null when the odds are unknown.
	 */
	static String standing(double luckier)
	{
		if (luckier < 0)
		{
			return null;
		}
		if (luckier < 0.5)
		{
			return "a late one";
		}
		long top = Math.max(1, Math.round((1 - luckier) * 100));
		return "top " + top + "%";
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();

		RoundRectangle2D card = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
		g.setPaint(new GradientPaint(0, 0, Theme.CARD_TOP, 0, h, Theme.CARD));
		g.fill(card);
		g.setColor(Theme.alpha(Theme.GOLD, 120));
		g.draw(card);
		g.setColor(Theme.alpha(Color.WHITE, 12));
		g.drawLine(Theme.RADIUS / 2, 1, w - Theme.RADIUS / 2, 1);

		Font small = Theme.small();
		Font body = Theme.body();
		FontMetrics sm = g.getFontMetrics(small);
		FontMetrics bm = g.getFontMetrics(body);

		int[] order = order(finds.size());
		int cols = order.length;
		int colW = Math.min(70, (w - 12) / 3);
		int left = (w - colW * cols) / 2;
		int shelfY = 82;

		// The shelf: a gilt ledge the plinths stand on.
		g.setPaint(new GradientPaint(10, 0, Theme.alpha(Theme.GOLD, 0), w / 2f, 0, Theme.alpha(Theme.GOLD, 150), true));
		g.fillRect(10, shelfY, w - 20, 2);
		g.setColor(Theme.alpha(Color.BLACK, 90));
		g.fillRect(10, shelfY + 2, w - 20, 2);

		for (int c = 0; c < cols; c++)
		{
			int i = order[c];
			Progress.Find find = finds.get(i);
			int x = left + c * colW;
			int cx = x + colW / 2;
			int ph = PLINTH[i];
			int pw = colW - 14;
			int top = shelfY - ph;

			// The plinth, lit from above, its rank on the front.
			g.setPaint(new GradientPaint(0, top, Theme.mix(Theme.CARD_HOVER, Theme.GOLD_DIM, 0.35), 0, shelfY, Theme.INK_DEEP));
			g.fillRect(cx - pw / 2, top, pw, ph);
			g.setColor(Theme.alpha(MEDAL[i], 150));
			g.drawLine(cx - pw / 2, top, cx + pw / 2 - 1, top);
			g.setColor(Theme.alpha(Theme.STROKE, 220));
			g.drawRect(cx - pw / 2, top, pw - 1, ph - 1);
			String rank = String.valueOf(i + 1);
			if (ph >= sm.getAscent() + 3)
			{
				text(g, small, rank, cx - sm.stringWidth(rank) / 2, top + (ph + sm.getAscent()) / 2 - 1, MEDAL[i]);
			}

			// The prize, with a glow behind it: gold for the rarest.
			int iy = top - 34;
			float r = i == 0 ? 26 : 20;
			g.setPaint(new RadialGradientPaint(cx, iy + 16, r, new float[]{0f, 1f},
				new Color[]{Theme.alpha(MEDAL[i], i == 0 ? 90 : 55), Theme.alpha(MEDAL[i], 0)}));
			g.fill(new Ellipse2D.Float(cx - r, iy + 16 - r, r * 2, r * 2));
			BufferedImage img = images[i];
			if (img != null)
			{
				int iw = Math.min(img.getWidth(), 36);
				int ih = Math.min(img.getHeight(), 32);
				g.drawImage(img, cx - iw / 2, iy, iw, ih, null);
			}
			else
			{
				g.setColor(Theme.alpha(Theme.INK_DEEP, 200));
				g.fill(new Ellipse2D.Float(cx - 12, iy + 4, 24, 24));
			}
			if (i == 0)
			{
				star(g, cx + 15, iy + 2);
			}

			// The plaque under the shelf: odds, name, and how early it came.
			int ty = shelfY + 8 + bm.getAscent();
			String odds = Format.chance(find.getChance());
			text(g, body, odds, cx - bm.stringWidth(odds) / 2, ty, i == 0 ? Theme.GOLD_BRIGHT : Theme.GOLD);
			// The name on up to two lines, so "Skeletal visage" is read whole rather than cut.
			for (String part : lines(find.getName(), sm, colW - 4))
			{
				ty += sm.getAscent() + 2;
				text(g, small, part, cx - sm.stringWidth(part) / 2, ty, Theme.TEXT);
			}
			if (find.getCount() > 0)
			{
				String kc = clip(Format.count(find.getCount()) + " kc", sm, colW - 2);
				ty += sm.getAscent() + 2;
				text(g, small, kc, cx - sm.stringWidth(kc) / 2, ty, Theme.TEXT_FAINT);
			}
			String standing = standing(find.getLuckier());
			if (standing != null)
			{
				standing = clip(standing, sm, colW - 2);
				ty += sm.getAscent() + 2;
				text(g, small, standing, cx - sm.stringWidth(standing) / 2, ty,
					find.getLuckier() < 0.5 ? Theme.TEXT_FAINT : Theme.luck(find.getLuckier()));
			}
		}
		paintRest(g, w, sm, bm);
		g.dispose();
	}

	/** Fourth and fifth on slim rows under the podium: rank, picture, name and odds. */
	private void paintRest(Graphics2D g, int w, FontMetrics sm, FontMetrics bm)
	{
		if (rest.isEmpty())
		{
			return;
		}
		int y = HEIGHT + 1;
		g.setPaint(new GradientPaint(14, 0, Theme.alpha(Theme.GOLD, 0), w / 2f, 0, Theme.alpha(Theme.GOLD, 90), true));
		g.drawLine(14, y, w - 14, y);
		for (int i = 0; i < rest.size(); i++)
		{
			Progress.Find find = rest.get(i);
			int top = y + 3 + i * ROW;
			int mid = top + ROW / 2;
			String rank = String.valueOf(i + 4);
			text(g, Theme.small(), rank, 14 - sm.stringWidth(rank) / 2 + 2, mid + sm.getAscent() / 2 - 1, Theme.TEXT_FAINT);
			BufferedImage img = restImages[i];
			if (img != null)
			{
				int iw = Math.min(img.getWidth(), 36);
				int ih = Math.min(img.getHeight(), 32);
				double scale = Math.min(22.0 / iw, 18.0 / ih);
				int dw = (int) Math.round(iw * scale);
				int dh = (int) Math.round(ih * scale);
				g.drawImage(img, 34 - dw / 2, mid - dh / 2, dw, dh, null);
			}
			String odds = Format.chance(find.getChance());
			int oddsX = w - 12 - sm.stringWidth(odds);
			text(g, Theme.small(), odds, oddsX, mid + sm.getAscent() / 2 - 1, Theme.GOLD);
			String name = clip(find.getName(), bm, oddsX - 50 - 6);
			text(g, Theme.body(), name, 50, mid + bm.getAscent() / 2 - 1, Theme.TEXT);
		}
	}

	/** A four-pointed glint for the rarest. */
	private static void star(Graphics2D g, int x, int y)
	{
		Path2D s = new Path2D.Float();
		s.moveTo(x, y - 5);
		s.lineTo(x + 1.3, y - 1.3);
		s.lineTo(x + 5, y);
		s.lineTo(x + 1.3, y + 1.3);
		s.lineTo(x, y + 5);
		s.lineTo(x - 1.3, y + 1.3);
		s.lineTo(x - 5, y);
		s.lineTo(x - 1.3, y - 1.3);
		s.closePath();
		g.setColor(Theme.GOLD_BRIGHT);
		g.fill(s);
	}

	private static void text(Graphics2D g, Font font, String s, int x, int y, Color c)
	{
		g.setFont(font);
		g.setColor(Theme.alpha(Color.BLACK, 170));
		g.drawString(s, x + 1, y + 1);
		g.setColor(c);
		g.drawString(s, x, y);
	}

	/** Up to two lines of a name, broken between words, the second cut short if it still does not fit. */
	static List<String> lines(String name, FontMetrics fm, int width)
	{
		List<String> out = new ArrayList<>();
		if (name == null || name.isEmpty())
		{
			return out;
		}
		if (fm.stringWidth(name) <= width)
		{
			out.add(name);
			return out;
		}
		String[] words = name.split(" ");
		StringBuilder first = new StringBuilder(words[0]);
		int i = 1;
		while (i < words.length && fm.stringWidth(first + " " + words[i]) <= width)
		{
			first.append(' ').append(words[i++]);
		}
		out.add(clip(first.toString(), fm, width));
		if (i < words.length)
		{
			StringBuilder rest = new StringBuilder(words[i++]);
			while (i < words.length)
			{
				rest.append(' ').append(words[i++]);
			}
			out.add(clip(rest.toString(), fm, width));
		}
		return out;
	}

	private static String clip(String s, FontMetrics fm, int width)
	{
		if (s == null)
		{
			return "";
		}
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
