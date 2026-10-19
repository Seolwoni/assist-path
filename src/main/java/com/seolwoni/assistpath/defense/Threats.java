package com.seolwoni.assistpath.defense;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What around the player is dangerous right now. Only what a player could know: mobs in sight (or right next to
 * us, heard), never anything behind walls further away. Neutral mobs (endermen, zombified piglins) are left alone.
 */
public final class Threats {
	private Threats() {
	}

	/** Mobs that would hurt us: hostile, alive, and seen (or very close). */
	public static List<Entity> hostiles(LocalPlayer p, double range) {
		List<Entity> out = new ArrayList<>();
		AABB box = p.getBoundingBox().inflate(range);
		for (Entity e : p.level().getEntities(p, box)) {
			if (!(e instanceof Enemy) || !e.isAlive() || e instanceof NeutralMob) continue;
			double d = e.distanceTo(p);
			if (d > range) continue;
			if (d > 4 && !p.hasLineOfSight(e)) continue;
			out.add(e);
		}
		return out;
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
		List<Entity> list = hostiles(p, 40);
		list.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
		List<BlockPos> out = new ArrayList<>();
		for (Entity e : list) {
			out.add(e.blockPosition().immutable());
			if (out.size() >= 12) break;
		}
		return out;
	}
}
