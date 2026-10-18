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

package com.seolwoni.assistpath.walk;

import com.seolwoni.assistcore.engine.Action;
import com.seolwoni.assistpath.PathOptions;
import com.seolwoni.assistpath.goals.Goal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** An action that walks to a goal with {@link PathWalker}. */
public class PathWalkAction implements Action {
	private final PathWalker walker;
	private final int failMode;

	public PathWalkAction(Goal goal, PathOptions options, int failMode) {
		this.walker = new PathWalker(goal, options, 6);
		this.failMode = failMode;
	}

	public PathWalkAction(Goal goal) {
		this(goal, PathOptions.WALK, SKIP);
	}

	public PathWalker walker() {
		return walker;
	}

	@Override
	public int tick(Minecraft mc, LocalPlayer p) {
		return walker.tick(mc, p);
	}

	@Override
	public int failMode() {
		return failMode;
	}

	@Override
	public String failReason() {
		return walker.failReason();
	}
}
