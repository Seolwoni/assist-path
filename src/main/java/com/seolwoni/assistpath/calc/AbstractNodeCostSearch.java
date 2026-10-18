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

import com.seolwoni.assistpath.PathSettings;
import com.seolwoni.assistpath.goals.Goal;
import com.seolwoni.assistpath.util.BetterBlockPos;
import com.seolwoni.assistpath.movement.CalculationContext;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Optional;

/**
 * Any pathfinding algorithm that keeps track of nodes recursively by their cost (e.g. A*, dijkstra)
 *
 * @author leijurv
 */
public abstract class AbstractNodeCostSearch {

    protected final BetterBlockPos realStart;
    protected final int startX;
    protected final int startY;
    protected final int startZ;

    protected final Goal goal;

    private final CalculationContext context;

    /**
     * @see <a href="https://github.com/cabaletta/baritone/issues/107">Issue #107</a>
     */
    private final Long2ObjectOpenHashMap<PathNode> map;

    protected PathNode startNode;

    protected PathNode mostRecentConsidered;

    protected final PathNode[] bestSoFar = new PathNode[COEFFICIENTS.length];

    private volatile boolean isFinished;

    protected boolean cancelRequested;

    /**
     * This is really complicated and hard to explain. I wrote a comment in the old version of MineBot but it was so
     * long it was easier as a Google Doc (because I could insert charts).
     *
     * @see <a href="https://docs.google.com/document/d/1WVHHXKXFdCR1Oz__KtK8sFqyvSwJN_H4lftkHFgmzlc/edit">here</a>
     */
    protected static final double[] COEFFICIENTS = {1.5, 2, 2.5, 3, 4, 5, 10};

    /**
     * If a path goes less than 5 blocks and doesn't make it to its goal, it's not worth considering.
     */
    protected static final double MIN_DIST_PATH = 5;

    /**
     * there are floating point errors caused by random combinations of traverse and diagonal over a flat area
     * that means that sometimes there's a cost improvement of like 10 ^ -16
     * it's not worth the time to update the costs, decrease-key the heap, potentially repropagate, etc
     * <p>
     * who cares about a hundredth of a tick? that's half a millisecond for crying out loud!
     */
    protected static final double MIN_IMPROVEMENT = 0.01;

    AbstractNodeCostSearch(BetterBlockPos realStart, int startX, int startY, int startZ, Goal goal, CalculationContext context) {
        this.realStart = realStart;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.goal = goal;
        this.context = context;
        this.map = new Long2ObjectOpenHashMap<>(PathSettings.get().pathingMapDefaultSize.value, PathSettings.get().pathingMapLoadFactor.value);
    }

    public void cancel() {
        cancelRequested = true;
    }

    /**
     * Runs the search. {@code primaryTimeout}: stop once a usable path exists after this long (ms);
     * {@code failureTimeout}: give up entirely after this long.
     */
    public synchronized PathResult calculate(long primaryTimeout, long failureTimeout) {
        if (isFinished) {
            throw new IllegalStateException("Path finder cannot be reused!");
        }
        cancelRequested = false;
        try {
            Path path = calculate0(primaryTimeout, failureTimeout).map(Path::postProcess).orElse(null);
            if (cancelRequested) {
                return new PathResult(PathResult.Type.CANCELLATION, null);
            }
            if (path == null || path.movements().isEmpty() && !goal.isInGoal(path.getDest())) {
                return new PathResult(PathResult.Type.FAILURE, null);
            }
            if (goal.isInGoal(path.getDest())) {
                return new PathResult(PathResult.Type.SUCCESS_TO_GOAL, path);
            }
            return new PathResult(PathResult.Type.SUCCESS_SEGMENT, path);
        } catch (Exception e) {
            e.printStackTrace();
            return new PathResult(PathResult.Type.EXCEPTION, null);
        } finally {
            isFinished = true;
        }
    }

    protected abstract Optional<Path> calculate0(long primaryTimeout, long failureTimeout);

    /**
     * Determines the distance squared from the specified node to the start
     * node. Intended for use in distance comparison, rather than anything that
     * considers the real distance value, hence the "sq".
     *
     * @param n A node
     * @return The distance, squared
     */
    protected double getDistFromStartSq(PathNode n) {
        int xDiff = n.x - startX;
        int yDiff = n.y - startY;
        int zDiff = n.z - startZ;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff;
    }

    /**
     * Attempts to search the block position hashCode long to {@link PathNode} map
     * for the node mapped to the specified pos. If no node is found,
     * a new node is created.
     *
     * @param x        The x position of the node
     * @param y        The y position of the node
     * @param z        The z position of the node
     * @param hashCode The hash code of the node, provided by {@link BetterBlockPos#longHash(int, int, int)}
     * @return The associated node
     * @see <a href="https://github.com/cabaletta/baritone/issues/107">Issue #107</a>
     */

    protected PathNode getNodeAtPosition(int x, int y, int z, long hashCode) {
        PathNode node = map.get(hashCode);
        if (node == null) {
            node = new PathNode(x, y, z, goal);
            map.put(hashCode, node);
        }
        return node;
    }

    public Optional<Path> pathToMostRecentNodeConsidered() {
        return Optional.ofNullable(mostRecentConsidered).map(node -> new Path(realStart, startNode, node, 0, goal, context));
    }

    public Optional<Path> bestPathSoFar() {
        return bestSoFar(false, 0);
    }

    protected Optional<Path> bestSoFar(boolean logInfo, int numNodes) {
        if (startNode == null) {
            return Optional.empty();
        }
        double bestDist = 0;
        for (int i = 0; i < COEFFICIENTS.length; i++) {
            if (bestSoFar[i] == null) {
                continue;
            }
            double dist = getDistFromStartSq(bestSoFar[i]);
            if (dist > bestDist) {
                bestDist = dist;
            }
            if (dist > MIN_DIST_PATH * MIN_DIST_PATH) { // square the comparison since distFromStartSq is squared
                if (logInfo) {
                    if (COEFFICIENTS[i] >= 3) {
                    }
                }
                return Optional.of(new Path(realStart, startNode, bestSoFar[i], numNodes, goal, context));
            }
        }
        // instead of returning bestSoFar[0], be less misleading
        // if it actually won't find any path, don't make them think it will by rendering a dark blue that will never actually happen
        if (logInfo) {
        }
        return Optional.empty();
    }

    public final boolean isFinished() {
        return isFinished;
    }

    public final Goal getGoal() {
        return goal;
    }

    public BetterBlockPos getStart() {
        return new BetterBlockPos(startX, startY, startZ);
    }

    protected int mapSize() {
        return map.size();
    }
}
