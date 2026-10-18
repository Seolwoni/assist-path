package com.seolwoni.assistcore.util;

import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Inventory queries and single clicks on the player's own inventory menu.
 * Each mod has an {@code Inv extends InvBase} that adds its own item kinds (pickaxes, seeds, ...),
 * so {@code Inv.count(...)} and friends work the same everywhere.
 */
public class InvBase {
	protected InvBase() {
	}

	public static String id(ItemStack s) {
		return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
	}

	public static String id(Item i) {
		return BuiltInRegistries.ITEM.getKey(i).toString();
	}

	/** Real food worth eating (no rotten flesh, raw chicken, golden apples, ...). */
	public static boolean isFood(ItemStack s) {
		if (s.isEmpty() || !s.has(DataComponents.FOOD)) return false;
		return !(s.is(Items.ROTTEN_FLESH) || s.is(Items.PUFFERFISH) || s.is(Items.SPIDER_EYE)
				|| s.is(Items.POISONOUS_POTATO) || s.is(Items.SUSPICIOUS_STEW) || s.is(Items.CHORUS_FRUIT)
				|| s.is(Items.CHICKEN) || s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE)
				|| s.is(Items.GOLDEN_CARROT));
	}

	/** Uses left before the item breaks (MAX_VALUE for items that do not wear out). */
	public static int remaining(ItemStack s) {
		if (!s.isDamageableItem()) return Integer.MAX_VALUE;
		return s.getMaxDamage() - s.getDamageValue();
	}

	// ---- counting ----
	public static int count(LocalPlayer p, Predicate<ItemStack> f) {
		Inventory inv = p.getInventory();
		int n = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (f.test(s)) n += s.getCount();
		}
		ItemStack off = inv.getItem(40);
		if (f.test(off)) n += off.getCount();
		return n;
	}

	public static int find(LocalPlayer p, Predicate<ItemStack> f, int from, int to) {
		Inventory inv = p.getInventory();
		for (int i = from; i < to; i++) if (f.test(inv.getItem(i))) return i;
		return -1;
	}

	public static int freeSlots(LocalPlayer p) {
		int n = 0;
		for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).isEmpty()) n++;
		return n;
	}

	// ---- clicking ----
	public static int menuSlot(int invIndex) {
		return invIndex < 9 ? InventoryMenu.USE_ROW_SLOT_START + invIndex : invIndex;
	}

	/** Inventory clicks are only safe when no container is open and nothing is on the cursor. */
	public static boolean canClick(Minecraft mc, LocalPlayer p) {
		Screen s = mc.gui.screen();
		if (s instanceof AbstractContainerScreen<?> && !(s instanceof InventoryScreen)) return false;
		return p.inventoryMenu.getCarried().isEmpty();
	}

	public static void click(Minecraft mc, LocalPlayer p, int menuSlot, int button, ContainerInput input) {
		mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, menuSlot, button, input, p);
	}

	/** Swap a main-inventory slot with hotbar slot {@code hotbar}. */
	public static void swapToHotbar(Minecraft mc, LocalPlayer p, int invIndex, int hotbar) {
		click(mc, p, menuSlot(invIndex), hotbar, ContainerInput.SWAP);
	}

	/**
	 * Makes sure an item matching {@code f} is in the hotbar and returns its hotbar slot, or -1 if there is none.
	 * Prefers an empty hotbar slot, then one whose item is not matched by {@code keep}.
	 */
	public static int bringToHotbar(Minecraft mc, LocalPlayer p, Predicate<ItemStack> f, Predicate<ItemStack> keep) {
		int h = find(p, f, 0, 9);
		if (h != -1) return h;
		int m = find(p, f, 9, 36);
		if (m == -1 || !canClick(mc, p)) return -1;
		Inventory inv = p.getInventory();
		int dst = -1;
		for (int i = 8; i >= 0; i--) {
			if (inv.getItem(i).isEmpty()) {
				dst = i;
				break;
			}
		}
		if (dst == -1) {
			for (int i = 8; i >= 0; i--) {
				if (!keep.test(inv.getItem(i))) {
					dst = i;
					break;
				}
			}
		}
		if (dst == -1) dst = 8;
		swapToHotbar(mc, p, m, dst);
		return dst;
	}

	public static void select(LocalPlayer p, int hotbar) {
		if (p.getInventory().getSelectedSlot() != hotbar) p.getInventory().setSelectedSlot(hotbar);
	}
}
