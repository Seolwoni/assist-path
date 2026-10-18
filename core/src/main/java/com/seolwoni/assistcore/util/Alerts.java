package com.seolwoni.assistcore.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Titles, action bar, chat lines and the three alert sounds used across the Assist series. */
public final class Alerts {
	private Alerts() {
	}

	public static void title(String title, String subtitle, int color) {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.hud.setTimes(2, 30, 8);
		mc.gui.hud.setSubtitle(Component.literal(subtitle == null ? "" : subtitle));
		mc.gui.hud.setTitle(Component.literal(title).withColor(color));
	}

	public static void actionBar(String text, int color) {
		Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal(text).withColor(color), false);
	}

	/** A chat line prefixed with the mod name, e.g. "[Mine Assist] ...". */
	public static void chat(String modName, String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[" + modName + "] " + text));
	}

	public static void ding() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.6f));
	}

	public static void warning() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 0.6f));
	}

	public static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT, 1.2f));
	}
}
