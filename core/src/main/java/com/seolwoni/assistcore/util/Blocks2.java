package com.seolwoni.assistcore.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Block checks shared by all Assist mods. */
public final class Blocks2 {
	private Blocks2() {
	}

	public static Level level() {
		return Minecraft.getInstance().level;
	}

	public static BlockState state(BlockPos p) {
		return level().getBlockState(p);
	}

	public static String id(BlockPos p) {
		return BuiltInRegistries.BLOCK.getKey(state(p).getBlock()).toString();
	}

	public static boolean isFluid(BlockPos p) {
		return !level().getFluidState(p).isEmpty();
	}

	public static boolean isLava(BlockPos p) {
		return level().getFluidState(p).is(FluidTags.LAVA);
	}

	public static boolean isWater(BlockPos p) {
		return level().getFluidState(p).is(FluidTags.WATER);
	}

	/** Nothing to dig: air, or a non-fluid replaceable thing such as grass or snow layers. */
	public static boolean isOpen(BlockPos p) {
		BlockState s = state(p);
		if (s.isAir()) return true;
		return s.canBeReplaced() && s.getFluidState().isEmpty() && !s.blocksMotion();
	}

	/** Something we can stand on / place against. */
	public static boolean isSolid(BlockPos p) {
		BlockState s = state(p);
		return !s.isAir() && s.getFluidState().isEmpty() && s.isCollisionShapeFullBlock(level(), p);
	}

	public static boolean isTorch(BlockPos p) {
		BlockState s = state(p);
		return s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH);
	}

	public static boolean unbreakable(BlockPos p) {
		return state(p).getDestroySpeed(level(), p) < 0;
	}

	/** Blocks that slide down when the block under them is removed. */
	public static boolean isFalling(BlockPos p) {
		BlockState s = state(p);
		return s.getBlock() instanceof net.minecraft.world.level.block.FallingBlock;
	}

	/** Nothing to bump into or get hurt by at {@code p} (crops and grass are fine, fire, berry bushes and cobwebs are not). */
	public static boolean passable(BlockPos p) {
		BlockState s = state(p);
		if (!s.getFluidState().isEmpty()) return false;
		if (s.is(net.minecraft.tags.BlockTags.FIRE) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.COBWEB) || s.is(Blocks.POWDER_SNOW)) return false;
		var shape = s.getCollisionShape(level(), p);
		// A carpet (moss carpet in lush caves) is walked over, not bumped into.
		return shape.isEmpty() || shape.max(net.minecraft.core.Direction.Axis.Y) <= 0.0625 + 1e-6;
	}

	/**
	 * Cover that is no wall: moss and wool carpets, cave vines (glow berries), hanging roots, glow lichen, spore
	 * blossoms, small dripleaf, petals, rails and the like: not a full block, nothing or almost nothing to stand
	 * against. A cave opening hung with vines is still an opening (mobs walk through), so walls go there.
	 */
	public static boolean isSoftCover(BlockPos p) {
		BlockState s = state(p);
		if (s.isAir() || !s.getFluidState().isEmpty()) return false;
		if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH)) return false;
		if (s.isCollisionShapeFullBlock(level(), p)) return false;
		var shape = s.getCollisionShape(level(), p);
		return shape.isEmpty() || shape.max(net.minecraft.core.Direction.Axis.Y) <= 0.1875 + 1e-6;
	}

	/** A cell a player can simply walk across: room for feet and head, and something safe to stand on. */
	public static boolean walkable(BlockPos p) {
		if (!passable(p) || !passable(p.above())) return false;
		BlockPos f = p.below();
		BlockState s = state(f);
		if (!s.getFluidState().isEmpty() || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE)) return false;
		return !s.getCollisionShape(level(), f).isEmpty();
	}

	/**
	 * True if the player can walk from {@code from} straight to the middle of {@code to} on level ground: every
	 * cell the body sweeps on the way is {@link #walkable}. Then there is no need to plan a route.
	 */
	public static boolean lineWalkable(net.minecraft.world.phys.Vec3 from, BlockPos to) {
		int y = to.getY();
		if (Math.abs(from.y - y) > 0.3) return false;
		double dx = to.getX() + 0.5 - from.x, dz = to.getZ() + 0.5 - from.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 0.01) return true;
		double px = -dz / len * 0.32, pz = dx / len * 0.32;
		int steps = (int) Math.ceil(len / 0.2);
		BlockPos start = BlockPos.containing(from.x, y, from.z);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int i = 0; i <= steps; i++) {
			double t = (double) i / steps;
			double x = from.x + dx * t, z = from.z + dz * t;
			for (int side = -1; side <= 1; side++) {
				m.set(net.minecraft.util.Mth.floor(x + px * side), y, net.minecraft.util.Mth.floor(z + pz * side));
				if (m.equals(start)) continue;
				if (!walkable(m)) return false;
			}
		}
		return true;
	}
}
