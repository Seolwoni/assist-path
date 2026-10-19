/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seolwoni.assistpath.movement;

import com.seolwoni.assistpath.PathSettings;
import com.seolwoni.assistpath.util.ActionCosts;
import com.seolwoni.assistpath.precompute.PrecomputedData;
import com.seolwoni.assistpath.util.BlockStateInterface;
import com.seolwoni.assistpath.util.ToolSet;
import com.seolwoni.assistpath.util.BetterWorldBorder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static com.seolwoni.assistpath.util.ActionCosts.COST_INF;

/**
 * @author Brady
 * @since 8/7/2018
 */
public class CalculationContext {

    private static final ItemStack STACK_BUCKET_WATER = new ItemStack(Items.WATER_BUCKET);

    public final boolean safeForThreadedUse;
    public final LocalPlayer player;
    public final Level world;
    public final BlockStateInterface bsi;
    public final ToolSet toolSet;
    public final boolean hasWaterBucket;
    public final boolean hasThrowaway;
    public final boolean canSprint;
    protected final double placeBlockCost; // protected because you should call the function instead
    public final boolean allowBreak;
    /** Assist Path: never step into water (crossing a field's water channel means jumping out onto farmland). */
    public final boolean avoidWater;
    public final List<Block> allowBreakAnyway;
    public final boolean allowParkour;
    public final boolean allowParkourPlace;
    public final boolean allowJumpAtBuildLimit;
    public final boolean allowParkourAscend;
    public final boolean assumeWalkOnWater;
    public boolean allowFallIntoLava;
    public final int frostWalker;
    public final boolean allowDiagonalDescend;
    public final boolean allowDiagonalAscend;
    public final boolean allowDownward;
    public int minFallHeight;
    public int maxFallHeightNoWater;
    public final int maxFallHeightBucket;
    public final double waterWalkSpeed;
    public final double breakBlockAdditionalCost;
    public double backtrackCostFavoringCoefficient;
    public double jumpPenalty;
    public final double walkOnWaterOnePenalty;
    public final boolean allowWalkOnMagmaBlocks;
    public final BetterWorldBorder worldBorder;

    public final PrecomputedData precomputedData;

    public CalculationContext(LocalPlayer player, Level world) {
        this(player, world, false);
    }

    public CalculationContext(LocalPlayer player, Level world, boolean forUseOnAnotherThread) {
        this(player, world, forUseOnAnotherThread, com.seolwoni.assistpath.PathOptions.FULL);
    }

