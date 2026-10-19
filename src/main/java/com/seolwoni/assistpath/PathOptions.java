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

/**
 * What a planned route may do. The Assist mods walk only (no digging or building on the way) unless they
 * say otherwise; farmland is never landed on from a height so fields are not trampled.
 *
 * @param allowBreak   dig through blocks in the way
 * @param allowPlace   put down blocks to cross gaps or climb
 * @param allowParkour jump over gaps
 * @param avoidWater   never step into water (farms: climbing out of a water channel can trample farmland)
 */
public record PathOptions(boolean allowBreak, boolean allowPlace, boolean allowParkour, boolean avoidWater) {
	/** Walking, climbing steps, dropping down and swimming only. */
	public static final PathOptions WALK = new PathOptions(false, false, false, false);
	/** Walking without going into water (around a field's water channels instead of through them). */
	public static final PathOptions WALK_DRY = new PathOptions(false, false, false, true);
	/** Everything Baritone does by default (used by the planning tests). */
	public static final PathOptions FULL = new PathOptions(true, true, false, false);
}
