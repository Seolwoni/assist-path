package com.seolwoni.assistpath.gametest;

import java.util.ArrayList;
import java.util.List;

import com.seolwoni.assistcore.engine.Action;
import com.seolwoni.assistcore.util.Body;
import com.seolwoni.assistpath.defense.Defense;
import com.seolwoni.assistpath.goals.GoalBlock;
import com.seolwoni.assistpath.walk.PathWalker;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * Defense on a stone field at night, difficulty normal, no natural spawning: the player walks back and forth on a
 * 30 block route (like a mod at work, calling {@link Defense#guard} every tick first) while one mob is summoned:
 * a creeper in the way, a zombie, a skeleton at range, and a zombie with fighting switched on.
 */
public class DefenseGameTest implements FabricClientGameTest {
	private final List<String> log = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	private record Case(String name, String summon, boolean attack, int ticks) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealWorld.suite().equals("defense")) return;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			RealWorld.cmd(sp, "gamerule doMobSpawning false");
			RealWorld.cmd(sp, "gamerule doDaylightCycle false");
			RealWorld.cmd(sp, "gamerule doWeatherCycle false");
			RealWorld.cmd(sp, "gamerule spawn_mobs false");
			RealWorld.cmd(sp, "gamerule advance_time false");
			RealWorld.cmd(sp, "gamerule advance_weather false");
			RealWorld.cmd(sp, "time set midnight");
			RealWorld.cmd(sp, "forceload add -40 -40 40 40");
			List<Case> cases = List.of(
					new Case("creeper in the way", "creeper 0 101 12", false, 700),
					new Case("zombie, fighting off", "zombie 6 101 6", false, 1000),
					new Case("skeleton at range", "skeleton -12 101 30", false, 700),
					new Case("two zombies, fighting off", "zombie -6 101 10|zombie 6 101 14", false, 700),
					new Case("zombie, fighting on", "zombie 5 101 8", true, 700),
					new Case("zombie walled in beside the route", "zombie 3 101 15", true, 600));
			int n = 0;
			for (Case c : cases) {
				n++;
				build(sp);
				if (c.name().startsWith("zombie walled in")) {
					// A sealed one-block cell 2-3 blocks from the route: the zombie is heard, never seen, cannot get out.
					RealWorld.cmd(sp, "fill 2 101 14 4 104 16 minecraft:stone");
					RealWorld.cmd(sp, "fill 3 101 15 3 102 15 minecraft:air");
				}
				RealWorld.cmd(sp, "difficulty normal");
				RealWorld.cmd(sp, "kill @e[type=!player]");
				RealWorld.cmd(sp, "gamemode survival @a");
				RealWorld.cmd(sp, "clear @a");
				RealWorld.cmd(sp, "effect clear @a");
				RealWorld.cmd(sp, "effect give @a minecraft:instant_health 1 5 true");
				RealWorld.cmd(sp, "effect give @a minecraft:saturation 2 5 true");
				RealWorld.cmd(sp, "effect give @a minecraft:night_vision infinite 0 true");
				if (c.attack()) RealWorld.cmd(sp, "give @a minecraft:iron_sword");
				RealWorld.cmd(sp, "tp @a 0.5 101 0.5 0 10");
				context.waitTicks(30);
				for (String s : c.summon().split("\\|")) RealWorld.cmd(sp, "summon minecraft:" + s);
				final boolean attack = c.attack();
				context.runOnClient(mc -> {
					Defense.Stats.reset();
					Defense.reset(mc);
					Defense.config.enabled = true;
					Defense.config.attack = attack;
				});
				if (c.name().startsWith("zombie walled in")) {
					// First stand right at the wall, 2 blocks from the zombie, for two seconds: nothing to do.
					RealWorld.cmd(sp, "tp @a 1.5 101 15.5 90 0");
					context.waitTicks(10);
					int took = 0;
					for (int k = 0; k < 40; k++) {
						if (context.computeOnClient(mc -> Defense.guard(mc, mc.player))) took++;
						context.waitTicks(1);
					}
					int unreach = context.computeOnClient(mc -> Defense.Stats.unreachableTicks);
					double dz = context.computeOnClient(mc -> {
						double best = 99;
						for (var e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(6))) if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("zombie")) best = Math.min(best, e.distanceTo(mc.player));
						return best;
					});
					log.add(String.format("    at the wall: zombie %.1f blocks away, Defense took over %d of 40 ticks, unreachableTicks=%d", dz, took, unreach));
					if (took > 0) failures.add(c.name() + ": took over at the wall (" + took + " ticks)");
					if (unreach == 0) failures.add(c.name() + ": the zombie behind the wall was never looked at (distance " + dz + ")");
					RealWorld.cmd(sp, "tp @a 0.5 101 0.5 0 10");
					context.waitTicks(10);
				}
				// The "work": walk to the far end of the route and back, again and again.
				BlockPos[] ends = { new BlockPos(0, 101, 30), new BlockPos(0, 101, 0) };
				int leg = 0, guarded = 0, worked = 0;
				final PathWalker[] walker = { new PathWalker(new GoalBlock(ends[0])) };
				float minHp = 20f;
				double minCreeper = 99;
				boolean died = false;
				for (int t = 0; t < c.ticks(); t++) {
					final int legNow = leg;
					int r = context.computeOnClient(mc -> {
						if (Defense.guard(mc, mc.player)) return -1;
						return walker[0].tick(mc, mc.player);
					});
					if (r == -1) guarded++;
					else worked++;
					if (r == Action.DONE || r == Action.FAIL) {
						leg = 1 - legNow;
						final int l = leg;
						walker[0] = new PathWalker(new GoalBlock(ends[l]));
					}
					context.waitTicks(1);
					float hp = context.computeOnClient(mc -> mc.player.getHealth());
					minHp = Math.min(minHp, hp);
					if (hp <= 0) {
						died = true;
						break;
					}
					double dc = context.computeOnClient(mc -> {
						double best = 99;
						for (var e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(20))) {
							if (e instanceof net.minecraft.world.entity.monster.Creeper) best = Math.min(best, e.distanceTo(mc.player));
						}
						return best;
					});
					minCreeper = Math.min(minCreeper, dc);
					if (t % 100 == 0) {
						final int tt = t;
						log.add(context.computeOnClient(mc -> "    t=" + tt + " pos=" + mc.player.blockPosition().toShortString() + " hp=" + mc.player.getHealth()
								+ " defense=" + Defense.status()));
					}
					if (t == 120) context.takeScreenshot(String.format("defense-%02d-%s", n, c.name().replaceAll("[^a-z]+", "-")));
				}
				String gaveUp = context.computeOnClient(mc -> Defense.giveUp());
				context.runOnClient(mc -> {
					Body.release(mc);
				});
				String stats = context.computeOnClient(mc -> Defense.Stats.summary());
				int mobsLeft = context.computeOnClient(mc -> {
					int k = 0;
					for (var e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(40))) if (e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive()) k++;
					return k;
				});
				log.add(String.format("%-26s minHp=%.1f died=%s guardedTicks=%d workTicks=%d closestCreeper=%.1f mobsLeft=%d %s", c.name(), minHp, died, guarded, worked,
						minCreeper > 98 ? -1 : minCreeper, mobsLeft, stats));
				int hits = context.computeOnClient(mc -> Defense.Stats.hitsTaken);
				if (died) failures.add(c.name() + ": the player died");
				switch (n) {
					case 1 -> {
						if (minHp < 14) failures.add(c.name() + ": got hurt (health down to " + minHp + ")");
						if (stats.contains("creeperEscapes=0")) failures.add(c.name() + ": never moved away from the creeper");
					}
					case 2, 4 -> {
						// Zombies follow you: without fighting the job cannot go on, but nobody may get hurt, and after a
						// while Defense says so (the mod then stops and tells the player).
						if (hits > 3) failures.add(c.name() + ": hit " + hits + " times (more than 3)");
						if (stats.contains("flees=0")) failures.add(c.name() + ": never moved away from the zombie");
						log.add("    give up: " + gaveUp);
						if (n == 2 && gaveUp == null) failures.add(c.name() + ": kept running from a zombie for 50 s without telling the mod to stop");
					}
					case 3 -> {
						if (hits > 4) failures.add(c.name() + ": hit " + hits + " times (more than 4)");
						if (guarded == 0) log.add("    (the skeleton never came into sight this time)");
					}
					case 5 -> {
						if (stats.contains("kills=0") && mobsLeft > 0) failures.add(c.name() + ": did not deal with the zombie");
						if (hits > 4) failures.add(c.name() + ": hit " + hits + " times (more than 4)");
					}
					case 6 -> {
						// Like a zombie in the cave behind the tunnel wall: no reason to stop working.
						if (guarded > 0) failures.add(c.name() + ": took over " + guarded + " ticks for a mob that cannot get out");
					}
					default -> {
					}
				}
				for (String ev : context.computeOnClient(mc -> new ArrayList<>(Defense.Stats.events))) log.add("    event: " + ev);
			}
			context.runOnClient(mc -> Defense.config.attack = false);
		} catch (Throwable t) {
			failures.add("exception: " + t);
		} finally {
			RealWorld.writeReport("assistpath-defense", failures, log);
		}
		if (!failures.isEmpty()) throw new AssertionError("Defense test failed: " + String.join("; ", failures));
	}

	/** A walled stone field 41 x 41 at y 100 with nothing above it. */
	private static void build(TestSingleplayerContext sp) {
		RealWorld.cmd(sp, "fill -20 100 -10 20 100 40 minecraft:stone");
		RealWorld.cmd(sp, "fill -20 101 -10 20 110 40 minecraft:air");
		RealWorld.cmd(sp, "fill -21 101 -11 21 103 -11 minecraft:stone");
		RealWorld.cmd(sp, "fill -21 101 41 21 103 41 minecraft:stone");
		RealWorld.cmd(sp, "fill -21 101 -11 -21 103 41 minecraft:stone");
		RealWorld.cmd(sp, "fill 21 101 -11 21 103 41 minecraft:stone");
		// A few pillars to hide behind (a real world has trees, hills and rocks).
		for (int[] c : new int[][] { { -8, 6 }, { 8, 6 }, { -6, 20 }, { 6, 22 }, { 0, 34 }, { -12, 14 }, { 12, 14 } })
			RealWorld.cmd(sp, "fill " + c[0] + " 101 " + c[1] + " " + (c[0] + 1) + " 103 " + (c[1] + 1) + " minecraft:stone");
	}
}
