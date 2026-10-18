package com.seolwoni.assistcore.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.seolwoni.assistcore.CoreConfig;
import com.seolwoni.assistcore.util.Alerts;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** All Assist HUD boxes, drawn together and hidden together with one key. */
public final class AssistHud {
	private static final List<HudBox> BOXES = new ArrayList<>();

	private AssistHud() {
	}

	public static void register(HudBox box) {
		if (!BOXES.contains(box)) BOXES.add(box);
	}

	public static List<HudBox> boxes() {
		return Collections.unmodifiableList(BOXES);
	}

	/** True while the player has hidden every Assist HUD (also used by mods that draw their own HUD). */
	public static boolean hidden() {
		return CoreConfig.get().hudHidden;
	}

	public static void toggleHidden() {
		CoreConfig c = CoreConfig.get();
		c.hudHidden = !c.hudHidden;
		CoreConfig.save();
		Alerts.actionBar(c.hudHidden ? "Assist HUD 숨김" : "Assist HUD 보임", 0xFFFFAA00);
		Alerts.click();
	}

	public static void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || hidden() || mc.gui.screen() instanceof HudEditScreen) return;
		for (HudBox b : BOXES) if (b.enabled()) b.draw(g, false);
	}
}
