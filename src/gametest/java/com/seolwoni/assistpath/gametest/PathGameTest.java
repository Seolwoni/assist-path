package com.seolwoni.assistpath.gametest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.seolwoni.assistpath.AssistPath;
import com.seolwoni.assistpath.AssistPath.Route;
import com.seolwoni.assistpath.AssistPath.Step;
import com.seolwoni.assistpath.goals.GoalBlock;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * Plans routes through prepared terrain (one lane per case, all on stone at y 100):
 * flat, a wall to go around, steps up a hill, a lava pool to avoid, a walled room to dig out of, a drop down.
 */
public class PathGameTest implements FabricClientGameTest {
	private final List<String> log = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	private record Case(String name, int lane, BlockPos goal, String expect) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			build(sp);
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender();
			context.takeScreenshot("path-00-terrain");

			List<Case> cases = List.of(
					new Case("flat", 0, new BlockPos(0, 101, 20), "walk"),
					new Case("wall (go around)", 8, new BlockPos(8, 101, 20), "around"),
					new Case("hill (steps up 4)", 16, new BlockPos(16, 105, 20), "up"),
					new Case("lava pool (avoid)", 24, new BlockPos(24, 101, 20), "nolava"),
					new Case("closed room (dig out)", 32, new BlockPos(32, 101, 20), "dig"),
					new Case("drop (3 down)", 40, new BlockPos(40, 98, 20), "down"));
			for (Case c : cases) {
				BlockPos start = new BlockPos(c.lane(), 101, 0);
				cmd(sp, "tp @a " + (start.getX() + 0.5) + " " + start.getY() + " " + (start.getZ() + 0.5));
				context.waitTicks(10);
				Route r = context.computeOnClient(mc -> AssistPath.find(mc.player, mc.level, start, new GoalBlock(c.goal()), 2000, 6000));
				if (r == null) {
					failures.add(c.name() + ": no path");
					continue;
				}
				StringBuilder kinds = new StringBuilder();
				int breaks = 0, places = 0;
				boolean lava = false;
				for (Step s : r.steps()) {
					kinds.append(s.kind().charAt(0));
					breaks += s.toBreak().size();
					places += s.toPlace().size();
				}
				for (BlockPos cell : r.cells()) {
					boolean onLava = context.computeOnClient(mc -> mc.level.getBlockState(cell.below()).is(Blocks.LAVA)
							|| mc.level.getBlockState(cell).is(Blocks.LAVA));
					if (onLava) lava = true;
				}
				log.add(String.format("%-22s %s steps=%d cost=%.1f nodes=%d %dms breaks=%d places=%d moves=%s", c.name(),
						r.reachesGoal() ? "REACHED" : "partial", r.steps().size(), r.cost(), r.nodes(), r.millis(), breaks, places, kinds));
				if (!r.reachesGoal()) failures.add(c.name() + ": path does not reach the goal");
				switch (c.expect()) {
					case "nolava" -> {
						if (lava) failures.add(c.name() + ": path goes through lava");
					}
					case "dig" -> {
						if (breaks == 0) failures.add(c.name() + ": expected to dig out, but breaks nothing");
					}
					case "around" -> {
						if (breaks > 0) failures.add(c.name() + ": digs through the wall instead of going around");
					}
					default -> {
					}
				}
			}
		} catch (Throwable t) {
			failures.add("exception: " + t);
			throw t;
		} finally {
			writeReport();
		}
		if (!failures.isEmpty()) throw new AssertionError("Assist Path test failed: " + String.join("; ", failures));
	}

	private static void cmd(TestSingleplayerContext sp, String c) {
		sp.getServer().runCommand(c);
	}

	private void build(TestSingleplayerContext sp) {
		cmd(sp, "forceload add -8 -8 56 32");
		cmd(sp, "difficulty peaceful");
		cmd(sp, "time set noon");
		cmd(sp, "fill -6 90 -6 50 100 30 minecraft:stone");
		cmd(sp, "fill -6 101 -6 50 120 30 minecraft:air");
		// wall: 3 high across the lane, open at the sides
		cmd(sp, "fill 6 101 10 10 103 10 minecraft:stone");
		// hill: steps up to y 104 (stand at 105)
		cmd(sp, "fill 14 101 8 18 101 30 minecraft:stone");
		cmd(sp, "fill 14 102 11 18 102 30 minecraft:stone");
		cmd(sp, "fill 14 103 14 18 103 30 minecraft:stone");
		cmd(sp, "fill 14 104 17 18 104 30 minecraft:stone");
		// lava pool across the middle with a 1-wide stone path on the side
		cmd(sp, "fill 21 100 8 27 100 12 minecraft:lava");
		cmd(sp, "fill 27 100 8 27 100 12 minecraft:stone");
		// closed room around the start of lane 32
		cmd(sp, "fill 30 101 -2 34 104 2 minecraft:stone");
		cmd(sp, "fill 31 101 -1 33 102 1 minecraft:air");
		// drop: the floor after z 10 is 3 lower
		cmd(sp, "fill 38 98 10 42 100 30 minecraft:air");
		cmd(sp, "gamemode survival @a");
		cmd(sp, "clear @a");
		cmd(sp, "give @a minecraft:iron_pickaxe");
		cmd(sp, "give @a minecraft:dirt 32");
		cmd(sp, "tp @a 0.5 101 0.5 0 20");
	}

	private void writeReport() {
		try {
			List<String> out = new ArrayList<>();
			out.add(failures.isEmpty() ? "RESULT: PASS" : "RESULT: FAIL (" + failures.size() + ")");
			for (String f : failures) out.add("  FAIL " + f);
			out.add("");
			out.addAll(log);
			Path p = FabricLoader.getInstance().getGameDir().resolve("assistpath-test-result.txt");
			Files.write(p, out, StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}
}
