package com.seolwoni.assistpath.defense;

import java.util.ArrayList;
import java.util.List;

import com.seolwoni.assistcore.engine.Action;
import com.seolwoni.assistcore.util.Blocks2;
import com.seolwoni.assistcore.util.Body;
import com.seolwoni.assistcore.util.Human;
import com.seolwoni.assistcore.util.InvBase;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Before giving up on being chased (fighting off): walls on the four sides at feet and head height and a roof,
 * with whatever full blocks we carry (dirt, stone, logs, planks, pumpkins...). Standing still out in the open at
 * night with the mob next to us was the old ending. Cells a mob stands in are left open; with no blocks at all we
 * simply stop as before. A few seconds at most.
 */
final class WallIn implements Action {
	private final List<BlockPos> cells = new ArrayList<>();
	private int index, stage, delay, wait, attempts, ticks;
	private BlockPos support;
	private Direction face;
	private Vec3 hit;
	int placed;

	WallIn(LocalPlayer p) {
		BlockPos f = p.blockPosition();
		// Feet level first (what walks up to us), then head level, then the roof.
		for (Direction d : Direction.Plane.HORIZONTAL) cells.add(f.relative(d));
		for (Direction d : Direction.Plane.HORIZONTAL) cells.add(f.above().relative(d));
		cells.add(f.above(2));
	}

	static boolean isWallBlock(ItemStack s) {
		if (s.isEmpty() || !(s.getItem() instanceof BlockItem bi)) return false;
		Block b = bi.getBlock();
		if (b instanceof FallingBlock || b instanceof EntityBlock) return false;
		var st = b.defaultBlockState();
		return st.isSolidRender() && !st.hasBlockEntity();
	}

	@Override
	public int tick(Minecraft mc, LocalPlayer p) {
		if (++ticks > 20 * 12) return DONE; // never stand about for long
		mc.options.keyUp.setDown(false);
		mc.options.keyDown.setDown(false);
		mc.options.keyLeft.setDown(false);
		mc.options.keyRight.setDown(false);
		mc.options.keySprint.setDown(false);
		while (index < cells.size() && !needed(mc, cells.get(index))) next();
		if (index >= cells.size()) return DONE;
		BlockPos pos = cells.get(index);
		switch (stage) {
			case 0 -> {
				if (!findSupport(p, pos)) {
					next();
					return RUN;
				}
				int slot = InvBase.bringToHotbar(mc, p, WallIn::isWallBlock, s -> false);
				if (slot == -1) return DONE; // nothing to build with
				InvBase.select(p, slot);
				delay = Human.delay(1, 3);
				Body.newTurn();
				stage = 1;
			}
			case 1 -> {
				if (delay-- > 0) return RUN;
				stage = 2;
			}
			case 2 -> {
				if (!Body.lookAt(p, hit, 3f)) return RUN;
				mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, support, false));
				p.swing(InteractionHand.MAIN_HAND);
				wait = Human.rand(2, 3);
				stage = 3;
			}
			default -> {
				if (wait-- > 0) return RUN;
				if (!Blocks2.isOpen(pos)) {
					placed++;
					next();
				} else if (++attempts >= 2) {
					next();
				} else {
					stage = 0;
				}
			}
		}
		return RUN;
	}

	private void next() {
		index++;
		stage = 0;
		attempts = 0;
	}

	/** Open (air, grass, flowers...) and nobody standing in it. */
	private static boolean needed(Minecraft mc, BlockPos pos) {
		if (!Blocks2.isOpen(pos) || Blocks2.isFluid(pos)) return false;
		return mc.level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos),
				e -> e instanceof net.minecraft.world.entity.LivingEntity).isEmpty();
	}

	private boolean findSupport(LocalPlayer p, BlockPos pos) {
		Vec3 eye = p.getEyePosition();
		double best = Double.MAX_VALUE;
		support = null;
		for (Direction dir : Direction.values()) {
			BlockPos q = pos.relative(dir);
			if (!Blocks2.isSolid(q)) continue;
			Direction f = dir.getOpposite();
			Vec3 h = new Vec3(q.getX() + 0.5 + f.getStepX() * 0.5, q.getY() + 0.5 + f.getStepY() * 0.5, q.getZ() + 0.5 + f.getStepZ() * 0.5);
			double dist = eye.distanceTo(h);
			if (dist > 4.4 || dist >= best) continue;
			best = dist;
			support = q;
			face = f;
			hit = h;
		}
		return support != null;
	}
}
