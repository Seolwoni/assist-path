package com.seolwoni.assistcore.storage;

import java.util.List;

import com.seolwoni.assistcore.util.Alerts;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Marking storage chests and the small helpers every storage trip needs. */
public final class Chests {
	public static final int MAX = 10;

	private Chests() {
	}

	/** K pressed: marks the chest being looked at, or unmarks it if it was marked. */
	public static void toggleLookedAt(Minecraft mc, List<ChestEntry> chests, Runnable save) {
		HitResult hit = mc.hitResult;
		if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK || mc.level == null) {
			Alerts.actionBar("상자를 바라보고 눌러주세요", 0xFFFF5555);
			return;
		}
		BlockPos pos = bhr.getBlockPos();
		if (!(mc.level.getBlockEntity(pos) instanceof Container)) {
			Alerts.actionBar("상자나 통 같은 보관함이 아니에요", 0xFFFF5555);
			return;
		}
		for (int i = 0; i < chests.size(); i++) {
			ChestEntry e = chests.get(i);
			if (e.x == pos.getX() && e.y == pos.getY() && e.z == pos.getZ()) {
				chests.remove(i);
				save.run();
				Alerts.actionBar("보관 상자 " + (i + 1) + "번 지정 해제", 0xFFFFAA00);
				Alerts.click();
				return;
			}
		}
		if (chests.size() >= MAX) {
			Alerts.actionBar("상자는 " + MAX + "개까지 지정할 수 있어요", 0xFFFF5555);
			return;
		}
		ChestEntry e = new ChestEntry();
		e.x = pos.getX();
		e.y = pos.getY();
		e.z = pos.getZ();
		chests.add(e);
		save.run();
		Alerts.actionBar("보관 상자 " + chests.size() + "번 지정: " + pos.toShortString() + " (설정 → 보관에서 담을 아이템 지정)", 0xFF55FF55);
		Alerts.click();
	}

	/** Which chest an item should go to: chests that list it first, then overflow chests. -1 if none has room. */
	public static int target(List<ChestEntry> chests, String itemId, boolean[] full) {
		for (int i = 0; i < chests.size(); i++) if (!full[i] && chests.get(i).items.contains(itemId)) return i;
		for (int i = 0; i < chests.size(); i++) if (!full[i] && chests.get(i).items.isEmpty()) return i;
		return -1;
	}

	/** The open chest/barrel screen, or null. */
	public static AbstractContainerScreen<?> openScreen(Minecraft mc) {
		Screen s = mc.gui.screen();
		if (s instanceof AbstractContainerScreen<?> acs && !(s instanceof InventoryScreen)) return acs;
		return null;
	}
}
