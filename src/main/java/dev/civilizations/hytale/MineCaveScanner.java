package dev.civilizations.hytale;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.ShaderType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineCaveObservation;
import dev.civilizations.core.MineCavePolicy;
import dev.civilizations.core.MineTunnelGeometry;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Local loaded-world cave scanner.
 *
 * <p>Only additional empty space outside known planned mine excavation is classified. The scanner
 * never loads chunks, never provides movement routes and never replaces Hytale navigation.</p>
 */
public final class MineCaveScanner {

    private static final int[][] NEIGHBORS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0},
        {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private MineCaveScanner() {
    }

    public static MineCaveObservation scan(
        World world,
        MineTunnelGeometry.Slice encounterSlice,
        Collection<MineTunnelGeometry> knownMineGeometries
    ) {
        if (world == null || encounterSlice == null || knownMineGeometries == null) {
            throw new IllegalArgumentException("Mine cave scan inputs must not be null.");
        }

        BlockPosition origin = encounterSlice.floorCenter();
        int horizontal = MineCavePolicy.SCAN_HORIZONTAL_RADIUS;
        int vertical = MineCavePolicy.SCAN_VERTICAL_RADIUS;

        Set<BlockPosition> excludedMine = new HashSet<>();
        for (MineTunnelGeometry geometry : knownMineGeometries) {
            for (BlockPosition block : geometry.excavationBlocks()) {
                if (insideBounds(origin, block, horizontal, vertical)) excludedMine.add(block);
            }
        }

        Set<BlockPosition> seeds = new HashSet<>();
        for (BlockPosition tunnelBlock : encounterSlice.excavationBlocks()) {
            for (int[] delta : NEIGHBORS) {
                BlockPosition neighbor = offset(tunnelBlock, delta);
                if (!insideBounds(origin, neighbor, horizontal, vertical)
                    || excludedMine.contains(neighbor)) {
                    continue;
                }
                BlockType type = loadedBlockType(world, neighbor);
                if (isEmpty(type)) seeds.add(neighbor);
            }
        }
        if (seeds.isEmpty()) return emptyObservation(origin);

        Set<BlockPosition> globallyVisited = new HashSet<>();
        Component best = null;
        boolean anyIncomplete = false;

        for (BlockPosition seed : seeds) {
            if (globallyVisited.contains(seed)) continue;
            Component component = flood(world, origin, seed, excludedMine, globallyVisited);
            anyIncomplete |= !component.complete;
            if (best == null || component.emptyBlocks > best.emptyBlocks) best = component;
        }

        if (best == null || best.emptyBlocks == 0) return emptyObservation(origin);

        MineCaveObservation.Status status = MineCavePolicy.classify(
            best.emptyBlocks,
            best.usableFloorBlocks,
            best.max.x() - best.min.x() + 1,
            best.max.y() - best.min.y() + 1,
            best.max.z() - best.min.z() + 1,
            best.complete && !anyIncomplete
        );

        BlockPosition center = new BlockPosition(
            (best.min.x() + best.max.x()) / 2,
            (best.min.y() + best.max.y()) / 2,
            (best.min.z() + best.max.z()) / 2
        );
        return new MineCaveObservation(
            status,
            center,
            best.min,
            best.max,
            best.emptyBlocks,
            best.usableFloorBlocks,
            best.hasFluid,
            best.hasLava
        );
    }

    private static Component flood(
        World world,
        BlockPosition origin,
        BlockPosition seed,
        Set<BlockPosition> excludedMine,
        Set<BlockPosition> globallyVisited
    ) {
        ArrayDeque<BlockPosition> queue = new ArrayDeque<>();
        Set<BlockPosition> local = new HashSet<>();
        queue.add(seed);

        BlockPosition min = seed;
        BlockPosition max = seed;
        int empty = 0;
        int usableFloor = 0;
        boolean hasFluid = false;
        boolean hasLava = false;
        boolean complete = true;

        while (!queue.isEmpty()) {
            BlockPosition current = queue.removeFirst();
            if (!local.add(current)) continue;
            globallyVisited.add(current);

            if (local.size() > MineCavePolicy.MAX_SCANNED_EMPTY_BLOCKS) {
                complete = false;
                break;
            }
            if (!insideBounds(
                origin,
                current,
                MineCavePolicy.SCAN_HORIZONTAL_RADIUS,
                MineCavePolicy.SCAN_VERTICAL_RADIUS
            )) {
                complete = false;
                continue;
            }
            if (excludedMine.contains(current)) continue;

            WorldChunk chunk = loadedChunk(world, current);
            if (chunk == null) {
                complete = false;
                continue;
            }
            BlockType type = chunk.getBlockType(current.x(), current.y(), current.z());
            if (!isEmpty(type)) continue;

            empty++;
            min = new BlockPosition(
                Math.min(min.x(), current.x()),
                Math.min(min.y(), current.y()),
                Math.min(min.z(), current.z())
            );
            max = new BlockPosition(
                Math.max(max.x(), current.x()),
                Math.max(max.y(), current.y()),
                Math.max(max.z(), current.z())
            );

            int fluidId = chunk.getFluidId(current.x(), current.y(), current.z());
            if (fluidId != Fluid.EMPTY_ID) {
                hasFluid = true;
                hasLava |= isLava(fluidId);
            }
            BlockPosition below = new BlockPosition(current.x(), current.y() - 1, current.z());
            WorldChunk belowChunk = loadedChunk(world, below);
            if (belowChunk == null) {
                complete = false;
            } else {
                BlockType belowType = belowChunk.getBlockType(below.x(), below.y(), below.z());
                if (!isEmpty(belowType)
                    && chunk.getFluidId(current.x(), current.y(), current.z()) == Fluid.EMPTY_ID) {
                    usableFloor++;
                }
            }

            for (int[] delta : NEIGHBORS) {
                BlockPosition next = offset(current, delta);
                if (excludedMine.contains(next) || local.contains(next)) continue;
                if (!insideBounds(
                    origin,
                    next,
                    MineCavePolicy.SCAN_HORIZONTAL_RADIUS,
                    MineCavePolicy.SCAN_VERTICAL_RADIUS
                )) {
                    complete = false;
                    continue;
                }
                WorldChunk nextChunk = loadedChunk(world, next);
                if (nextChunk == null) {
                    complete = false;
                    continue;
                }
                if (isEmpty(nextChunk.getBlockType(next.x(), next.y(), next.z()))) queue.addLast(next);
            }
        }

        return new Component(min, max, empty, usableFloor, hasFluid, hasLava, complete);
    }

    private static MineCaveObservation emptyObservation(BlockPosition origin) {
        return new MineCaveObservation(
            MineCaveObservation.Status.NONE,
            origin,
            origin,
            origin,
            0,
            0,
            false,
            false
        );
    }

    private static boolean insideBounds(
        BlockPosition origin,
        BlockPosition block,
        int horizontal,
        int vertical
    ) {
        return Math.abs(block.x() - origin.x()) <= horizontal
            && Math.abs(block.z() - origin.z()) <= horizontal
            && Math.abs(block.y() - origin.y()) <= vertical;
    }

    private static BlockPosition offset(BlockPosition origin, int[] delta) {
        return new BlockPosition(
            origin.x() + delta[0],
            origin.y() + delta[1],
            origin.z() + delta[2]
        );
    }

    private static WorldChunk loadedChunk(World world, BlockPosition block) {
        return world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(block.x(), block.z()));
    }

    private static BlockType loadedBlockType(World world, BlockPosition block) {
        WorldChunk chunk = loadedChunk(world, block);
        return chunk == null ? null : chunk.getBlockType(block.x(), block.y(), block.z());
    }

    private static boolean isEmpty(BlockType type) {
        return type == BlockType.EMPTY
            || (type != null && type.getMaterial() == BlockMaterial.Empty);
    }

    private static boolean isLava(int fluidId) {
        if (fluidId == Fluid.EMPTY_ID) return false;
        Fluid fluid = Fluid.getAssetMap().getAssetOrDefault(fluidId, Fluid.UNKNOWN);
        return fluid != null && fluid.hasEffect(ShaderType.Lava);
    }

    private record Component(
        BlockPosition min,
        BlockPosition max,
        int emptyBlocks,
        int usableFloorBlocks,
        boolean hasFluid,
        boolean hasLava,
        boolean complete
    ) {
    }
}
