package com.seolwoni.assistcore.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Does nothing for a number of ticks (a short breather, or waiting for drops to land). */
public final class WaitAction implements Action {
	private int ticks;

	public WaitAction(int ticks) {
		this.ticks = ticks;
	}

	@Override
	public int tick(Minecraft mc, LocalPlayer p) {
		return ticks-- <= 0 ? DONE : RUN;
	}
}
