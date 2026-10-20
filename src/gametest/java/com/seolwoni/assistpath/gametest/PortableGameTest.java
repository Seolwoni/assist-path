package com.seolwoni.assistpath.gametest;

import java.util.ArrayList;
import java.util.List;

import com.seolwoni.assistcore.engine.Action;
import com.seolwoni.assistcore.storage.Chests;
import com.seolwoni.assistcore.storage.PortableStorage;
import com.seolwoni.assistcore.util.Body;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.AABB;

/**
 * Storing into a carried box (Assist Core {@link PortableStorage}) on a stone field, survival: the inventory is
 * full of cobblestone, the box is put down next to the player, filled, and (where it can be) picked up again.
 */
public class PortableGameTest implements FabricClientGameTest {
	private final List<String> log = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealWorld.suite().equals("portable")) return;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			RealWorld.cmd(sp, "gamerule spawn_mobs false");
			RealWorld.cmd(sp, "gamerule advance_time false");
			RealWorld.cmd(sp, "time set noon");
			RealWorld.cmd(sp, "difficulty peaceful");
			RealWorld.cmd(sp, "forceload add -20 -20 20 20");

			// 1. Shulker box: 27 stacks go in, the box comes back with them.
			setup(sp, context, true, true, true);
			Result r = run(context, PortableStorage.SHULKER, true);
			log.add("shulker: " + r);
			if (!"DONE".equals(r.end)) failures.add("shulker: ended " + r.end + " (" + r.reason + ")");
			if (r.cobbleBefore - r.cobbleAfter != 27 * 64) failures.add("shulker: moved " + (r.cobbleBefore - r.cobbleAfter) + " cobblestone, not 1728");
			if (r.shulkersAfter != 1) failures.add("shulker: " + r.shulkersAfter + " shulker boxes in the inventory afterwards");
			if (r.boxesStanding != 0) failures.add("shulker: a box was left standing");
			if (r.dropsLeft != 0) failures.add("shulker: a box lies on the ground");
			if (!r.mainHandBack) failures.add("shulker: did not put the pickaxe back in hand");

			// 2. The same full shulker box again: it is known full now, so there is nothing to use.
			int none = context.computeOnClient(mc -> PortableStorage.choose(mc.player, PortableStorage.SHULKER, true) == PortableStorage.Kind.NONE ? 1 : 0);
			log.add("full shulker skipped: " + (none == 1));
			if (none != 1) failures.add("full shulker: would be put down again");

			// 3. Ender chest with a Silk Touch pickaxe: comes back.
			setup(sp, context, false, true, true);
			r = run(context, PortableStorage.ENDER, true);
			log.add("ender + silk touch: " + r);
			if (!"DONE".equals(r.end)) failures.add("ender silk: ended " + r.end + " (" + r.reason + ")");
			if (r.cobbleBefore - r.cobbleAfter != 27 * 64) failures.add("ender silk: moved " + (r.cobbleBefore - r.cobbleAfter));
			if (r.endersAfter != 1) failures.add("ender silk: ender chest not back in the inventory");
			if (r.boxesStanding != 0) failures.add("ender silk: ender chest left standing");

			// 4. Ender chest without Silk Touch: stays where it was put (never broken into obsidian).
			setup(sp, context, false, true, false);
			r = run(context, PortableStorage.ENDER, true);
			log.add("ender, no silk touch: " + r);
			if (!"DONE".equals(r.end)) failures.add("ender left: ended " + r.end + " (" + r.reason + ")");
			if (r.cobbleBefore - r.cobbleAfter != 27 * 64) failures.add("ender left: moved " + (r.cobbleBefore - r.cobbleAfter));
			if (r.boxesStanding != 1) failures.add("ender left: no ender chest standing next to the player");
			if (r.obsidian > 0) failures.add("ender left: it was broken into obsidian");

			// 5. Interrupted (a mob made us run): the queue is dropped, the mod asks for the trip back, the box is
			// fetched from 4 blocks away.
			setup(sp, context, true, true, true);
			r = runInterrupted(sp, context);
			log.add("shulker, interrupted: " + r);
			if (!"DONE".equals(r.end)) failures.add("interrupted: ended " + r.end + " (" + r.reason + ")");
			if (r.shulkersAfter != 1) failures.add("interrupted: shulker box not back (" + r.shulkersAfter + ")");
			if (r.boxesStanding != 0) failures.add("interrupted: box left standing");
			context.takeScreenshot("portable-end");
		} catch (Throwable t) {
			failures.add("exception: " + t);
		} finally {
			RealWorld.writeReport("assistpath-portable", failures, log);
		}
		if (!failures.isEmpty()) throw new AssertionError("Portable storage test failed: " + String.join("; ", failures));
	}

	private record Result(String end, String reason, int ticks, int cobbleBefore, int cobbleAfter, int shulkersAfter, int endersAfter,
			int boxesStanding, int dropsLeft, int obsidian, boolean mainHandBack) {
		@Override
		public String toString() {
			return end + " in " + ticks + " ticks" + (reason.isEmpty() ? "" : " (" + reason + ")") + ", cobblestone " + cobbleBefore + " -> " + cobbleAfter
					+ ", shulker boxes " + shulkersAfter + ", ender chests " + endersAfter + ", standing " + boxesStanding + ", drops " + dropsLeft
					+ ", obsidian " + obsidian + ", hand back " + mainHandBack;
		}
	}

	private void setup(TestSingleplayerContext sp, ClientGameTestContext context, boolean shulker, boolean ender, boolean silk) {
		RealWorld.cmd(sp, "fill -10 100 -10 10 100 10 minecraft:stone");
		RealWorld.cmd(sp, "fill -10 101 -10 10 106 10 minecraft:air");
		RealWorld.cmd(sp, "kill @e[type=!player]");
		RealWorld.cmd(sp, "gamemode survival @a");
		RealWorld.cmd(sp, "clear @a");
		for (int i = 0; i < 27; i++) RealWorld.cmd(sp, "item replace entity @a enderchest." + i + " with minecraft:air");
		RealWorld.cmd(sp, "tp @a 0.5 101 0.5 0 20");
		if (silk) RealWorld.cmd(sp, "give @a minecraft:diamond_pickaxe[minecraft:enchantments={\"minecraft:silk_touch\":1}]");
		else RealWorld.cmd(sp, "give @a minecraft:diamond_pickaxe");
		RealWorld.cmd(sp, "give @a minecraft:iron_sword");
		if (shulker) RealWorld.cmd(sp, "give @a minecraft:shulker_box");
		if (ender) RealWorld.cmd(sp, "give @a minecraft:ender_chest");
		RealWorld.cmd(sp, "give @a minecraft:cobblestone 2048");
		context.waitTicks(20);
		context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		context.waitTicks(5);
	}

	private Result run(ClientGameTestContext context, int mode, boolean canLeave) {
		int before = context.computeOnClient(mc -> count(mc, Items.COBBLESTONE));
		final PortableStorage[] box = { null };
		context.runOnClient(mc -> box[0] = PortableStorage.trip("test", mc.player, mode,
				i -> mc.player.getInventory().getItem(i).is(Items.COBBLESTONE), null, canLeave));
		if (box[0] == null) return new Result("NONE", "", 0, before, before, 0, 0, 0, 0, 0, false);
		return finish(context, box[0], before);
	}

	private Result runInterrupted(TestSingleplayerContext sp, ClientGameTestContext context) {
		int before = context.computeOnClient(mc -> count(mc, Items.COBBLESTONE));
		final PortableStorage[] box = { null };
		context.runOnClient(mc -> box[0] = PortableStorage.trip("test", mc.player, PortableStorage.SHULKER,
				i -> mc.player.getInventory().getItem(i).is(Items.COBBLESTONE), null, true));
		if (box[0] == null) return new Result("NONE", "", 0, before, before, 0, 0, 0, 0, 0, false);
		int t = 0;
		for (; t < 600; t++) {
			int r = context.computeOnClient(mc -> box[0].tick(mc, mc.player));
			context.waitTicks(1);
			if (r != Action.RUN) return new Result(r == Action.DONE ? "DONE(too early)" : "FAIL", box[0].failReason(), t, before, 0, 0, 0, 0, 0, 0, false);
			if (box[0].moved() >= 5) break; // part of the loot is in
		}
		// The mob: the screen closes, the player is somewhere else, the queue is gone.
		context.runOnClient(mc -> {
			Body.release(mc);
			if (Chests.openScreen(mc) != null) mc.player.closeContainer();
		});
		RealWorld.cmd(sp, "tp @a 4.5 101 3.5 90 0");
		context.waitTicks(10);
		final Action[] back = { null };
		context.runOnClient(mc -> back[0] = PortableStorage.interrupted(mc));
		if (back[0] == null) return new Result("NOT RESUMABLE", "", t, before, 0, 0, 0, 0, 0, 0, false);
		return finish(context, back[0], before);
	}

	private Result finish(ClientGameTestContext context, Action a, int before) {
		int r = Action.RUN, t = 0;
		for (; t < 1600 && r == Action.RUN; t++) {
			r = context.computeOnClient(mc -> a.tick(mc, mc.player));
			context.waitTicks(1);
		}
		context.runOnClient(Body::release);
		context.waitTicks(20);
		String end = r == Action.DONE ? "DONE" : r == Action.FAIL ? "FAIL" : "TIMEOUT";
		int ticks = t;
		return context.computeOnClient(mc -> {
			int standing = 0;
			BlockPos f = mc.player.blockPosition();
			for (BlockPos q : BlockPos.betweenClosed(f.offset(-8, -2, -8), f.offset(8, 3, 8))) {
				var s = mc.level.getBlockState(q);
				if (s.is(Blocks.ENDER_CHEST) || s.getBlock() instanceof ShulkerBoxBlock) standing++;
			}
			int drops = 0, obsidian = 0;
			for (var e : mc.level.getEntities(mc.player, new AABB(f).inflate(10))) {
				if (e instanceof ItemEntity ie) {
					if (PortableStorage.isPortable(ie.getItem())) drops++;
					if (ie.getItem().is(Items.OBSIDIAN)) obsidian += ie.getItem().getCount();
				}
			}
			obsidian += count(mc, Items.OBSIDIAN);
			int shulkers = 0, enders = 0;
			for (int i = 0; i < 36; i++) {
				ItemStack s = mc.player.getInventory().getItem(i);
				if (PortableStorage.isShulkerItem(s)) shulkers++;
				if (PortableStorage.isEnderItem(s)) enders++;
			}
			boolean hand = mc.player.getMainHandItem().is(Items.DIAMOND_PICKAXE);
			return new Result(end, a.failReason(), ticks, before, count(mc, Items.COBBLESTONE), shulkers, enders, standing, drops, obsidian, hand);
		});
	}

	private static int count(Minecraft mc, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack s = mc.player.getInventory().getItem(i);
			if (s.is(item)) n += s.getCount();
		}
		return n;
	}
}
