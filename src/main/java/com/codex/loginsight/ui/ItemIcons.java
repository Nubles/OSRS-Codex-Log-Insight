package com.codex.loginsight.ui;

import java.awt.image.BufferedImage;

/** Where the panel gets item pictures; the plugin answers from RuneLite's item manager. */
public interface ItemIcons
{
	/**
	 * @param onLoaded run once if the picture arrives later than this call
	 * @return the picture, possibly still blank, or null
	 */
	BufferedImage icon(int itemId, Runnable onLoaded);

	/**
	 * True once this picture has been drawn in, so a card made from it shows the item rather than a blank. A source
	 * whose pictures are whole when handed out (as in tests) keeps this default.
	 */
	default boolean ready(BufferedImage image)
	{
		return image != null;
	}
}
