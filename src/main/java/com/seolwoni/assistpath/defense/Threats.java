package com.seolwoni.assistpath.defense;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What around the player is dangerous right now. Only what a player could know: mobs in sight (or right next to
 * us, heard), never anything behind walls further away. Neutral mobs (endermen, zombified piglins) are left alone.
 * <p>
 * A mob that cannot get to us is no danger either, the way a player in a tunnel does not mind a zombie groaning in
 * the cave behind the wall: mobs that fight up close count only if they can walk to us (a short search over the
 * blocks between, like a mob's own pathfinding would do). Mobs that shoot count when they can see us.
 */
public final class Threats {
	private Threats() {
	}

	/** How far (blocks) the walk-to-us check looks; a mob further away than this is judged by sight alone. */
	private static final int REACH_RADIUS = 12;
	private static final int REACH_NODES = 600;

	private record Cached(boolean reach, long until) {
	}

	/** Reachability per mob, kept for half a second (mobs move, but not that fast). */
	private static final Map<Integer, Cached> REACH = new HashMap<>();
	/** Mobs Defense gave up on (could not land a hit): ignored for a while unless they come right up to us. */
	private static final Map<Integer, Long> IGNORE = new HashMap<>();

	/** Mobs that would hurt us: hostile, alive, seen (or very close), and able to get at us. */
	public static List<Entity> hostiles(LocalPlayer p, double range) {
		List<Entity> out = new ArrayList<>();
		AABB box = p.getBoundingBox().inflate(range);
		long now = p.level().getGameTime();
		for (Entity e : p.level().getEntities(p, box)) {
			if (!(e instanceof Enemy) || !e.isAlive() || e instanceof NeutralMob) continue;
			double d = e.distanceTo(p);
			if (d > range) continue;
			boolean sees = p.hasLineOfSight(e);
			if (d > 4 && !sees) continue;
			Long ig = IGNORE.get(e.getId());
			if (ig != null && ig > now && d > 2.5) continue;
			if (!Defense.ranged(e) || !sees) {
				// Up close only: it has to be able to walk to us. (Spiders climb walls: sight is enough for them.)
				if (!(climber(e) && sees) && d <= REACH_RADIUS && !canReach(p, e, now)) {
					Defense.Stats.unreachableTicks++;
					continue;
				}
			}
			out.add(e);
		}
		return out;
	}

	/** Defense could not hit this one: leave it alone for {@code ticks} unless it comes right up to us. */
	static void ignore(LocalPlayer p, Entity e, int ticks) {
		IGNORE.put(e.getId(), p.level().getGameTime() + ticks);
		if (IGNORE.size() > 64) IGNORE.values().removeIf(t -> t < p.level().getGameTime());
	}

	private static boolean climber(Entity e) {
		String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
		return id.contains("spider");
	}

	private static boolean canReach(LocalPlayer p, Entity e, long now) {
		Cached c = REACH.get(e.getId());
		if (c != null && c.until > now) return c.reach;
		boolean r = walkable(p.level(), e.blockPosition(), p.blockPosition());
		REACH.put(e.getId(), new Cached(r, now + 10));
		if (REACH.size() > 128) REACH.values().removeIf(x -> x.until < now);
		return r;
	}

	/**
	 * Can something the size of a zombie walk from {@code from} to next to {@code to}? Steps up one block, drops up
	 * to three, wades through water, never through lava or solid blocks; stays within {@link #REACH_RADIUS}.
	 */
	static boolean walkable(Level level, BlockPos from, BlockPos to) {
		BlockPos start = floor(level, from);
		if (start == null) return false;
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		open.add(start);
		seen.add(start);
		while (!open.isEmpty() && seen.size() < REACH_NODES) {
			BlockPos c = open.poll();
			if (Math.abs(c.getX() - to.getX()) <= 1 && Math.abs(c.getZ() - to.getZ()) <= 1 && Math.abs(c.getY() - to.getY()) <= 1) return true;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos n = c.relative(d);
				BlockPos next = null;
				if (room(level, n)) {
					// Level, or drop down to the floor below (at most three).
					for (int k = 0; k <= 3; k++) {
						BlockPos q = n.below(k);
						if (!room(level, q)) break;
						if (!room(level, q.below()) || wet(level, q)) {
							next = q;
							break;
						}
					}
				} else if (room(level, n.above()) && clear(level, c.above(2))) {
					next = n.above(); // a step up
				}
				if (next == null || lava(level, next) || lava(level, next.below())) continue;
				if (Math.abs(next.getX() - to.getX()) > REACH_RADIUS || Math.abs(next.getZ() - to.getZ()) > REACH_RADIUS
						|| Math.abs(next.getY() - to.getY()) > REACH_RADIUS) continue;
				if (seen.add(next)) open.add(next);
			}
		}
		return false;
	}

	/** The cell a mob standing at {@code p} has its feet in (it may be in the air for a moment: look a little down). */
	private static BlockPos floor(Level level, BlockPos p) {
		for (int k = 0; k <= 3; k++) {
			BlockPos q = p.below(k);
			if (!room(level, q)) return k == 0 ? (room(level, p.above()) ? p.above() : null) : q.above();
			if (!room(level, q.below()) || wet(level, q)) return q;
		}
		return p;
	}

	/** Room for a mob's body: feet and head cells without collision. */
	private static boolean room(Level level, BlockPos q) {
		return clear(level, q) && clear(level, q.above());
	}

	private static boolean clear(Level level, BlockPos q) {
		BlockState s = level.getBlockState(q);
		return s.getCollisionShape(level, q).isEmpty() && !s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
	}

	private static boolean wet(Level level, BlockPos q) {
		return level.getBlockState(q).getFluidState().is(net.minecraft.tags.FluidTags.WATER);
	}

	private static boolean lava(Level level, BlockPos q) {
		return level.getBlockState(q).getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
	}

	public static Creeper nearCreeper(LocalPlayer p, double range) {
		Creeper best = null;
		double bestD = Double.MAX_VALUE;
		for (Entity e : hostiles(p, range)) {
			if (e instanceof Creeper c && c.distanceTo(p) < bestD) {
				bestD = c.distanceTo(p);
				best = c;
			}
		}
		return best;
	}

	/**
	 * A projectile flying at us: where it will pass closest within the next {@code ticks} ticks.
	 * Returns its velocity if it comes within {@code margin} blocks of our body, else null.
	 */
	public static Vec3 incoming(LocalPlayer p, int ticks, double margin) {
		Vec3 me = p.position().add(0, p.getBbHeight() * 0.5, 0);
		for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(24))) {
			if (!(e instanceof Projectile pr) || !e.isAlive()) continue;
			if (pr.getOwner() == p) continue;
			Vec3 v = e.getDeltaMovement();
			if (v.lengthSqr() < 0.04) continue; // lying in the ground
			Vec3 rel = me.subtract(e.position());
			double t = rel.dot(v) / v.lengthSqr();
			if (t < 0 || t > ticks) continue;
			Vec3 closest = e.position().add(v.scale(t));
			if (closest.distanceTo(me) < margin) return v;
		}
		return null;
	}

	/** For the path planner: block positions of visible hostile mobs (at most 12, nearest first). */
	public static List<BlockPos> avoidPositions(LocalPlayer p) {
		if (p == null || p.level() == null) return List.of();
		List<Entity> list = new ArrayList<>();
		for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(40))) {
			if (!(e instanceof Enemy) || !e.isAlive() || e instanceof NeutralMob) continue;
			if (e.distanceTo(p) > 40 || !p.hasLineOfSight(e)) continue;
			list.add(e);
		}
		list.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
		List<BlockPos> out = new ArrayList<>();
		for (Entity e : list) {
			out.add(e.blockPosition().immutable());
			if (out.size() >= 12) break;
		}
		return out;
	}
}
