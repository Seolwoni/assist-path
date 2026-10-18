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
 * It only plans routes; Assist Core walks them like a person would.
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

	/**
	 * Plans a route from {@code start} (feet position) to {@code goal}. Runs on the calling thread
	 * (call it on the client thread). Returns null if nothing usable was found.
	 *
	 * @param primaryMs stop as soon as a usable path exists after this long
	 * @param failMs    give up after this long
	 */
	public static Route find(LocalPlayer player, Level level, BlockPos start, Goal goal, long primaryMs, long failMs) {
		long t0 = System.currentTimeMillis();
		CalculationContext ctx = new CalculationContext(player, level);
		BetterBlockPos s = new BetterBlockPos(start);
		AStarPathFinder finder = new AStarPathFinder(s, s.x, s.y, s.z, goal, new Favoring((List<BetterBlockPos>) null, ctx), ctx);
		PathResult r = finder.calculate(primaryMs, failMs);
		if (!r.found()) return null;
		Path path = r.path();
		List<Step> steps = new ArrayList<>();
		for (Movement m : path.movements()) {
			steps.add(new Step(m.kind(), m.getSrc(), m.getDest(), List.copyOf(m.toBreak(ctx.bsi)), List.copyOf(m.toPlace(ctx.bsi)), m.getCost()));
		}
		return new Route(steps, r.type() == PathResult.Type.SUCCESS_TO_GOAL, path.getNumNodesConsidered(), System.currentTimeMillis() - t0);
	}
}
