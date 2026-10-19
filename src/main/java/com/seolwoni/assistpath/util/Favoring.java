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

package com.seolwoni.assistpath.util;

import java.util.List;

import com.seolwoni.assistpath.movement.CalculationContext;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;

/**
 * Makes the search prefer (or avoid) some positions, e.g. the previous path so a recalculated path does not
 * wobble. Modified for Assist Path (2026): mob avoidance removed for now.
 */
public final class Favoring {

    private final Long2DoubleOpenHashMap favorings;

    public Favoring(List<BetterBlockPos> previous, CalculationContext context) {
        favorings = new Long2DoubleOpenHashMap();
        favorings.defaultReturnValue(1.0D);
        double coeff = context.backtrackCostFavoringCoefficient;
        if (coeff != 1D && previous != null) {
            previous.forEach(pos -> favorings.put(BetterBlockPos.longHash(pos), coeff));
        }
        // Mob avoidance (from Baritone's Avoidance): cells near a visible hostile mob cost more, so the route keeps
        // its distance when there is another way. Not a wall: if the only way passes a mob, the route still goes.
        if (com.seolwoni.assistpath.PathSettings.get().avoidance.value && context.mobs != null) {
            double c = com.seolwoni.assistpath.PathSettings.get().mobAvoidanceCoefficient.value;
            int r = com.seolwoni.assistpath.PathSettings.get().mobAvoidanceRadius.value;
            for (net.minecraft.core.BlockPos m : context.mobs) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (dx * dx + dy * dy + dz * dz > r * r) continue;
                            long h = BetterBlockPos.longHash(m.getX() + dx, m.getY() + dy, m.getZ() + dz);
                            favorings.put(h, favorings.get(h) * c);
                        }
                    }
                }
            }
        }
    }

    public boolean isEmpty() {
        return favorings.isEmpty();
    }

    public double calculate(long hash) {
        return favorings.get(hash);
    }
}