    /** Assist Path: the route options decide breaking, placing and parkour (on top of the settings). */
    public CalculationContext(LocalPlayer player, Level world, boolean forUseOnAnotherThread, com.seolwoni.assistpath.PathOptions options) {
        this.precomputedData = new PrecomputedData();
        this.safeForThreadedUse = forUseOnAnotherThread;
        this.player = player;
        this.world = world;
        this.bsi = new BlockStateInterface(world, forUseOnAnotherThread);
        this.toolSet = new ToolSet(player);
        this.hasThrowaway = options.allowPlace() && PathSettings.get().allowPlace.value && hasThrowawayBlocks(player);
        this.hasWaterBucket = PathSettings.get().allowWaterBucketFall.value && Inventory.isHotbarSlot(player.getInventory().findSlotMatchingItem(STACK_BUCKET_WATER)) && world.dimension() != Level.NETHER;
        this.canSprint = PathSettings.get().allowSprint.value && player.getFoodData().getFoodLevel() > 6;
        this.placeBlockCost = PathSettings.get().blockPlacementPenalty.value;
        this.allowBreak = options.allowBreak() && PathSettings.get().allowBreak.value;
        this.avoidWater = options.avoidWater();
        this.allowBreakAnyway = new ArrayList<>(PathSettings.get().allowBreakAnyway.value);
        this.allowParkour = options.allowParkour() || PathSettings.get().allowParkour.value;
        this.allowParkourPlace = options.allowPlace() && PathSettings.get().allowParkourPlace.value;
        this.allowJumpAtBuildLimit = PathSettings.get().allowJumpAtBuildLimit.value;
        this.allowParkourAscend = PathSettings.get().allowParkourAscend.value;
        this.assumeWalkOnWater = PathSettings.get().assumeWalkOnWater.value;
        this.allowFallIntoLava = false; // Super secret internal setting for ElytraBehavior
        // todo: technically there can now be datapack enchants that replace blocks with any other at any range
        int frostWalkerLevel = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemEnchantments itemEnchantments = player
                .getItemBySlot(slot)
                .getEnchantments();
            for (Holder<Enchantment> enchant : itemEnchantments.keySet()) {
                if (enchant.is(Enchantments.FROST_WALKER)) {
                    frostWalkerLevel = itemEnchantments.getLevel(enchant);
                }
            }
        }
        this.frostWalker = frostWalkerLevel;
        this.allowDiagonalDescend = PathSettings.get().allowDiagonalDescend.value;
        this.allowDiagonalAscend = PathSettings.get().allowDiagonalAscend.value;
        this.allowDownward = PathSettings.get().allowDownward.value;
        this.minFallHeight = 3; // Minimum fall height used by MovementFall
        this.maxFallHeightNoWater = PathSettings.get().maxFallHeightNoWater.value;
        this.maxFallHeightBucket = PathSettings.get().maxFallHeightBucket.value;
        float waterSpeedMultiplier = 1.0f;
        OUTER: for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemEnchantments itemEnchantments = player
                .getItemBySlot(slot)
                .getEnchantments();
            for (Holder<Enchantment> enchant : itemEnchantments.keySet()) {
                List<EnchantmentAttributeEffect> effects = enchant.value()
                    .getEffects(EnchantmentEffectComponents.ATTRIBUTES);
                for (EnchantmentAttributeEffect effect : effects) {
                    if (effect.attribute().is(Attributes.WATER_MOVEMENT_EFFICIENCY.unwrapKey().get())) {
                        waterSpeedMultiplier = effect.amount().calculate(itemEnchantments.getLevel(enchant));
                        break OUTER;
                    }
                }
            }
        }
        this.waterWalkSpeed = ActionCosts.WALK_ONE_IN_WATER_COST * (1 - waterSpeedMultiplier) + ActionCosts.WALK_ONE_BLOCK_COST * waterSpeedMultiplier;
        this.breakBlockAdditionalCost = PathSettings.get().blockBreakAdditionalPenalty.value;
        this.backtrackCostFavoringCoefficient = PathSettings.get().backtrackCostFavoringCoefficient.value;
        this.jumpPenalty = PathSettings.get().jumpPenalty.value;
        this.walkOnWaterOnePenalty = PathSettings.get().walkOnWaterOnePenalty.value;
        this.allowWalkOnMagmaBlocks = PathSettings.get().allowWalkOnMagmaBlocks.value;
        // why cache these things here, why not let the movements just get directly from settings?
        // because if some movements are calculated one way and others are calculated another way,
        // then you get a wildly inconsistent path that isn't optimal for either scenario.
        this.worldBorder = new BetterWorldBorder(world.getWorldBorder());
    }

    /** Blocks we may put down to bridge or pillar (Baritone's "throwaway" items). */
    public static boolean hasThrowawayBlocks(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (PathSettings.get().acceptableThrowawayItems.value.contains(inv.getItem(i).getItem())) return true;
        }
        return false;
    }

    public BlockState get(int x, int y, int z) {
        return bsi.get0(x, y, z); // laughs maniacally
    }

    public boolean isLoaded(int x, int z) {
        return bsi.isLoaded(x, z);
    }

    public BlockState get(BlockPos pos) {
        return get(pos.getX(), pos.getY(), pos.getZ());
    }

    public Block getBlock(int x, int y, int z) {
        return get(x, y, z).getBlock();
    }

    public double costOfPlacingAt(int x, int y, int z, BlockState current) {
        if (!hasThrowaway) { // only true if allowPlace is true, see constructor
            return COST_INF;
        }
        if (isPossiblyProtected(x, y, z)) {
            return COST_INF;
        }
        if (!worldBorder.canPlaceAt(x, z)) {
            return COST_INF;
        }
        if (!PathSettings.get().allowPlaceInFluidsSource.value && current.getFluidState().isSource()) {
            return COST_INF;
        }
        if (!PathSettings.get().allowPlaceInFluidsFlow.value && !current.getFluidState().isEmpty() && !current.getFluidState().isSource()) {
            return COST_INF;
        }
        return placeBlockCost;
    }

    public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
        if (!allowBreak && !allowBreakAnyway.contains(current.getBlock())) {
            return COST_INF;
        }
        if (isPossiblyProtected(x, y, z)) {
            return COST_INF;
        }
        return 1;
    }

    public double placeBucketCost() {
        return placeBlockCost; // shrug
    }

    public boolean isPossiblyProtected(int x, int y, int z) {
        // TODO more protection logic here; see #220
        return false;
    }
}
