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

package com.seolwoni.assistpath.calc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.seolwoni.assistpath.goals.Goal;
import com.seolwoni.assistpath.movement.CalculationContext;
import com.seolwoni.assistpath.movement.Movement;
import com.seolwoni.assistpath.movement.Moves;
import com.seolwoni.assistpath.util.BetterBlockPos;

/**
 * A found path: the positions and the movement between each pair of them.
 *
 * @author leijurv
 *
 * Modified for Assist Path (2026): no IPath interface, cut off where a movement became impossible.
 */
public final class Path {

    private final BetterBlockPos start;
    private BetterBlockPos end;
    private final List<BetterBlockPos> path;
    private final List<Movement> movements;
    private final List<PathNode> nodes;
    private final Goal goal;
    private final int numNodes;
    private final CalculationContext context;
    private boolean verified;

    Path(BetterBlockPos realStart, PathNode start, PathNode end, int numNodes, Goal goal, CalculationContext context) {
        this.end = new BetterBlockPos(end.x, end.y, end.z);
        this.numNodes = numNodes;
        this.movements = new ArrayList<>();
        this.goal = goal;
        this.context = context;

        PathNode current = end;
        List<BetterBlockPos> tempPath = new ArrayList<>();
        List<PathNode> tempNodes = new ArrayList<>();
        while (current != null) {
            tempNodes.add(current);
            tempPath.add(new BetterBlockPos(current.x, current.y, current.z));
            current = current.previous;
        }
        var startNodePos = new BetterBlockPos(start.x, start.y, start.z);
        if (!realStart.equals(startNodePos) && start.equals(end)) {
            this.start = realStart;
            PathNode fakeNode = new PathNode(realStart.x, realStart.y, realStart.z, goal);
            fakeNode.cost = 0;
            tempNodes.add(fakeNode);
            tempPath.add(realStart);
        } else {
            this.start = startNodePos;
        }
        this.path = new ArrayList<>(Lists.reverse(tempPath));
        this.nodes = new ArrayList<>(Lists.reverse(tempNodes));
    }

    public Goal getGoal() {
        return goal;
    }

    private Movement runBackwards(BetterBlockPos src, BetterBlockPos dest, double cost) {
        for (Moves moves : Moves.values()) {
            Movement move = moves.apply0(context, src);
            if (move.getDest().equals(dest)) {
                // the stricter of the cached node cost difference and the cost calculated now
                move.override(Math.min(move.calculateCost(context), cost));
                return move;
            }
        }
        return null;
    }

    /** Builds the movement list; if one became impossible during the search, the path stops before it. */
    public Path postProcess() {
        if (verified) {
            throw new IllegalStateException("Path must not be verified twice");
        }
        verified = true;
        for (int i = 0; i < path.size() - 1; i++) {
            double cost = nodes.get(i + 1).cost - nodes.get(i).cost;
            Movement move = runBackwards(path.get(i), path.get(i + 1), cost);
            if (move == null) {
                while (path.size() > i + 1) path.remove(path.size() - 1);
                end = path.get(path.size() - 1);
                break;
            }
            movements.add(move);
        }
        return this;
    }

    public List<Movement> movements() {
        return Collections.unmodifiableList(movements);
    }

    public List<BetterBlockPos> positions() {
        return Collections.unmodifiableList(path);
    }

    public int getNumNodesConsidered() {
        return numNodes;
    }

    public BetterBlockPos getSrc() {
        return start;
    }

    public BetterBlockPos getDest() {
        return end;
    }

    public double totalCost() {
        double c = 0;
        for (Movement m : movements) c += m.getCost();
        return c;
    }
}
