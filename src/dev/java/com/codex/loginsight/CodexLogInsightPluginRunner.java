package com.codex.loginsight;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CodexLogInsightPluginRunner
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CodexLogInsightPlugin.class);
		RuneLite.main(args);
	}
}
