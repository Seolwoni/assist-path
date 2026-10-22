package com.seolwoni.assistpath.defense;

import java.util.List;

import com.seolwoni.assistcore.engine.Action;
import com.seolwoni.assistcore.util.Body;
import com.seolwoni.assistcore.util.Human;
import com.seolwoni.assistcore.util.InvBase;
import com.seolwoni.assistpath.PathOptions;
import com.seolwoni.assistpath.goals.GoalBlock;
import com.seolwoni.assistpath.walk.PathWalker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * Defense: keeps the player alive while a mod works. Every mod calls {@link #guard} each tick before its own work;
 * while it returns true, Defense has the controls and the mod waits (its queued work carries on afterwards).
 *
 * <ul>
 * <li>A creeper close by: get away from it, whatever else is going on.</li>
 * <li>An arrow (or other projectile) about to hit: a quick step to the side.</li>
 * <li>Hostile mobs close: back off and walk away, or (only when fighting is switched on, with a weapon, not
 * outnumbered and not low on health) turn and fight.</li>
 * </ul>
 * Only mobs a player could see are counted. Fighting is off by default.
 */
public final class Defense {
	private Defense() {
	}

	public static final class Config {
		/** Off: the mods behave as before (stop when a mob comes, if they are set to). */
		public boolean enabled = true;
		/** Fight back (melee) when it is safe to; off by default. */
		public boolean attack = false;
		/** At or below this health, never fight: get away. */
		public float fleeHealth = 10f;
		/** Mobs closer than this make us move away (or fight). */
		public double keepAway = 7;
		/**
		 * Where we may go while getting away or fighting (null: anywhere). A mod sets this when leaving its own
		 * ground is the bigger danger: the miner stays inside the tunnels it dug instead of running out into a
		 * cave (where it then cannot find the way back).
		 */
		public java.util.function.Predicate<BlockPos> region = null;
	}

	static boolean inRegion(BlockPos feet) {
		return config.region == null || config.region.test(feet);
	}

	public static final Config config = new Config();

	/** What happened this session (for the HUD, the tests and the usage log). */
	public static final class Stats {
		public static int flees, dodges, creeperEscapes, fights, kills, hitsTaken;
		/** Ticks a mob was close but could not get to us (behind a wall), so it was left alone; fights given up. */
		public static int unreachableTicks, fightsGivenUp;
		/** The last few times Defense took over: when, what, which mob, how far, whether it could see us. */
		public static final java.util.ArrayDeque<String> events = new java.util.ArrayDeque<>();
		public static float lowestHealth = 20f;

		public static String summary() {
			return "defense: flees=" + flees + " dodges=" + dodges + " creeperEscapes=" + creeperEscapes + " fights=" + fights + " kills=" + kills
					+ " hitsTaken=" + hitsTaken + " lowestHealth=" + lowestHealth + " unreachableTicks=" + unreachableTicks + " fightsGivenUp=" + fightsGivenUp;
		}

		public static void reset() {
			flees = dodges = creeperEscapes = fights = kills = hitsTaken = 0;
			unreachableTicks = fightsGivenUp = 0;
			events.clear();
			lowestHealth = 20f;
		}
	}

	private enum Mode { NONE, FLEE, CREEPER, DODGE, FIGHT }

	private static Mode mode = Mode.NONE;
	private static int modeTicks, calm, dodgeTicks, replan, lastHurt, chased, quiet;
	private static String giveUp;
	private static boolean dodgeLeft, dodgeHold;
	private static Vec3 dodgeDir;
	private static PathWalker walker;
	private static BlockPos fleeTo;
	private static Entity target;
	/** Swings at the current target and ticks spent on it: a fight with no swing for 3 s is given up. */
	private static int swings, targetTicks;
	private static String status = "";

	/** What Defense is doing right now (empty when nothing). */
	public static String status() {
		return mode == Mode.NONE ? "" : status;
	}

	public static boolean active() {
		return mode != Mode.NONE;
	}

	/**
	 * Non-null when running away does not help (a mob has kept following for 40 seconds, fighting is off): the mod
	 * should stop and tell the player. Cleared by {@link #reset}.
	 */
	public static String giveUp() {
		return giveUp;
	}

	/**
	 * Call every tick before the mod's own work. Returns true while Defense handles a threat: the mod should not
	 * run its own actions this tick.
	 */
	public static boolean guard(Minecraft mc, LocalPlayer p) {
		if (!config.enabled || p == null || mc.level == null) return false;
		if (mode == Mode.NONE && ++quiet > 20 * 30) chased = 0; // half a minute without trouble: start over
		if (mode != Mode.NONE) quiet = 0;
		if (p.hurtTime == 9 && lastHurt != 9) Stats.hitsTaken++;
		lastHurt = p.hurtTime;
		Stats.lowestHealth = Math.min(Stats.lowestHealth, p.getHealth());

		if (mode == Mode.FIGHT && target != null && !target.isAlive()) {
			Stats.kills++;
			log("몹을 처치했어요");
			target = null;
		}
		Creeper creeper = Threats.nearCreeper(p, mode == Mode.CREEPER ? 9 : 6);
		Vec3 arrow = mode == Mode.DODGE ? null : Threats.incoming(p, 14, 1.3);
		List<Entity> near = Threats.hostiles(p, config.keepAway);

		// Being chased: count every tick while running away (the calm pauses in between too).
		if (mode == Mode.FLEE || mode == Mode.CREEPER) {
			if (++chased > 20 * 40 && giveUp == null) {
				giveUp = "몹이 계속 따라와요";
				log("몹이 40초 넘게 따라와서 작업을 멈췄어요");
			}
		} else if (mode != Mode.NONE) {
			chased = 0;
		}

		Mode want;
		if (creeper != null) want = Mode.CREEPER;
		else if (arrow != null) want = Mode.DODGE;
		else if (mode == Mode.DODGE && dodgeTicks > 0) want = Mode.DODGE;
		else if (!near.isEmpty()) want = canFight(p, near) ? Mode.FIGHT : Mode.FLEE;
		else if (mode == Mode.FLEE && !Threats.hostiles(p, 11).isEmpty()) want = mode; // keep going until clear
		else want = Mode.NONE;

		if (want == Mode.NONE) {
			if (mode == Mode.NONE) return false;
			// Calm down for a moment before handing back (no twitching back and forth at the edge of the range).
			if (++calm < Human.rand(10, 20)) {
				Body.release(mc);
				return true;
			}
			end(mc);
			return false;
		}
		calm = 0;
		if (want != mode) {
			begin(mc, p, want, arrow);
			note(p, want, creeper != null ? List.of(creeper) : near);
		}
		modeTicks++;
		switch (mode) {
			case CREEPER -> flee(mc, p, List.of(creeper), true);
			case DODGE -> dodge(mc, p);
			case FIGHT -> fight(mc, p, near);
			case FLEE -> flee(mc, p, Threats.hostiles(p, 12), false);
			default -> {
			}
		}
		return true;
	}

	private static boolean canFight(LocalPlayer p, List<Entity> near) {
		if (!config.attack || p.getHealth() <= config.fleeHealth) return false;
		if (near.size() > 2) return false;
		for (Entity e : near) if (e instanceof Creeper) return false;
		// Never walk up to these: a wither skeleton withers, a ghast floats out of reach. Get away from them.
		for (Entity e : near) if (NO_FIGHT.contains(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath())) return false;
		// A skeleton a few blocks off keeps shooting while we walk up to it (night mine test: 8 arrows taken in a
		// cave): only fight one that is already close; otherwise get behind cover.
		for (Entity e : near) if (ranged(e) && e.distanceTo(p) > 3.5) return false;
		return weaponSlot(p) >= 0;
	}

	private static void begin(Minecraft mc, LocalPlayer p, Mode m, Vec3 arrow) {
		Body.release(mc);
		if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
		Body.newTurn();
		Body.resetWalk();
		walker = null;
		fleeTo = null;
		replan = 0;
		modeTicks = 0;
		switch (m) {
			case CREEPER -> {
				Stats.creeperEscapes++;
				status = "크리퍼를 피하는 중";
				log("크리퍼가 가까워서 피했어요");
			}
			case DODGE -> {
				Stats.dodges++;
				status = "화살을 피하는 중";
				dodgeTicks = Human.rand(6, 9);
				// Step across the arrow's line: sideways to its flight, on whichever side is free.
				double yaw = Math.toRadians(p.getYRot());
				Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
				Vec3 across = arrow == null ? left : new Vec3(-arrow.z, 0, arrow.x).normalize();
				boolean freeA = standable(p, BlockPos.containing(p.position().add(across.scale(1.2)))) && safeStep(p, across),
						freeB = standable(p, BlockPos.containing(p.position().subtract(across.scale(1.2)))) && safeStep(p, across.scale(-1));
				// Neither side has ground (a bridge over a cave, a ledge): do not step off it to dodge. Crouch and take
				// the arrow instead, the way a player on a narrow bridge would.
				dodgeHold = !freeA && !freeB;
				Vec3 go = freeA ? across : across.scale(-1);
				dodgeDir = go;
				dodgeLeft = go.dot(left) > 0;
			}
			case FIGHT -> {
				Stats.fights++;
				status = "몹과 싸우는 중";
				log("몹과 싸웠어요");
			}
			case FLEE -> {
				Stats.flees++;
				status = "몹을 피해 물러나는 중";
				log("몹을 피해 물러났어요");
			}
			default -> {
			}
		}
		mode = m;
	}

	/** For the tests and the usage log: what made us act. */
	private static void note(LocalPlayer p, Mode m, List<Entity> why) {
		StringBuilder sb = new StringBuilder(m.name().toLowerCase());
		sb.append(" at ").append(p.blockPosition().toShortString());
		for (Entity e : why) {
			String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
			sb.append(" | ").append(id).append(' ').append(String.format("%.1f", e.distanceTo(p))).append(p.hasLineOfSight(e) ? " seen" : " unseen");
			if (sb.length() > 200) break;
		}
		Stats.events.addLast(sb.toString());
		while (Stats.events.size() > 40) Stats.events.removeFirst();
	}

	private static void end(Minecraft mc) {
		Body.release(mc);
		if (dodgeHold) mc.options.keyShift.setDown(false);
		dodgeHold = false;
		Body.resetWalk();
		Body.newTurn();
		mode = Mode.NONE;
		walker = null;
		fleeTo = null;
		target = null;
		calm = 0;
	}

	public static void reset(Minecraft mc) {
		if (mode != Mode.NONE && mc != null) end(mc);
		mode = Mode.NONE;
		giveUp = null;
		chased = 0;
	}

	private static void log(String what) {
		try {
			com.seolwoni.assistcore.log.UsageLog.event("defense", what);
		} catch (RuntimeException ignored) {
		}
	}

	// ------------------------------------------------------------------ flee

	/** Walk (run) to a free spot well away from the threats; replan now and then as they move. */
	private static void flee(Minecraft mc, LocalPlayer p, List<Entity> from, boolean creeper) {
		if (from.isEmpty()) {
			Body.release(mc);
			return;
		}
		if (fleeTo == null || walker == null || --replan <= 0) {
			fleeTo = fleeSpot(p, from, creeper ? 9 : 10);
			replan = Human.rand(30, 45);
			walker = fleeTo == null ? null : new PathWalker(new GoalBlock(fleeTo), PathOptions.WALK, 1);
		}
		if (walker != null) {
			int r = walker.tick(mc, p);
			if (r == Action.RUN) return;
			walker = null;
			fleeTo = null;
			replan = 0;
		}
		// No way out found: back away from the nearest one, facing it (like a player would), but not off an edge.
		Entity n = from.get(0);
		Body.lookAt(p, n.getEyePosition(), 6f);
		double yaw = Math.toRadians(p.getYRot());
		Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
		mc.options.keyUp.setDown(false);
		mc.options.keyDown.setDown(safeStep(p, back));
	}

	/** A standable cell 8 to 14 blocks away that keeps the most distance from every threat. */
	private static BlockPos fleeSpot(LocalPlayer p, List<Entity> from, int min) {
		BlockPos me = p.blockPosition();
		Vec3 away = Vec3.ZERO;
		for (Entity e : from) {
			Vec3 d = p.position().subtract(e.position());
			double len = Math.max(0.5, d.length());
			away = away.add(d.scale(1.0 / (len * len)));
		}
		BlockPos best = null;
		double bestScore = -1e9;
		for (int a = 0; a < 24; a++) {
			double ang = a * Math.PI * 2 / 24;
			for (int r = min; r <= min + 5; r += 2) {
				int x = me.getX() + (int) Math.round(Math.cos(ang) * r), z = me.getZ() + (int) Math.round(Math.sin(ang) * r);
				for (int dy = 3; dy >= -3; dy--) {
					BlockPos c = new BlockPos(x, me.getY() + dy, z);
					if (!standable(p, c)) continue;
					if (!inRegion(c)) break;
					double nearest = Double.MAX_VALUE;
					for (Entity e : from) nearest = Math.min(nearest, Math.sqrt(e.blockPosition().distSqr(c)));
					Vec3 dir = new Vec3(c.getX() - me.getX(), 0, c.getZ() - me.getZ()).normalize();
					double score = nearest * 2 + (away.lengthSqr() > 0 ? dir.dot(away.normalize()) * 6 : 0) - Math.abs(dy);
					// Out of sight of archers is worth a lot (they cannot hit what they cannot see).
					for (Entity e : from) if (ranged(e) && seesSpot(p, e, c)) score -= 12;
					if (score > bestScore) {
						bestScore = score;
						best = c;
					}
					break;
				}
			}
		}
		return best;
	}

	private static final java.util.Set<String> NO_FIGHT = java.util.Set.of("wither_skeleton", "ghast");
	private static final java.util.Set<String> RANGED = java.util.Set.of("skeleton", "stray", "bogged", "pillager", "witch", "blaze", "ghast");

	/** Ground under the next step in this direction (at most one block down): no walking off ledges or into holes. */
	static boolean safeStep(LocalPlayer p, Vec3 dir) {
		Vec3 at = p.position().add(dir.normalize().scale(0.8));
		BlockPos feet = BlockPos.containing(at.x, p.getY() + 0.1, at.z);
		if (!inRegion(feet) && !inRegion(feet.below()) && inRegion(p.blockPosition())) return false;
		var level = p.level();
		for (int down = 0; down <= 1; down++) {
			BlockPos below = feet.below(down + 1);
			var st = level.getBlockState(below);
			if (!st.getFluidState().isEmpty() && st.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return false;
			if (st.isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) return true;
		}
		return false;
	}

	static boolean ranged(Entity e) {
		String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
		return RANGED.contains(id);
	}

	private static boolean seesSpot(LocalPlayer p, Entity e, BlockPos c) {
		Vec3 eye = e.getEyePosition();
		Vec3 spot = new Vec3(c.getX() + 0.5, c.getY() + 1.5, c.getZ() + 0.5);
		var hit = p.level().clip(new net.minecraft.world.level.ClipContext(eye, spot, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, e));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
	}

	private static boolean standable(LocalPlayer p, BlockPos feet) {
		if (!inRegion(feet) && inRegion(p.blockPosition())) return false;
		var level = p.level();
		var f = level.getBlockState(feet);
		var h = level.getBlockState(feet.above());
		var b = level.getBlockState(feet.below());
		if (f.blocksMotion() || h.blocksMotion() || !f.getFluidState().isEmpty() || !h.getFluidState().isEmpty()) return false;
		if (!b.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) return false;
		return b.getFluidState().isEmpty() && !b.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
				&& !b.is(net.minecraft.world.level.block.Blocks.CACTUS) && !f.is(net.minecraft.world.level.block.Blocks.FIRE);
	}

	// ------------------------------------------------------------------ dodge

	private static void dodge(Minecraft mc, LocalPlayer p) {
		if (dodgeTicks-- <= 0) {
			Body.release(mc);
			if (dodgeHold) mc.options.keyShift.setDown(false);
			dodgeHold = false;
			mode = Mode.NONE;
			return;
		}
		mc.options.keyUp.setDown(false);
		mc.options.keySprint.setDown(false);
		// Every tick: still ground on that side (and still inside where we may go)? If not, stop and crouch.
		if (dodgeHold || dodgeDir == null || !safeStep(p, dodgeDir)) {
			dodgeHold = true;
			mc.options.keyLeft.setDown(false);
			mc.options.keyRight.setDown(false);
			mc.options.keyShift.setDown(true);
			return;
		}
		mc.options.keyLeft.setDown(dodgeLeft);
		mc.options.keyRight.setDown(!dodgeLeft);
	}

	// ------------------------------------------------------------------ fight

	private static int weaponSlot(LocalPlayer p) {
		int sword = -1, axe = -1;
		for (int i = 0; i < 9; i++) {
			var s = p.getInventory().getItem(i);
			if (s.is(ItemTags.SWORDS) && sword < 0) sword = i;
			if (s.is(ItemTags.AXES) && axe < 0) axe = i;
		}
		return sword >= 0 ? sword : axe;
	}

	private static void fight(Minecraft mc, LocalPlayer p, List<Entity> near) {
		if (target == null || !target.isAlive() || target.distanceTo(p) > config.keepAway + 2) {
			if (target != null && !target.isAlive()) {
				Stats.kills++;
				log("몹을 처치했어요");
			}
			target = null;
			double best = Double.MAX_VALUE;
			for (Entity e : near) {
				if (e instanceof Enemy && e.distanceTo(p) < best) {
					best = e.distanceTo(p);
					target = e;
				}
			}
			if (target == null) return;
			swings = 0;
			targetTicks = 0;
		}
		if (++targetTicks > 60 && swings == 0) {
			// Three seconds without being able to swing at it (out of reach behind something, or we cannot step
			// closer without going over an edge): leave it be, unless it comes right up to us.
			Stats.fightsGivenUp++;
			Threats.ignore(p, target, 200);
			log("닿지 않는 몹이라 싸우기를 그만뒀어요");
			target = null;
			mc.options.keyUp.setDown(false);
			mc.options.keyDown.setDown(false);
			return;
		}
		int slot = weaponSlot(p);
		if (slot >= 0) InvBase.select(p, slot);
		Vec3 aim = target.position().add(0, target.getBbHeight() * 0.6, 0);
		boolean onTarget = Body.lookAt(p, aim, 8f);
		double d = target.distanceTo(p);
		// Keep at arm's length: step in when too far, back off when it gets too close, but never off an edge
		// (a fight must not end with a fall into a cave or out of the tunnel).
		double yaw = Math.toRadians(p.getYRot());
		Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		mc.options.keyUp.setDown(d > 2.8 && safeStep(p, fwd));
		mc.options.keyDown.setDown(d < 1.6 && safeStep(p, fwd.scale(-1)));
		if (onTarget && d <= 3.0 && p.getAttackStrengthScale(0.5f) >= 0.95f && mc.gameMode != null) {
			mc.gameMode.attack(p, target);
			p.swing(InteractionHand.MAIN_HAND);
			swings++;
		}
	}
}
