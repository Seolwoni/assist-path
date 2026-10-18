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

package com.seolwoni.assistpath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The path-finding settings Assist Path uses, with Baritone's defaults (from baritone.api.Settings).
 * Only the ones the path calculation reads are here.
 */
public final class PathSettings {
	/** A setting value (named like Baritone's so the ported code reads the same). */
	public static final class Setting<T> {
		public T value;

		Setting(T value) {
			this.value = value;
		}
	}

	private static final PathSettings INSTANCE = new PathSettings();

	public static PathSettings get() {
		return INSTANCE;
	}

	public final Setting<Boolean> allowBreak = new Setting<>(true);
	public final Setting<List<Block>> allowBreakAnyway = new Setting<>(new ArrayList<>());
	public final Setting<Boolean> allowDiagonalAscend = new Setting<>(false);
	public final Setting<Boolean> allowDiagonalDescend = new Setting<>(false);
	public final Setting<Boolean> allowDownward = new Setting<>(true);
	public final Setting<Boolean> allowJumpAtBuildLimit = new Setting<>(false);
	public final Setting<Boolean> allowParkour = new Setting<>(false);
	public final Setting<Boolean> allowParkourAscend = new Setting<>(true);
	public final Setting<Boolean> allowParkourPlace = new Setting<>(false);
	public final Setting<Boolean> allowPlace = new Setting<>(true);
	public final Setting<Boolean> allowPlaceInFluidsFlow = new Setting<>(true);
	public final Setting<Boolean> allowPlaceInFluidsSource = new Setting<>(true);
	public final Setting<Boolean> allowSprint = new Setting<>(true);
	public final Setting<Boolean> allowVines = new Setting<>(false);
	public final Setting<Boolean> allowWalkOnBottomSlab = new Setting<>(true);
	public final Setting<Boolean> allowWalkOnMagmaBlocks = new Setting<>(false);
	public final Setting<Boolean> allowWaterBucketFall = new Setting<>(false);
	public final Setting<Boolean> assumeWalkOnLava = new Setting<>(false);
	public final Setting<Boolean> assumeWalkOnWater = new Setting<>(false);
	public final Setting<Boolean> autoTool = new Setting<>(true);
	public final Setting<Double> avoidBreakingMultiplier = new Setting<>(.1);
	public final Setting<Boolean> avoidUpdatingFallingBlocks = new Setting<>(true);
	public final Setting<Double> backtrackCostFavoringCoefficient = new Setting<>(0.5);
	public final Setting<Double> blockBreakAdditionalPenalty = new Setting<>(2D);
	public final Setting<Double> blockPlacementPenalty = new Setting<>(20D);
	public final Setting<List<Block>> blocksToAvoid = new Setting<>(new ArrayList<>(List.of(Blocks.TRIPWIRE)));
	public final Setting<List<Block>> blocksToAvoidBreaking = new Setting<>(new ArrayList<>(Arrays.asList(
			Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL,
			Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.ENDER_CHEST)));
	public final Setting<List<Block>> blocksToDisallowBreaking = new Setting<>(new ArrayList<>());
	public final Setting<List<Item>> acceptableThrowawayItems = new Setting<>(new ArrayList<>(Arrays.asList(
			Blocks.DIRT.asItem(), Blocks.COBBLESTONE.asItem(), Blocks.NETHERRACK.asItem(), Blocks.STONE.asItem(),
			Blocks.COBBLED_DEEPSLATE.asItem())));
	public final Setting<Boolean> considerPotionEffects = new Setting<>(true);
	public final Setting<Boolean> itemSaver = new Setting<>(false);
	public final Setting<Integer> itemSaverThreshold = new Setting<>(10);
	public final Setting<Double> jumpPenalty = new Setting<>(2D);
	public final Setting<Integer> maxFallHeightBucket = new Setting<>(20);
	public final Setting<Integer> maxFallHeightNoWater = new Setting<>(3);
	public final Setting<Boolean> minimumImprovementRepropagation = new Setting<>(true);
	public final Setting<Integer> pathingMapDefaultSize = new Setting<>(1024);
	public final Setting<Float> pathingMapLoadFactor = new Setting<>(0.75f);
	public final Setting<Integer> pathingMaxChunkBorderFetch = new Setting<>(50);
	public final Setting<Boolean> slowPath = new Setting<>(false);
	public final Setting<Long> slowPathTimeDelayMS = new Setting<>(100L);
	public final Setting<Long> slowPathTimeoutMS = new Setting<>(40000L);
	public final Setting<Boolean> strictLiquidCheck = new Setting<>(false);
	public final Setting<Boolean> useSwordToMine = new Setting<>(true);
	public final Setting<Double> walkOnWaterOnePenalty = new Setting<>(3D);

	private PathSettings() {
	}
}
