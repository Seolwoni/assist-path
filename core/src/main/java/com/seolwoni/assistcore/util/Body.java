package com.seolwoni.assistcore.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Smooth, human-looking looking and walking. */
public final class Body {
	private static float turnSpeed = 18f;
	private static int walkTicks;

	private Body() {
	}

	private static float wrap(float d) {
		d %= 360f;
		if (d >= 180f) d -= 360f;
		if (d < -180f) d += 360f;
		return d;
	}

	public static float yawTo(LocalPlayer p, Vec3 t) {
		Vec3 e = p.getEyePosition();
		return (float) (Math.toDegrees(Math.atan2(t.z - e.z, t.x - e.x)) - 90.0);
	}

	public static float pitchTo(LocalPlayer p, Vec3 t) {
		Vec3 e = p.getEyePosition();
		double dx = t.x - e.x, dy = t.y - e.y, dz = t.z - e.z;
		return (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
	}

	/** Start a new turn: picks a fresh top speed so every movement looks a little different. */
	public static void newTurn() {
		turnSpeed = (7f + Human.RANDOM.nextFloat() * 7f) * Human.turnScale();
	}

	/** Current angular speed in degrees per tick. Kept between targets so the head never jerks. */
	private static float vel;

	/**
	 * Turns towards yaw/pitch like a hand on a mouse: speeds up, cruises, then eases in.
	 * The speed carries over from the previous turn instead of snapping. Returns true once within
	 * {@code tolerance} degrees.
	 */
	public static boolean turnTo(LocalPlayer p, float yaw, float pitch, float tolerance) {
		float dy = wrap(yaw - p.getYRot());
		float dp = pitch - p.getXRot();
		float dist = (float) Math.sqrt(dy * dy + dp * dp);
		if (dist <= tolerance) {
			vel *= 0.5f;
			return true;
		}
		// A new target (moved a fair bit since last time): pick how this turn bends.
		if (Math.abs(wrap(yaw - lastYaw)) + Math.abs(pitch - lastPitch) > 15f || dist > curveFrom + 15f) {
			curve = (float) ((0.15 + Human.RANDOM.nextDouble() * 0.3) * (Human.RANDOM.nextBoolean() ? 1 : -1) * Human.liveliness());
			curveFrom = dist;
		}
		lastYaw = yaw;
		lastPitch = pitch;
		float accel = Math.max(0.6f, turnSpeed * 0.3f);
		// The fastest speed that can still slow down in time for the target.
		float want = Math.min(turnSpeed, (float) Math.sqrt(2f * accel * dist));
		want = Math.max(want, 0.6f);
		if (vel < want) vel = Math.min(want, vel + accel);
		else vel = Math.max(want, vel - accel * 1.5f);
		float step = Math.min(vel, dist);
		// Move along a gentle arc rather than a ruler-straight line: a sideways part that fades out on arrival.
		float ux = dy / dist, uy = dp / dist;
		float bend = dist > 4f ? curve * Math.min(1f, dist / 25f) : 0f;
		float mx = ux - uy * bend, my = uy + ux * bend;
		float ml = (float) Math.sqrt(mx * mx + my * my);
		float sy = step * mx / ml, sp = step * my / ml;
		p.setYRot(p.getYRot() + sy);
		p.setXRot(Math.max(-90f, Math.min(90f, p.getXRot() + sp)));
		return dist - step <= tolerance;
	}

	private static float curve, curveFrom, lastYaw, lastPitch;

	// ------------------------------------------------------------------ glances

	private static float glanceYaw, glancePitch;
	private static int glanceTicks, walkGlanceIn = 200;

	/**
	 * Look a little aside (yaw offset) and up or down (absolute pitch) for a few ticks while walking on,
	 * like glancing around. Used by {@link #walkThrough}; nothing else changes.
	 */
	public static void glance(float yawOffset, float pitch, int ticks) {
		glanceYaw = yawOffset;
		glancePitch = pitch;
		glanceTicks = ticks;
	}

	public static boolean glancing() {
		return glanceTicks > 0;
	}

	public static void stopGlance() {
		glanceTicks = 0;
	}

	public static boolean lookAt(LocalPlayer p, Vec3 t, float tolerance) {
		return turnTo(p, yawTo(p, t), pitchTo(p, t), tolerance);
	}

	// ------------------------------------------------------------------ walking

	public static void resetWalk() {
		walkTicks = 0;
	}

	public static int walkTicks() {
		return walkTicks;
	}

	/**
	 * Walks to the centre of the given feet position. Returns true when arrived.
	 * Looks where it is going with a relaxed downward pitch, and eases off near the target.
	 */
	public static boolean walkTo(LocalPlayer p, BlockPos feet) {
		Minecraft mc = Minecraft.getInstance();
		walkTicks++;
		Vec3 target = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
		Vec3 pos = p.position();
		double dx = target.x - pos.x, dz = target.z - pos.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		double dyFeet = feet.getY() - pos.y;
		boolean arrived = dist < 0.25 && Math.abs(dyFeet) < 0.6;
		if (arrived) {
			release(mc);
			return true;
		}
		if (p.isSprinting() && dist < 2.5) {
			mc.options.keySprint.setDown(false);
			p.setSprinting(false);
		}
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		// Keep the head roughly where it is while walking, only easing out of extreme angles.
		float pitch = Math.max(-10f, Math.min(35f, p.getXRot()));
		// On a longer straight walk, now and then glance aside, up or down (walking on meanwhile).
		if (dist > 5 && glanceTicks <= 0 && --walkGlanceIn <= 0 && p.onGround()) {
			walkGlanceIn = (int) (Human.rand(120, 320) / Human.liveliness());
			if (Math.abs(wrap(yaw - p.getYRot())) < 15f) {
				double r = Human.RANDOM.nextDouble();
				glance((float) ((6 + Human.RANDOM.nextDouble() * 8) * (Human.RANDOM.nextBoolean() ? 1 : -1)),
						r < 0.4 ? (float) (-20 + Human.RANDOM.nextDouble() * 12) : r < 0.75 ? (float) (35 + Human.RANDOM.nextDouble() * 15)
								: (float) (Human.RANDOM.nextDouble() * 10), Human.rand(8, 16));
			}
		}
		boolean glance = glanceTicks > 0 && dist > 2.0;
		if (glance) {
			glanceTicks--;
			turnTo(p, yaw + glanceYaw, glancePitch, 1f);
		} else {
			glanceTicks = 0;
			turnTo(p, yaw, pitch, 1f);
		}
		float yawErr = Math.abs(wrap(yaw + (glance ? glanceYaw : 0f) - p.getYRot()));
		// Ease off: only push forward while facing roughly the right way, and tap it when very close.
		// Far: walk on while turning. Close by: only small steps from (almost) standing, so it settles instead
		// of sliding past the middle and turning round again.
		Vec3 v = p.getDeltaMovement();
		double speed = Math.sqrt(v.x * v.x + v.z * v.z);
		// Let go early enough to glide to a stop near the middle (ground friction keeps about half the speed each tick).
		double glide = speed * 1.2;
		boolean push = dist > glide + 0.1 && yawErr < (dist > 0.5 ? 50f : 35f);
		mc.options.keyUp.setDown(push);
		// Swim: keep the head above water (and climb out of a water hole onto the next cell).
		mc.options.keyJump.setDown((dyFeet > 0.5 && p.onGround()) || swimUp(p, dyFeet));
		return false;
	}

	/**
	 * Walks through {@code cell} on the way to {@code next} without stopping: aims a little ahead, keeps
	 * the forward key down and jumps up steps. Returns true once {@code cell} has been passed.
	 */
	public static boolean walkThrough(LocalPlayer p, BlockPos cell, BlockPos next, boolean sprint) {
		return walkThrough(p, cell, next, sprint, false);
	}

	/** Walking numbers for the tests: how far and how fast routes are walked. */
	public static final class Stats {
		public static double walked;
		public static int moveTicks, sprintTicks, jumps;

		public static String summary() {
			return String.format("walk: %.0f blocks in %.0f s = %.2f blocks/s, sprinting %d%%, sprint-jumps %d", walked, moveTicks / 20.0,
					moveTicks > 0 ? walked / (moveTicks / 20.0) : 0, moveTicks > 0 ? 100 * sprintTicks / moveTicks : 0, jumps);
		}

		public static void reset() {
			walked = 0;
			moveTicks = sprintTicks = jumps = 0;
		}
	}

	/**
	 * As {@link #walkThrough(LocalPlayer, BlockPos, BlockPos, boolean)}; {@code jump}: the way ahead is long, level and
	 * open above, so jump along while sprinting (the quick way players cover open ground).
	 */
	public static boolean walkThrough(LocalPlayer p, BlockPos cell, BlockPos next, boolean sprint, boolean jump) {
		Minecraft mc = Minecraft.getInstance();
		walkTicks++;
		Vec3 pos = p.position();
		double cx = cell.getX() + 0.5, cz = cell.getZ() + 0.5;
		double rx = pos.x - cx, rz = pos.z - cz;
		double dist = Math.sqrt(rx * rx + rz * rz);
		double dyFeet = cell.getY() - pos.y;
		if (Math.abs(dyFeet) < 0.6) {
			if (dist < 0.55) return true;
			// Already past the cell on the way to the next one (cutting a corner a little is fine).
			double nx = next.getX() + 0.5 - cx, nz = next.getZ() + 0.5 - cz;
			double nl = Math.sqrt(nx * nx + nz * nz);
			if (nl > 0.01) {
				double along = (rx * nx + rz * nz) / nl;
				double side = Math.abs(rx * nz - rz * nx) / nl;
				if (along > -0.05 && side < 0.45 && dist < 1.2) return true;
			}
		}
		// Look ahead: once close, already steer towards the next cell.
		double tx = cx, tz = cz;
		if (dist < 0.9) {
			tx = cx + (next.getX() + 0.5 - cx) * 0.6;
			tz = cz + (next.getZ() + 0.5 - cz) * 0.6;
		}
		float yaw = (float) (Math.toDegrees(Math.atan2(tz - pos.z, tx - pos.x)) - 90.0);
		float pitch = Math.max(-10f, Math.min(30f, p.getXRot()));
		boolean glance = glanceTicks > 0;
		if (glance) {
			glanceTicks--;
			turnTo(p, yaw + glanceYaw, glancePitch, 1f);
		} else {
			turnTo(p, yaw, pitch, 1f);
		}
		// While glancing the head is off the way on purpose: keep walking on.
		float yawErr = Math.abs(wrap(yaw + (glance ? glanceYaw : 0f) - p.getYRot()));
		// Keep walking while turning a corner, like a person does (only stop for a sharp turn).
		// (close to the cell only when facing it, or it would circle round it).
		mc.options.keyUp.setDown(yawErr < (dist > 2.0 ? 75f : dist > 1.0 ? 45f : 30f));
		// Keep running through a gentle bend (a person does not let go of sprint for every few degrees).
		boolean run = sprint && !glance && yawErr < (dist > 1.5 ? 30f : 20f) && p.getFoodData().getFoodLevel() > 6;
		mc.options.keySprint.setDown(run);
		if (!run && p.isSprinting()) p.setSprinting(false);
		Vec3 v = p.getDeltaMovement();
		boolean hop = jump && run && p.isSprinting() && p.onGround() && !p.isInWater() && yawErr < 12f && v.x * v.x + v.z * v.z > 0.04;
		if (hop) Stats.jumps++;
		// Swim: keep the head above water (and climb out of a water hole onto the next cell).
		mc.options.keyJump.setDown((dyFeet > 0.5 && p.onGround()) || swimUp(p, dyFeet) || hop);
		return false;
	}

	/**
	 * In water, only swim up when it is needed: head under water, or pushing against the edge to climb out.
	 * Bobbing up and down all the way would make it jump out high and land hard (that tramples farmland).
	 */
	private static boolean swimUp(LocalPlayer p, double dyFeet) {
		if (!p.isInWater()) return false;
		return p.isUnderWater() || (p.horizontalCollision && dyFeet > -0.5);
	}

	public static void release(Minecraft mc) {
		mc.options.keyUp.setDown(false);
		mc.options.keyJump.setDown(false);
		mc.options.keyAttack.setDown(false);
		mc.options.keySprint.setDown(false);
		if (mc.player != null) mc.player.setSprinting(false);
	}
}
