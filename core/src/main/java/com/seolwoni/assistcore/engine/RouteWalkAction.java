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
	/** This walk: just walk, no running (people do not always run), and whether long stretches are sprint-jumped. */
	private final boolean stroll = Human.RANDOM.nextDouble() < 0.12, hopper = Human.RANDOM.nextDouble() < 0.6;
	private Vec3 lastPos;

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

	/**
	 * How far the way goes on from here without a sharp turn: along the route from the player, through the cells
	 * ahead, until it bends more than 50 degrees from where we are heading or climbs or drops more than a block at
	 * once (slopes with one-block steps are fine: people run up and down hills). {@code level[0]} is set to how far
	 * of that is flat.
	 */
	private double runLength(LocalPlayer p, double[] level) {
		double x = p.getX(), z = p.getZ();
		double hx = 0, hz = 0, total = 0;
		int y = route.get(idx).getY();
		boolean flat = Math.abs(p.getY() - y) < 0.3;
		level[0] = 0;
		for (int i = idx; i < Math.min(route.size(), idx + 16); i++) {
			BlockPos c = route.get(i);
			if (Math.abs(c.getY() - y) > 1) break;
			if (c.getY() != y) flat = false;
			y = c.getY();
			if (Blocks2.isWater(c) || Blocks2.isWater(c.below())) break;
			double dx = c.getX() + 0.5 - x, dz = c.getZ() + 0.5 - z;
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len < 0.01) continue;
			if (hx == 0 && hz == 0) {
				hx = dx / len;
				hz = dz / len;
			} else if ((dx * hx + dz * hz) / len < 0.64) {
				break; // turns more than about 50 degrees
			}
			total += len;
			if (flat) level[0] = total;
			x = c.getX() + 0.5;
			z = c.getZ() + 0.5;
		}
		return total;
	}

	/** Room for a jump (two free cells over the head) along the next stretch: never bump into a tunnel roof. */
	private boolean openAbove(LocalPlayer p) {
		BlockPos f = p.blockPosition();
		for (int k = 2; k <= 3; k++) if (!Blocks2.passable(f.above(k))) return false;
		for (int i = idx; i < Math.min(route.size(), idx + 4); i++) {
			BlockPos c = route.get(i);
			for (int k = 2; k <= 3; k++) if (!Blocks2.passable(c.above(k))) return false;
		}
		return true;
	}

	/** Landing from a jump onto farmland tramples it. */
	private boolean farmlandAhead() {
		for (int i = Math.max(0, idx - 1); i < Math.min(route.size(), idx + 5); i++) {
			if (Blocks2.state(route.get(i).below()).is(net.minecraft.world.level.block.Blocks.FARMLAND)) return true;
		}
		return false;
	}

	@Override
	public int tick(Minecraft mc, LocalPlayer p) {
		if (route.isEmpty()) return DONE;
		Vec3 now = p.position();
		if (lastPos != null && lookTicks < 0) {
			double d = Math.sqrt((now.x - lastPos.x) * (now.x - lastPos.x) + (now.z - lastPos.z) * (now.z - lastPos.z));
			if (d < 1.5) {
				Body.Stats.walked += d;
				Body.Stats.moveTicks++;
				if (p.isSprinting()) Body.Stats.sprintTicks++;
			}
		}
		lastPos = now;
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
			// How far the way runs on level and open from here: to the cell we head for in a straight line (corners
			// cut), then on along the same line. Long enough: run; very long and open above: run and jump along.
			double[] level = new double[1];
			double run = runLength(p, level);
			boolean sprint = Human.pace >= 1 && !stroll && run >= 4.0;
			// Jumping along is fine up and down one-block steps too (it is how players run up a hill).
			boolean jump = sprint && hopper && Human.sprintJump && run >= 7.0 && openAbove(p) && !farmlandAhead();
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
			if (Body.walkThrough(p, route.get(idx), route.get(idx + 1), sprint, jump)) {
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
