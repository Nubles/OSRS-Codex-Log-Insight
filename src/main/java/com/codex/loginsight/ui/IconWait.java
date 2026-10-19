package com.codex.loginsight.ui;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Gathers the item pictures a card needs and hands them over once every one has been drawn in, so a saved card never
 * shows an empty plinth. Each picture is asked for once per wait; the instance handed out is the one waited on, since
 * the item manager may hand out a fresh blank for the same item later. A picture that never comes is given up on
 * after a few seconds and the card is made without it. Swing thread only.
 */
final class IconWait
{
	static final int GIVE_UP_MS = 4000;

	private final ItemIcons icons;
	private final Map<Integer, BufferedImage> images = new LinkedHashMap<>();
	private final Consumer<Map<Integer, BufferedImage>> done;
	private Timer giveUp;
	private boolean finished;

	private IconWait(ItemIcons icons, Consumer<Map<Integer, BufferedImage>> done)
	{
		this.icons = icons;
		this.done = done;
	}

	/**
	 * @param done called on the Swing thread exactly once, with each item's picture (or none for one that never came)
	 */
	static IconWait forItems(ItemIcons icons, List<Integer> itemIds, Consumer<Map<Integer, BufferedImage>> done)
	{
		return forItems(icons, itemIds, GIVE_UP_MS, done);
	}

	/** @return the wait, so it can be called off when the plugin stops */
	static IconWait forItems(ItemIcons icons, List<Integer> itemIds, int giveUpMs, Consumer<Map<Integer, BufferedImage>> done)
	{
		IconWait wait = new IconWait(icons, done);
		for (int id : itemIds)
		{
			if (id > 0 && !wait.images.containsKey(id) && icons != null)
			{
				BufferedImage image = icons.icon(id, () -> SwingUtilities.invokeLater(wait::check));
				// No picture at all will never arrive: the card draws that find without one rather than waiting.
				if (image != null)
				{
					wait.images.put(id, image);
				}
			}
		}
		if (!wait.check())
		{
			wait.giveUp = new Timer(giveUpMs, e -> wait.finish());
			wait.giveUp.setRepeats(false);
			wait.giveUp.start();
		}
		return wait;
	}

	/** Calls the wait off: the card is never made, and the give-up timer is stopped. */
	void cancel()
	{
		finished = true;
		if (giveUp != null)
		{
			giveUp.stop();
		}
	}

	/** Finishes once every picture is in; true when it has. */
	private boolean check()
	{
		if (finished)
		{
			return true;
		}
		for (BufferedImage image : images.values())
		{
			if (!icons.ready(image))
			{
				return false;
			}
		}
		finish();
		return true;
	}

	private void finish()
	{
		if (finished)
		{
			return;
		}
		finished = true;
		if (giveUp != null)
		{
			giveUp.stop();
		}
		Map<Integer, BufferedImage> out = new LinkedHashMap<>();
		for (Map.Entry<Integer, BufferedImage> e : images.entrySet())
		{
			if (icons.ready(e.getValue()))
			{
				out.put(e.getKey(), e.getValue());
			}
		}
		done.accept(out);
	}

	/** The item ids a list of finds needs pictures for, in order. */
	static List<Integer> ids(List<com.codex.loginsight.core.Progress.Find> finds)
	{
		List<Integer> out = new ArrayList<>();
		for (com.codex.loginsight.core.Progress.Find f : finds)
		{
			out.add(f.getItemId());
		}
		return out;
	}
}
