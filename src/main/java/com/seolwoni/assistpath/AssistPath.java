/*
 * This file is part of Assist Path, which is based on Baritone.
 *
 * Assist Path is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Assist Path is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Assist Path.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seolwoni.assistpath;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.seolwoni.assistpath.calc.AStarPathFinder;
import com.seolwoni.assistpath.calc.Path;
import com.seolwoni.assistpath.calc.PathResult;
import com.seolwoni.assistpath.goals.Goal;
import com.seolwoni.assistpath.movement.CalculationContext;
import com.seolwoni.assistpath.movement.Movement;
import com.seolwoni.assistpath.util.BetterBlockPos;
import com.seolwoni.assistpath.util.Favoring;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Assist Path: path finding for the Assist series, based on Baritone's A* and movement costs.
 * It plans routes (on its own thread) and {@link com.seolwoni.assistpath.walk.PathWalker} walks them with Assist Core.
 */
public final class AssistPath implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("Assist Path");

	/** One step of a planned route. */
	public record Step(String kind, BlockPos from, BlockPos to, List<BlockPos> toBreak, List<BlockPos> toPlace, double cost) {
	}

	/** A planned route: the steps, whether it reaches the goal, and how much was searched. */
	public record Route(List<Step> steps, boolean reachesGoal, int nodes, long millis) {
		public List<BlockPos> cells() {
			List<BlockPos> out = new ArrayList<>();
			if (!steps.isEmpty()) out.add(steps.get(0).from());
			for (Step s : steps) out.add(s.to());
			return out;
		}

		public double cost() {
			double c = 0;
			for (Step s : steps) c += s.cost();
			return c;
		}
	}

	@Override
	public void onInitializeClient() {
		LOGGER.info("Assist Path loaded - path finding based on Baritone (LGPL-3.0), made by seolwoni");
	}

	/** Timing of the last plans, for the HUD and the tests. */
	public static final class Stats {
		public static volatile long lastMillis, maxMillis;
		public static volatile int plans, failed;
		/** Walks with {@link com.seolwoni.assistpath.walk.PathWalker}: started, arrived, given up; ticks spent waiting for plans and walking. */
		public static volatile int walks, arrived, gaveUp, waitTicks, walkTicks;
		public static final java.util.Map<String, Integer> reasons = new java.util.concurrent.ConcurrentHashMap<>();

		public static String summary() {
			return "path: walks=" + walks + " arrived=" + arrived + " gaveUp=" + gaveUp + " " + reasons + " plans=" + plans
					+ " notFound=" + failed + " waitTicks=" + waitTicks + " walkTicks=" + walkTicks + " slowestPlan=" + maxMillis + "ms";
		}
	}

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Assist Path planner");
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	});

	/** Same as {@link #find(LocalPlayer, Level, BlockPos, Goal, PathOptions, long, long)} with every move allowed. */
	public static Route find(LocalPlayer player, Level level, BlockPos start, Goal goal, long primaryMs, long failMs) {
		return find(player, level, start, goal, PathOptions.FULL, primaryMs, failMs);
	}

	/**
	 * Plans a route from {@code start} (feet position) to {@code goal} on the calling thread.
	 * Returns null if nothing usable was found. Prefer {@link #plan} in game: it does not hold up the frame.
	 *
	 * @param primaryMs stop as soon as a usable path exists after this long
	 * @param failMs    give up after this long
	 */
	public static Route find(LocalPlayer player, Level level, BlockPos start, Goal goal, PathOptions options, long primaryMs, long failMs) {
		return compute(new CalculationContext(player, level, false, options), start, goal, primaryMs, failMs);
	}

	/**
	 * Plans a route on the planner thread. What it needs from the player (tools, food, items) is read now,
	 * on the client thread; the search itself only reads loaded chunks. The future gives null if nothing was found.
	 */
	public static CompletableFuture<Route> plan(LocalPlayer player, Level level, BlockPos start, Goal goal, PathOptions options, long primaryMs, long failMs) {
		CalculationContext ctx = new CalculationContext(player, level, true, options);
		BlockPos s = start.immutable();
		return CompletableFuture.supplyAsync(() -> {
			try {
				return compute(ctx, s, goal, primaryMs, failMs);
			} catch (RuntimeException e) {
				// A chunk changed under the search (or was unloaded): treat as "not found", the walker plans again.
				LOGGER.debug("Path planning failed", e);
				Stats.failed++;
				return null;
			}
		}, WORKER);
	}

	private static Route compute(CalculationContext ctx, BlockPos start, Goal goal, long primaryMs, long failMs) {
		long t0 = System.currentTimeMillis();
		BetterBlockPos s = new BetterBlockPos(start);
		AStarPathFinder finder = new AStarPathFinder(s, s.x, s.y, s.z, goal, new Favoring((List<BetterBlockPos>) null, ctx), ctx);
		PathResult r = finder.calculate(primaryMs, failMs);
		long ms = System.currentTimeMillis() - t0;
		Stats.plans++;
		Stats.lastMillis = ms;
		if (ms > Stats.maxMillis) Stats.maxMillis = ms;
		if (!r.found()) {
			Stats.failed++;
			return null;
		}
		Path path = r.path();
		List<Step> steps = new ArrayList<>();
		for (Movement m : path.movements()) {
			steps.add(new Step(m.kind(), m.getSrc(), m.getDest(), List.copyOf(m.toBreak(ctx.bsi)), List.copyOf(m.toPlace(ctx.bsi)), m.getCost()));
		}
		return new Route(steps, r.type() == PathResult.Type.SUCCESS_TO_GOAL, path.getNumNodesConsidered(), System.currentTimeMillis() - t0);
	}
}
