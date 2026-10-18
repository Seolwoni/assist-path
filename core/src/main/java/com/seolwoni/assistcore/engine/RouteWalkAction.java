package com.seolwoni.assistcore.engine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.seolwoni.assistcore.util.Blocks2;
import com.seolwoni.assistcore.util.Body;
import com.seolwoni.assistcore.util.Human;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Walks a list of cells in one smooth go: no stop at every cell, runs on straight stretches (unless the
 * work pace is "relaxed"), and only settles exactly on the last cell.
 */
public class RouteWalkAction implements Action {
	private final List<BlockPos> route;
	private int idx, onCell, pre = -1, post = -1, lookTicks, nextGlance;
	private Set<BlockPos> cellSet;

	public RouteWalkAction(List<BlockPos> route) {
		this.route = route;
	}

	/** Ticks of walking until the next glance (more often when relaxed). */
	private static int glanceGap() {
		double l = Human.liveliness();
		return (int) (Human.rand(120, 320) / l);
	}

	/** Index of the cell being walked to (where a fallback should pick up after a failure). */
	public int index() {
		return idx;
	}

	@Override
	public int failMode() {
		return SKIP;
	}

	/** Straight, level cells ahead of {@code from} (same step each time). */
	private int straightAhead(int from) {
		if (from + 1 >= route.size()) return 0;
		BlockPos a = route.get(from), b = route.get(from + 1);
		int sx = b.getX() - a.getX(), sy = b.getY() - a.getY(), sz = b.getZ() - a.getZ();
		int n = 1;
		for (int i = from + 1; i + 1 < route.size(); i++) {
			BlockPos c = route.get(i), d = route.get(i + 1);
			if (d.getX() - c.getX() != sx || d.getY() - c.getY() != sy || d.getZ() - c.getZ() != sz) break;
			n++;
		}
		return sy == 0 ? n : 0;
	}

	/**
	 * Cut corners like a person: on level ground, head straight for the farthest route cell that can be reached
	 * in a straight line (the strip the player's body sweeps stays on walkable cells), instead of zig-zagging
	 * from cell to cell.
	 */
	private int farthestInSight(LocalPlayer p) {
		BlockPos here = route.get(idx);
		int y = here.getY();
		if (Math.abs(p.getY() - y) > 0.3 || !p.onGround()) return idx;
		Vec3 from = p.position();
		int best = idx;
		for (int k = idx + 1; k < Math.min(route.size(), idx + 12); k++) {
			BlockPos c = route.get(k);
			if (c.getY() != y) break;
			if (!lineClear(from, c, y)) break;
			best = k;
		}
		return best;
	}

	private boolean lineClear(Vec3 from, BlockPos to, int y) {
		double tx = to.getX() + 0.5, tz = to.getZ() + 0.5;
		double dx = tx - from.x, dz = tz - from.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 0.01) return true;
		double px = -dz / len * 0.32, pz = dx / len * 0.32;
		int steps = (int) Math.ceil(len / 0.2);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int i = 0; i <= steps; i++) {
			double t = (double) i / steps;
			double x = from.x + dx * t, z = from.z + dz * t;
			for (int side = -1; side <= 1; side++) {
				m.set(Mth.floor(x + px * side), y, Mth.floor(z + pz * side));
				if (cellSet.contains(m)) continue;
				if (!Blocks2.walkable(m)) return false;
			}
		}
		return true;
	}

	@Override
	public int tick(Minecraft mc, LocalPlayer p) {
		if (route.isEmpty()) return DONE;
		if (cellSet == null) cellSet = new HashSet<>(route);
		if (idx < route.size() - 1) {
			int k = farthestInSight(p);
			if (k > idx) {
				idx = k;
				onCell = 0;
			}
		}
		if (pre < 0) {
			pre = Human.delay(0, 4);
			Body.resetWalk();
			Body.newTurn();
			Body.stopGlance();
			nextGlance = glanceGap();
		}
		if (lookTicks >= 0) {
			// Look where the way goes before setting off (a few cells ahead, eyes a bit down), at most a moment.
			BlockPos ahead = route.get(Math.min(route.size() - 1, idx + 3));
			Vec3 at = new Vec3(ahead.getX() + 0.5, ahead.getY() + 0.6, ahead.getZ() + 0.5);
			boolean faced = p.position().distanceToSqr(at) < 2.5 || Body.lookAt(p, at, 6f);
			if ((pre-- > 0 || !faced) && lookTicks++ < 14) return RUN;
			lookTicks = -1;
		}
		if (post >= 0) return post-- <= 0 ? DONE : RUN;
		if (idx >= route.size() - 1) {
			Body.stopGlance();
			// Last cell: arrive properly.
			if (Body.walkTo(p, route.get(route.size() - 1))) {
				Body.release(mc);
				post = Human.delay(0, 3);
				return RUN;
			}
		} else {
			int straight = straightAhead(idx);
			boolean sprint = Human.pace >= 1 && straight >= 3;
			// Now and then glance aside, up or down on a straight, level stretch, walking on meanwhile.
			if (--nextGlance <= 0 && !Body.glancing()) {
				nextGlance = glanceGap();
				if (straight >= 4 && p.onGround() && !p.isInWater()) {
					float side = (float) ((6 + Human.RANDOM.nextDouble() * 8) * (Human.RANDOM.nextBoolean() ? 1 : -1));
					double r = Human.RANDOM.nextDouble();
					float pitch = r < 0.4 ? (float) (-20 + Human.RANDOM.nextDouble() * 12) // up
							: r < 0.75 ? (float) (35 + Human.RANDOM.nextDouble() * 15) // down at the ground
							: (float) (Human.RANDOM.nextDouble() * 10); // level, just aside
					Body.glance(side, pitch, Human.rand(8, 18));
				}
			}
			if (Body.walkThrough(p, route.get(idx), route.get(idx + 1), sprint)) {
				idx++;
				onCell = 0;
				return RUN;
			}
		}
		if (++onCell > 80) {
			Body.release(mc);
			return FAIL;
		}
		return RUN;
	}
}
