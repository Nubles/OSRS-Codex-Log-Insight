package com.codex.loginsight.ui;

import com.codex.loginsight.core.Luck;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import net.runelite.client.ui.FontManager;

/**
 * The Codex look: warm ink, aged gold and a single arcane teal, drawn with the
 * game's own fonts so the panel reads as part of Gielinor rather than a web page.
 * Log Insight adds a violet for the rarest finds, the colour of a pet's glow.
 */
public final class Theme
{
	public static final Color INK = new Color(23, 20, 16);
	public static final Color INK_DEEP = new Color(16, 14, 11);
	public static final Color CARD = new Color(34, 30, 24);
	public static final Color CARD_TOP = new Color(42, 37, 29);
	public static final Color CARD_HOVER = new Color(48, 42, 33);
	public static final Color STROKE = new Color(66, 56, 42);
	public static final Color STROKE_SOFT = new Color(48, 41, 32);

	public static final Color GOLD = new Color(214, 178, 106);
	public static final Color GOLD_BRIGHT = new Color(246, 214, 144);
	public static final Color GOLD_DIM = new Color(132, 108, 66);
	public static final Color TEAL = new Color(96, 184, 170);
	public static final Color GREEN = new Color(130, 196, 108);
	public static final Color EMBER = new Color(222, 134, 86);
	public static final Color BLOCKED = new Color(176, 92, 84);
	public static final Color BLOOD = new Color(204, 70, 60);
	public static final Color BLOOD_DEEP = new Color(120, 32, 28);
	public static final Color VIOLET = new Color(176, 132, 222);

	public static final Color TEXT = new Color(234, 224, 202);
	public static final Color TEXT_MUTED = new Color(160, 148, 126);
	public static final Color TEXT_FAINT = new Color(110, 100, 84);

	public static final int RADIUS = 10;
	public static final int GUTTER = 8;

	private Theme()
	{
	}

	public static Font title()
	{
		return FontManager.getRunescapeBoldFont();
	}

	public static Font body()
	{
		return FontManager.getRunescapeFont();
	}

	public static Font small()
	{
		return FontManager.getRunescapeSmallFont();
	}

	public static Color alpha(Color c, int alpha)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}

	public static Color mix(Color a, Color b, double t)
	{
		t = Math.max(0, Math.min(1, t));
		return new Color(
			(int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
			(int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
			(int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
			(int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
	}

	/** Progress colour: ember when just begun, gold through the middle, green when nearly there. */
	public static Color progressColor(double progress)
	{
		if (progress >= 1)
		{
			return GREEN;
		}
		if (progress < 0.5)
		{
			return mix(EMBER, GOLD, progress * 2);
		}
		return mix(GOLD, TEAL, (progress - 0.5) * 2);
	}

	/** Blood when parched, ember when dry, gold when even, teal when fortunate and green when blessed. */
	public static Color luck(Luck.Verdict verdict)
	{
		switch (verdict)
		{
			case PARCHED:
				return BLOOD;
			case DRY:
				return EMBER;
			case FORTUNATE:
				return TEAL;
			case BLESSED:
				return GREEN;
			default:
				return GOLD;
		}
	}

	/** The same scale as a smooth ramp, for gauges and bars. */
	public static Color luck(double percentile)
	{
		double p = Math.max(0, Math.min(1, percentile));
		if (p < 0.25)
		{
			return mix(BLOOD, EMBER, p / 0.25);
		}
		if (p < 0.5)
		{
			return mix(EMBER, GOLD, (p - 0.25) / 0.25);
		}
		if (p < 0.75)
		{
			return mix(GOLD, TEAL, (p - 0.5) / 0.25);
		}
		return mix(TEAL, GREEN, (p - 0.75) / 0.25);
	}

	/** How overdue a missing slot is: gold while it is normal, through ember to blood as it gets rare to be this dry. */
	public static Color dry(double dryChance)
	{
		if (dryChance < 0)
		{
			return TEXT_FAINT;
		}
		if (dryChance >= 0.5)
		{
			return TEXT_MUTED;
		}
		if (dryChance >= 0.2)
		{
			return GOLD;
		}
		return dryChance >= 0.07 ? EMBER : BLOOD;
	}

	/** Smooth shapes, but the pixel fonts stay crisp. */
	public static void smooth(Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
	}
}
