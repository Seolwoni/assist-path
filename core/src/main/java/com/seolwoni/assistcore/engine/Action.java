package com.seolwoni.assistcore.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * One step of work, ticked every client tick until it is done or fails.
 * Actions can push sub-actions in front of themselves on their queue and return RUN to come back afterwards.
 */
public interface Action {
	/** Results of {@link #tick}. */
	int RUN = 0, DONE = 1, FAIL = 2;
	/** What a failure means; each mod decides how to react (see {@link ActionQueue}). */
	int SKIP = 0, ABANDON = 1, END = 2, STOP = 3;

	int tick(Minecraft mc, LocalPlayer p);

	default int failMode() {
		return STOP;
	}

	default String failReason() {
		return "";
	}
}
