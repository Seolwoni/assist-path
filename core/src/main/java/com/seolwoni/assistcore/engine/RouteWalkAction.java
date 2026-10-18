package com.seolwoni.assistcore.engine;

import java.util.List;

import com.seolwoni.assistcore.util.Body;
import com.seolwoni.assistcore.util.Human;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/**
 * Walks a list of cells in one smooth go: no stop at every cell, runs on straight stretches (unless the
 * work pace is "relaxed"), and only settles exactly on the last cell.
 */
public class RouteWalkAction implements Action {
	private final List<BlockPos> route;
	private int idx, onCell, pre = -1, post = -1;

	public RouteWalkAction(List<BlockPos> route) {
		this.route = route;
	}

	/** Index of the cell being walked to (where a fallback should pick up after a failure). */
	public int index() {
		return idx;
	}

	@Override
	public int failMode() {
		return SKIP;
	}

	/** Straight, level cells ahead of {@code from} (same step each time). */
	private int straightAhead(int from) {
		if (from + 1 >= route.size()) return 0;
		BlockPos a = route.get(from), b = route.get(from + 1);
		int sx = b.getX() - a.getX(), sy = b.getY() - a.getY(), sz = b.getZ() - a.getZ();
		int n = 1;
		for (int i = from + 1; i + 1 < route.size(); i++) {
			BlockPos c = route.get(i), d = route.get(i + 1);
			if (d.getX() - c.getX() != sx || d.getY() - c.getY() != sy || d.getZ() - c.getZ() != sz) break;
			n++;
		}
		return sy == 0 ? n : 0;
	}

	@Override
	public int tick(Minecraft mc, LocalPlayer p) {
		if (route.isEmpty()) return DONE;
		if (pre < 0) {
			pre = Human.delay(0, 4);
			Body.resetWalk();
			Body.newTurn();
		}
		if (pre-- > 0) return RUN;
		if (post >= 0) return post-- <= 0 ? DONE : RUN;
		if (idx >= route.size() - 1) {
			// Last cell: arrive properly.
			if (Body.walkTo(p, route.get(route.size() - 1))) {
				Body.release(mc);
				post = Human.delay(0, 3);
				return RUN;
			}
		} else {
			boolean sprint = Human.pace >= 1 && straightAhead(idx) >= 3;
			if (Body.walkThrough(p, route.get(idx), route.get(idx + 1), sprint)) {
				idx++;
				onCell = 0;
				return RUN;
			}
		}
		if (++onCell > 80) {
			Body.release(mc);
			return FAIL;
		}
		return RUN;
	}
}
