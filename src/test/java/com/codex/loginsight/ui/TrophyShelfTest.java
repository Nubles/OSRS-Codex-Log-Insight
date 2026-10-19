package com.codex.loginsight.ui;

import static org.junit.Assert.assertEquals;
import com.codex.loginsight.core.Progress;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class TrophyShelfTest
{
	@Test
	public void fourthAndFifthGetARowEachUnderThePodium()
	{
		List<Progress.Find> finds = new ArrayList<>();
		for (int i = 0; i < 5; i++)
		{
			finds.add(new Progress.Find(100 + i, "Item " + i, "Page", null, 0, 1 / (1000.0 - i), -1, i));
		}
		assertEquals(TrophyShelf.HEIGHT, new TrophyShelf(finds.subList(0, 3), null).getPreferredSize().height);
		assertEquals(TrophyShelf.height(2), new TrophyShelf(finds, null).getPreferredSize().height);
		assertEquals(TrophyShelf.height(1), new TrophyShelf(finds.subList(0, 4), null).getPreferredSize().height);
		assertEquals(TrophyShelf.HEIGHT + 8 + 2 * TrophyShelf.ROW, TrophyShelf.height(2));
	}
}
