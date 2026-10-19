package com.seolwoni.assistpath.gametest;

import java.util.ArrayList;
import java.util.List;

import com.seolwoni.assistcore.engine.Action;
import com.seolwoni.assistcore.util.Body;
import com.seolwoni.assistpath.AssistPath;
import com.seolwoni.assistpath.goals.GoalBlock;
import com.seolwoni.assistpath.walk.PathWalker;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;

/**
 * Real-map walking for Assist Path: in a generated world (peaceful), walk from one spot to dry-land spots 25 and
 * 50 blocks away in four directions, the way the Assist mods walk (no digging, no building).
 * real-forest (seed 1), real-hills (seed 1, windswept hills), real-river (seed 2, crossing a river), real-village (seed 3).
 */
public class PathRealTest implements FabricClientGameTest {
	private final List<String> log = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		String suite = RealWorld.suite();
		ResourceKey<Biome> biome;
		String seed;
		switch (suite) {
			case "real-forest" -> {
				biome = Biomes.FOREST;
				seed = RealWorld.SEED;
			}
			case "real-hills" -> {
				biome = Biomes.WINDSWEPT_HILLS;
				seed = RealWorld.SEED;
			}
			case "real-river" -> {
				biome = Biomes.RIVER;
				seed = RealWorld.SEED2;
			}
			case "real-village" -> {
				biome = null;
				seed = RealWorld.SEED3;
			}
			default -> {
				return;
			}
		}
		try (TestSingleplayerContext sp = RealWorld.create(context, seed)) {
			sp.getConnection().waitForChunksRender();
			RealWorld.setup(sp);
			RealWorld.cmd(sp, "gamerule mob_griefing false");
			RealWorld.cmd(sp, "gamerule mobGriefing false");
			BlockPos b = biome == null ? RealWorld.findVillage(sp, BlockPos.ZERO) : RealWorld.findBiome(sp, biome, BlockPos.ZERO);
			if (b == null) {
				failures.add("place not found: " + suite);
				throw new AssertionError("place not found: " + suite);
			}
			RealWorld.goTo(context, sp, b.getX(), b.getZ(), 88);
			int[] start = RealWorld.dryLand(sp, b.getX(), b.getZ(), 30);
			int sy = RealWorld.surface(sp, start[0], start[1]);
			log.add(suite + " (seed " + seed + "): start " + start[0] + ", " + sy + ", " + start[1]);
			RealWorld.cmd(sp, "give @a minecraft:bread 16");
			RealWorld.cmd(sp, "effect give @a minecraft:saturation infinite 0 true");
			int farmBefore = farmland(sp, start[0], start[1]);

			int arrived = 0, total = 0, shots = 0;
			long ticksAll = 0, worstTick = 0;
			int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
			for (int dist : new int[] {25, 50}) {
				for (int[] d : dirs) {
					int[] goal = RealWorld.dryLand(sp, start[0] + d[0] * dist, start[1] + d[1] * dist, 8);
					int gy = RealWorld.surface(sp, goal[0], goal[1]);
					BlockPos target = new BlockPos(goal[0], gy, goal[1]);
					RealWorld.cmd(sp, "tp @a " + (start[0] + 0.5) + " " + sy + " " + (start[1] + 0.5));
					context.waitTicks(20);
					PathWalker walker = new PathWalker(new GoalBlock(target));
					int r = Action.RUN, ticks = 0, wet = 0;
					long slow = 0;
					float hp0 = context.computeOnClient(mc -> mc.player.getHealth());
					int limit = 20 * (dist == 25 ? 30 : 60);
					while (r == Action.RUN && ticks < limit) {
						long t0 = System.nanoTime();
						r = context.computeOnClient(mc -> walker.tick(mc, mc.player));
						slow = Math.max(slow, (System.nanoTime() - t0) / 1_000_000);
						context.waitTicks(1);
						ticks++;
						if (context.computeOnClient(mc -> mc.player.isInWater())) wet++;
						if (ticks % 600 == 0) context.takeScreenshot(String.format("%s-%03d", suite, shots++));
					}
					final int res = r;
					context.runOnClient(mc -> Body.release(mc));
					BlockPos at = context.computeOnClient(mc -> PathWalker.feet(mc.player));
					float hp1 = context.computeOnClient(mc -> mc.player.getHealth());
					boolean ok = res == Action.DONE && at.distManhattan(target) <= 2;
					total++;
					if (ok) arrived++;
					ticksAll += ticks;
					worstTick = Math.max(worstTick, slow);
					double straight = Math.sqrt(Math.pow(target.getX() - start[0], 2) + Math.pow(target.getZ() - start[1], 2));
					log.add(String.format("%2d m %-6s -> %s: %s in %d ticks (%.1f blocks/s), replans=%d, in water %d ticks, health %.0f->%.0f, slowest tick %d ms%s",
							dist, d[0] > 0 ? "east" : d[0] < 0 ? "west" : d[1] > 0 ? "south" : "north", target.toShortString(),
							ok ? "ARRIVED" : res == Action.FAIL ? "FAILED (" + walker.failReason() + ")" : "TIMEOUT", ticks, straight / (ticks / 20.0),
							walker.replans(), wet, hp0, hp1, slow, ok ? "" : " — stopped at " + at.toShortString()));
					if (hp1 < hp0 - 2) failures.add("got hurt on the way to " + target.toShortString());
				}
			}
			int farmAfter = farmland(sp, start[0], start[1]);
			log.add(String.format("arrived %d of %d (%.0f%%), %d ticks walking in all, slowest walker tick %d ms; planner: %s", arrived, total, arrived * 100.0 / total,
					ticksAll, worstTick, AssistPath.Stats.summary()));
			log.add("farmland around the start: " + farmBefore + " -> " + farmAfter);
			if (farmAfter < farmBefore) failures.add("trampled " + (farmBefore - farmAfter) + " farmland");
			if (worstTick > 50) failures.add("a walker tick took " + worstTick + " ms");
			if (arrived * 100 < total * 75) failures.add("arrived only " + arrived + " of " + total);
		} catch (Throwable t) {
			failures.add("exception: " + t);
			throw t;
		} finally {
			RealWorld.writeReport("assistpath-" + suite, failures, log);
		}
		if (!failures.isEmpty()) throw new AssertionError("Assist Path " + suite + " failed: " + String.join("; ", failures));
	}

	private static int farmland(TestSingleplayerContext sp, int x, int z) {
		return sp.getServer().computeOnServer(server -> {
			int n = 0;
			for (BlockPos p : BlockPos.betweenClosed(x - 60, 40, z - 60, x + 60, 140, z + 60)) if (server.overworld().getBlockState(p).is(Blocks.FARMLAND)) n++;
			return n;
		});
	}
}
