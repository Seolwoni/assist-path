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

import com.seolwoni.assistpath.PathSettings;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Fast block lookups for path calculation.
 *
 * @author leijurv
 *
 * Modified for Assist Path (2026): reads only the chunks the client has loaded (no world cache yet).
 */
public class BlockStateInterface {

    private final ClientChunkCache provider;
    protected final Level world;
    public final BlockPos.MutableBlockPos isPassableBlockPos;
    public final BlockGetter access;
    public final BetterWorldBorder worldBorder;

    private LevelChunk prev = null;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public BlockStateInterface(Level world) {
        this(world, false);
    }

    public BlockStateInterface(Level world, boolean copyLoadedChunks) {
        this.world = world;
        this.worldBorder = new BetterWorldBorder(world.getWorldBorder());
        this.provider = (ClientChunkCache) world.getChunkSource();
        this.isPassableBlockPos = new BlockPos.MutableBlockPos();
        this.access = new BlockStateInterfaceAccessWrapper(this);
    }

    public boolean worldContainsLoadedChunk(int blockX, int blockZ) {
        return provider.hasChunk(blockX >> 4, blockZ >> 4);
    }

    public static BlockState get(Level world, BlockPos pos) {
        return new BlockStateInterface(world).get0(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockState get0(BlockPos pos) {
        return get0(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockState get0(int x, int y, int z) { // Mickey resigned
        y -= world.dimensionType().minY();
        // Invalid vertical position
        if (y < 0 || y >= world.dimensionType().height()) {
            return AIR;
        }
        LevelChunk cached = prev;
        // there's great cache locality in block state lookups, skip the chunk lookup when possible (issue #113)
        if (cached != null && cached.getPos().x() == x >> 4 && cached.getPos().z() == z >> 4) {
            return getFromChunk(cached, x, y, z);
        }
        LevelChunk chunk = provider.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
        if (chunk != null && !chunk.isEmpty()) {
            prev = chunk;
            return getFromChunk(chunk, x, y, z);
        }
        return AIR;
    }

    public boolean isLoaded(int x, int z) {
        LevelChunk prevChunk = prev;
        if (prevChunk != null && prevChunk.getPos().x() == x >> 4 && prevChunk.getPos().z() == z >> 4) {
            return true;
        }
        prevChunk = provider.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
        if (prevChunk != null && !prevChunk.isEmpty()) {
            prev = prevChunk;
            return true;
        }
        return false;
    }

    // get the block at x,y,z from this chunk WITHOUT creating a single blockpos object
    public static BlockState getFromChunk(LevelChunk chunk, int x, int y, int z) {
        LevelChunkSection section = chunk.getSections()[y >> 4];
        if (section.hasOnlyAir()) {
            return AIR;
        }
        return section.getBlockState(x & 15, y & 15, z & 15);
    }
}
