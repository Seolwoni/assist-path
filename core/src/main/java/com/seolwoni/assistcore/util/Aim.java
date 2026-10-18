package com.seolwoni.assistcore.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Where to look when mining or using a block. */
public final class Aim {
	private Aim() {
	}

	/** The face of {@code pos} that points at the eye (the one a player would click). */
	public static Direction faceToward(Vec3 eye, BlockPos pos) {
		double dx = eye.x - (pos.getX() + 0.5), dy = eye.y - (pos.getY() + 0.5), dz = eye.z - (pos.getZ() + 0.5);
		double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
		if (ay >= ax && ay >= az) return dy > 0 ? Direction.UP : Direction.DOWN;
		if (ax >= az) return dx > 0 ? Direction.EAST : Direction.WEST;
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** A point on a block face, a little off-centre ({@code spread} blocks) so it never looks robotic. */
	public static Vec3 facePoint(BlockPos pos, Direction face, double spread) {
		double x = pos.getX() + 0.5 + face.getStepX() * 0.5;
		double y = pos.getY() + 0.5 + face.getStepY() * 0.5;
		double z = pos.getZ() + 0.5 + face.getStepZ() * 0.5;
		if (face.getAxis() != Direction.Axis.X) x += Human.jitter(spread);
		if (face.getAxis() != Direction.Axis.Y) y += Human.jitter(spread);
		if (face.getAxis() != Direction.Axis.Z) z += Human.jitter(spread);
		return new Vec3(x, y, z);
	}
}
