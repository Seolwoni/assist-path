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

package com.seolwoni.assistpath.walk;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.seolwoni.assistcore.engine.Action;
import com.seolwoni.assistcore.engine.RouteWalkAction;
import com.seolwoni.assistcore.util.Body;
import com.seolwoni.assistpath.AssistPath;
import com.seolwoni.assistpath.AssistPath.Route;
import com.seolwoni.assistpath.PathOptions;
import com.seolwoni.assistpath.goals.Goal;
import com.seolwoni.assistpath.movement.MovementHelper;
import com.seolwoni.assistpath.util.BlockStateInterface;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/**
 * Gets the player to a goal: plans a route on the planner thread (the game keeps running meanwhile), walks it
 * with Assist Core (no stop at every cell, runs on straight stretches), and plans again when the route turns
 * out blocked, the walk gets stuck, or the route only went part of the way.
 *
 * Use it inside an action ({@link #tick} returns {@link Action#RUN}, {@link Action#DONE} or {@link Action#FAIL}),
 * or queue a {@link PathWalkAction}.
 */
public final class PathWalker {
	/** How long one plan may search: answer fast with what is there, give up after a while. */
	private static final long PRIMARY_MS = 300, FAIL_MS = 2000;
	/** Ticks to wait for a plan before giving up (the planner thread may be busy). */
	private static final int PLAN_WAIT_TICKS = 100;

	private final Goal goal;
	private final PathOptions options;
	private final int maxReplans;
	private CompletableFuture<Route> plan;
	private RouteWalkAction walk;
	private List<BlockPos> cells;
	private boolean partial;
	private int planTicks, replans, legs, sinceCheck, notFound;
	private String failReason = "";

	public PathWalker(Goal goal, PathOptions options, int maxReplans) {
		this.goal = goal;
		this.options = options;
		this.maxReplans = maxReplans;
	}

	public PathWalker(Goal goal) {
		this(goal, PathOptions.WALK, 6);
	}

	/** Feet position the way Baritone counts it (standing on farmland or a slab still counts as the cell above). */
	public static BlockPos feet(LocalPlayer p) {
		return BlockPos.containing(p.getX(), p.getY() + 0.1251, p.getZ());
	}

	public String failReason() {
		return failReason;
	}

	/** Cells of the route being walked (empty while planning). */
	public List<BlockPos> cells() {
		return cells == null ? List.of() : cells;
	}

	/** Index of the route cell being walked to (-1 while planning). */
	public int index() {
		return walk == null ? -1 : walk.index();
	}

	public int replans() {
		return replans;
	}

	private boolean counted;

	private int fail(Minecraft mc, String why) {
		AssistPath.Stats.gaveUp++;
		AssistPath.Stats.reasons.merge(why, 1, Integer::sum);
		Body.release(mc);
		if (plan != null) plan.cancel(false);
		plan = null;
		walk = null;
		failReason = why;
		return Action.FAIL;
	}

	private boolean replan(Minecraft mc) {
		Body.release(mc);
		walk = null;
		cells = null;
		return ++replans <= maxReplans;
	}

	/** True if the next few cells of the route are still open (a block may have been placed or fallen in). */
	private boolean aheadOpen(Minecraft mc) {
		int at = walk.index();
		BlockStateInterface bsi = new BlockStateInterface(mc.level);
		for (int i = at; i < Math.min(cells.size(), at + 4); i++) {
			BlockPos c = cells.get(i);
			if (!MovementHelper.canWalkThrough(bsi, c.getX(), c.getY(), c.getZ())) return false;
			if (!MovementHelper.canWalkThrough(bsi, c.getX(), c.getY() + 1, c.getZ())) return false;
		}
		return true;
	}

	public int tick(Minecraft mc, LocalPlayer p) {
		if (!counted) {
			counted = true;
			AssistPath.Stats.walks++;
		}
		if (walk == null) {
			if (plan == null) {
				BlockPos from = feet(p);
				if (goal.isInGoal(from) && (cells != null || replans > 0)) {
					Body.release(mc);
					AssistPath.Stats.arrived++;
					return Action.DONE;
				}
				plan = AssistPath.plan(p, mc.level, from, goal, options, PRIMARY_MS, FAIL_MS);
				planTicks = 0;
				Body.release(mc);
			}
			if (!plan.isDone()) {
				// Planning: stand still for a moment, like someone looking where to go.
				AssistPath.Stats.waitTicks++;
				if (++planTicks > PLAN_WAIT_TICKS) return fail(mc, "길을 찾는 데 너무 오래 걸려요");
				return Action.RUN;
			}
			Route r = plan.getNow(null);
			plan = null;
			if (r == null) {
				// Not found twice in a row: there is no way (no point in searching again and again).
				if (++notFound < 2 && replan(mc)) return Action.RUN;
				return fail(mc, "갈 수 있는 길이 없어요");
			}
			notFound = 0;
			for (AssistPath.Step s : r.steps()) {
				if ((!options.allowBreak() && !s.toBreak().isEmpty()) || (!options.allowPlace() && !s.toPlace().isEmpty())) {
					return fail(mc, "길을 내야 갈 수 있는 곳이에요");
				}
			}
			if (r.steps().isEmpty()) {
				if (goal.isInGoal(feet(p))) {
					cells = List.of(feet(p));
					walk = new RouteWalkAction(cells); // settle on the cell
				} else {
					if (replan(mc)) return Action.RUN;
					return fail(mc, "갈 수 있는 길이 없어요");
				}
			} else {
				cells = r.cells();
				walk = new RouteWalkAction(cells);
			}
			partial = !r.reachesGoal();
			sinceCheck = 0;
		}
		if (++sinceCheck >= 10) {
			sinceCheck = 0;
			if (!aheadOpen(mc)) {
				if (replan(mc)) return Action.RUN;
				return fail(mc, "길이 계속 막혀요");
			}
		}
		AssistPath.Stats.walkTicks++;
		int r = walk.tick(mc, p);
		if (r == Action.DONE) {
			if (!partial && goal.isInGoal(feet(p))) {
				walk = null;
				AssistPath.Stats.arrived++;
				return Action.DONE;
			}
			// Only part of the way (or slipped off the goal): plan the rest from here.
			if (partial && ++legs <= 40) {
				Body.release(mc);
				walk = null;
				cells = null;
				return Action.RUN;
			}
			if (replan(mc)) return Action.RUN;
			return fail(mc, "목적지까지 가지 못했어요");
		}
		if (r == Action.FAIL) {
			if (replan(mc)) return Action.RUN;
			return fail(mc, "길에서 자꾸 막혀요");
		}
		return Action.RUN;
	}
}
