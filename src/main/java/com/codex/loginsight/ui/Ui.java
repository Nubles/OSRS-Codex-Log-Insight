package com.codex.loginsight.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import javax.swing.JLabel;

/** Small helpers shared by the panel's components. */
public final class Ui
{
	private Ui()
	{
	}

	public static JLabel label(String text, Font font, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(font);
		label.setForeground(color);
		return label;
	}

	/**
	 * A label that wraps inside the side panel's narrow width. Swing's HTML renderer
	 * treats CSS pixels as points, so the width is converted to keep it in screen pixels.
	 */
	public static JLabel wrapped(String text, Font font, Color color, int width)
	{
		int css = Math.round(width * 0.75f);
		return label("<html><div style='width:" + css + "px'>" + escape(text) + "</div></html>", font, color);
	}

	/**
	 * A label wrapped to the width and cut to at most this many lines, the last ending in an
	 * ellipsis when the text runs on. Long names stay tidy instead of growing a card.
	 */
	public static JLabel clamped(String text, Font font, Color color, int width, int lines)
	{
		JLabel label = label("", font, color);
		java.awt.FontMetrics fm = label.getFontMetrics(font);
		java.util.List<String> out = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		String[] words = text.split(" ");
		int i = 0;
		while (i < words.length && out.size() < lines)
		{
			String next = line.length() == 0 ? words[i] : line + " " + words[i];
			if (fm.stringWidth(next) <= width || line.length() == 0)
			{
				line = new StringBuilder(next);
				i++;
			}
			else
			{
				out.add(line.toString());
				line = new StringBuilder();
			}
		}
		if (line.length() > 0 && out.size() < lines)
		{
			out.add(line.toString());
		}
		if (i < words.length || fm.stringWidth(out.get(out.size() - 1)) > width)
		{
			String last = out.get(out.size() - 1);
			while (last.length() > 1 && fm.stringWidth(last + "…") > width)
			{
				last = last.substring(0, last.length() - 1).trim();
			}
			out.set(out.size() - 1, last + "…");
		}
		StringBuilder html = new StringBuilder("<html>");
		for (int k = 0; k < out.size(); k++)
		{
			html.append(k == 0 ? "" : "<br>").append(escape(out.get(k)));
		}
		label.setText(html.append("</html>").toString());
		label.setToolTipText(text);
		return label;
	}

	public static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Lets a clickable card keep its hover and click while the pointer is over its children. */
	static void forwardMouse(Container root, MouseAdapter adapter)
	{
		for (Component child : root.getComponents())
		{
			child.addMouseListener(adapter);
			if (child instanceof Container)
			{
				forwardMouse((Container) child, adapter);
			}
		}
	}
}
