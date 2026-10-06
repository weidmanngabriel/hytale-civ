package dev.civilizations.hytale;

import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineInfrastructureTask;
import dev.civilizations.core.MineTunnel;
import dev.civilizations.core.MineTunnelGeometry;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** Resolves semantic infrastructure work into concrete Hytale block placements. */
public final class MineInfrastructurePlacementResolver {

    private static final String FIR_BRANCH = "Wood_Fir_Branch_Long";
    private static final String FIR_TRUNK = "Wood_Fir_Trunk";
    private static final int SUPPORT_VERTICAL_SCAN = 12;

    private MineInfrastructurePlacementResolver() {
    }

    public static ResolvedTask resolve(
        World world,
        MineInfrastructureTask task,
        MineTunnel.Kind tunnelKind,
        MineTunnelGeometry geometry
    ) {
        if (world == null || task == null || tunnelKind == null || geometry == null) return null;
        return switch (task.type()) {
            case BUILD_SUPPORT -> resolveSupport(world, task, geometry);
            case PLACE_LIGHT -> resolveLight(world, task, tunnelKind, geometry);
            case BUILD_STEP -> resolveStep(world, task, geometry);
            case BUILD_BRIDGE -> resolveBridge(world, task, geometry);
        };
    }

    private static ResolvedTask resolveSupport(
        World world,
        MineInfrastructureTask task,
        MineTunnelGeometry geometry
    ) {
        int index = task.startSliceIndex();
        if (index <= 0 || index >= geometry.slices().size() - 1) return null;
        MineTunnelGeometry.Slice slice = geometry.slices().get(index);
        Cardinal forward = localForward(geometry.slices(), index);
        Cardinal cross = forward.cross();
        int minOffset = -(slice.widthBlocks() / 2);
        int maxOffset = minOffset + slice.widthBlocks() - 1;

        // Grow into already-open side pockets by at most three cells; do not make cave-spanning
        // monster frames from a normal tunnel support.
        for (int i = 0; i < 3; i++) {
            BlockPosition candidate = at(slice.floorCenter(), cross, minOffset - 1, 1);
            if (!isEmpty(world, candidate)) break;
            minOffset--;
        }
        for (int i = 0; i < 3; i++) {
            BlockPosition candidate = at(slice.floorCenter(), cross, maxOffset + 1, 1);
            if (!isEmpty(world, candidate)) break;
            maxOffset++;
        }

        if (maxOffset - minOffset - 1 < 4) return null;

        int minBeamY = slice.floorCenter().y() + 3;
        int maxBeamY = slice.floorCenter().y() + Math.max(slice.heightBlocks() + 5, 8);
        int beamY = -1;
        for (int y = minBeamY; y <= maxBeamY; y++) {
            if (!rowEmpty(world, slice.floorCenter(), cross, minOffset, maxOffset, y)) break;
            beamY = y;
        }
        if (beamY < minBeamY) return null;

        BlockPosition leftBaseStone = solidBelow(
            world, atY(at(slice.floorCenter(), cross, minOffset, 0), slice.floorCenter().y() - 1)
        );
        BlockPosition rightBaseStone = solidBelow(
            world, atY(at(slice.floorCenter(), cross, maxOffset, 0), slice.floorCenter().y() - 1)
        );
        if (leftBaseStone == null || rightBaseStone == null) return null;

        List<PlacementStep> placements = new ArrayList<>();
        RotationTuple vertical = RotationTuple.NONE;

        for (int y = leftBaseStone.y() + 1; y < beamY; y++) {
            BlockPosition position = atY(at(slice.floorCenter(), cross, minOffset, 0), y);
            placements.add(new PlacementStep(position, FIR_BRANCH, vertical,
                new BlockPosition(position.x(), y - 1, position.z()), true));
        }
        for (int y = rightBaseStone.y() + 1; y < beamY; y++) {
            BlockPosition position = atY(at(slice.floorCenter(), cross, maxOffset, 0), y);
            placements.add(new PlacementStep(position, FIR_BRANCH, vertical,
                new BlockPosition(position.x(), y - 1, position.z()), true));
        }

        RotationTuple beamRotation = trunkRotation(cross);
        // Build the top beam from both outside ends towards the centre.
        int left = minOffset;
        int right = maxOffset;
        while (left <= right) {
            BlockPosition leftPosition = atY(at(slice.floorCenter(), cross, left, 0), beamY);
            placements.add(new PlacementStep(leftPosition, FIR_TRUNK, beamRotation,
                new BlockPosition(leftPosition.x(), beamY - 1, leftPosition.z()), true));
            if (right != left) {
                BlockPosition rightPosition = atY(at(slice.floorCenter(), cross, right, 0), beamY);
                placements.add(new PlacementStep(rightPosition, FIR_TRUNK, beamRotation,
                    new BlockPosition(rightPosition.x(), beamY - 1, rightPosition.z()), true));
            }
            left++;
            right--;
        }

        if (placements.isEmpty()) return null;
        return new ResolvedTask(workTarget(slice.floorCenter()), List.copyOf(placements));
    }

    private static ResolvedTask resolveLight(
        World world,
        MineInfrastructureTask task,
        MineTunnel.Kind tunnelKind,
        MineTunnelGeometry geometry
    ) {
        int index = task.startSliceIndex();
        if (index <= 0 || index >= geometry.slices().size() - 1) return null;
        MineTunnelGeometry.Slice slice = geometry.slices().get(index);
        Cardinal forward = localForward(geometry.slices(), index);
        Cardinal cross = forward.cross();
        int minOffset = -(slice.widthBlocks() / 2);
        int maxOffset = minOffset + slice.widthBlocks() - 1;

        if (tunnelKind == MineTunnel.Kind.MAIN) {
            String pillar = MineBlockPlacement.resolveAsset(
                new String[]{"Stone_Brick_Pillar_Base", "Stone_Brick_Pillar_-_Base"},
                "stone", "brick", "pillar", "base"
            );
            String lantern = MineBlockPlacement.resolveAsset(
                new String[]{"Lantern", "Furniture_Lantern"},
                "lantern"
            );
            if (pillar == null || lantern == null) return null;

            int[] candidates = new int[]{minOffset + 1, maxOffset - 1};
            for (int lateral : candidates) {
                if (Math.abs(lateral) <= 1) continue;
                BlockPosition base = at(slice.floorCenter(), cross, lateral, 0);
                BlockPosition below = new BlockPosition(base.x(), base.y() - 1, base.z());
                BlockPosition top = new BlockPosition(base.x(), base.y() + 1, base.z());
                if (!isEmpty(world, base) || !isEmpty(world, top) || isEmpty(world, below)) continue;
                return new ResolvedTask(
                    workTarget(slice.floorCenter()),
                    List.of(
                        new PlacementStep(base, pillar, RotationTuple.NONE, below, false),
                        new PlacementStep(top, lantern, RotationTuple.NONE, base, false)
                    )
                );
            }
            return null;
        }

        String torch = MineBlockPlacement.resolveAsset(
            new String[]{"Torch", "Wall_Torch"},
            "torch"
        );
        if (torch == null) return null;

        int y = slice.floorCenter().y() + 2;
        int[] edgeOffsets = new int[]{minOffset, maxOffset};
        for (int lateral : edgeOffsets) {
            BlockPosition target = atY(at(slice.floorCenter(), cross, lateral, 0), y);
            Cardinal outward = lateral == minOffset ? cross.opposite() : cross;
            BlockPosition wall = new BlockPosition(
                target.x() + outward.dx(), target.y(), target.z() + outward.dz()
            );
            if (!isEmpty(world, target) || isEmpty(world, wall)) continue;
            RotationTuple rotation = yawRotation(outward.opposite());
            return new ResolvedTask(
                workTarget(slice.floorCenter()),
                List.of(new PlacementStep(target, torch, rotation, wall, false))
            );
        }
        return null;
    }

    private static ResolvedTask resolveStep(
        World world,
        MineInfrastructureTask task,
        MineTunnelGeometry geometry
    ) {
        String stair = MineBlockPlacement.resolveAsset(
            new String[]{"Stone_Brick_Stairs", "Stone_Brick_Stair", "Stone_Stairs"},
            "stone", "stair"
        );
        if (stair == null) {
            stair = MineBlockPlacement.resolveAsset(
                new String[]{"Stone_Brick_Steps", "Stone_Steps"},
                "stone", "step"
            );
        }
        if (stair == null) return null;

        List<PlacementStep> placements = new ArrayList<>();
        for (MineTunnelGeometry.StepTransition transition : geometry.stepTransitions()) {
            if (transition.fromSliceIndex() < task.startSliceIndex()
                || transition.toSliceIndex() > task.endSliceIndex()) {
                continue;
            }

            BlockPosition from = transition.fromFloorCenter();
            BlockPosition to = transition.toFloorCenter();
            BlockPosition low = from.y() < to.y() ? from : to;
            BlockPosition high = from.y() < to.y() ? to : from;
            Cardinal rise = cardinalBetween(low, high);
            if (rise == null) {
                rise = localForward(geometry.slices(), transition.fromSliceIndex());
            }
            Cardinal cross = rise.cross();
            RotationTuple rotation = yawRotation(rise);

            for (int lateral = -1; lateral <= 1; lateral++) {
                BlockPosition position = at(low, cross, lateral, 0);
                BlockPosition below = new BlockPosition(
                    position.x(), position.y() - 1, position.z()
                );
                if (!isEmpty(world, position) || isEmpty(world, below)) continue;
                placements.add(new PlacementStep(position, stair, rotation, below, false));
            }
        }

        if (placements.isEmpty()) return null;
        MineTunnelGeometry.Slice start = geometry.slices().get(task.startSliceIndex());
        return new ResolvedTask(workTarget(start.floorCenter()), List.copyOf(placements));
    }

    private static ResolvedTask resolveBridge(
        World world,
        MineInfrastructureTask task,
        MineTunnelGeometry geometry
    ) {
        String deck = MineBlockPlacement.resolveAsset(
            new String[]{"Wood_Fir_Planks", "Wood_Fir_Plank"},
            "fir", "plank"
        );
        if (deck == null) deck = FIR_BRANCH;

        List<PlacementStep> placements = new ArrayList<>();
        List<MineTunnelGeometry.Slice> slices = geometry.slices();
        for (int index = task.startSliceIndex(); index <= task.endSliceIndex(); index++) {
            MineTunnelGeometry.Slice slice = slices.get(index);
            Cardinal forward = localForward(slices, index);
            Cardinal cross = forward.cross();
            int deckY = slice.floorCenter().y() - 1;

            for (int lateral = -1; lateral <= 1; lateral++) {
                BlockPosition position = atY(at(slice.floorCenter(), cross, lateral, 0), deckY);
                if (!isEmpty(world, position)) continue;
                placements.add(new PlacementStep(
                    position, deck, RotationTuple.NONE,
                    new BlockPosition(position.x(), position.y() - 1, position.z()), true
                ));
            }

            RotationTuple longRotation = trunkRotation(forward);
            for (int lateral : new int[]{-2, 2}) {
                BlockPosition position = atY(at(slice.floorCenter(), cross, lateral, 0), deckY);
                if (!isEmpty(world, position)) continue;
                placements.add(new PlacementStep(
                    position, FIR_TRUNK, longRotation,
                    new BlockPosition(position.x(), position.y() - 1, position.z()), true
                ));
            }

            if ((index - task.startSliceIndex()) % 3 == 0) {
                RotationTuple crossRotation = trunkRotation(cross);
                for (int lateral = -2; lateral <= 2; lateral++) {
                    BlockPosition position = atY(at(slice.floorCenter(), cross, lateral, 0), deckY - 1);
                    if (!isEmpty(world, position)) continue;
                    placements.add(new PlacementStep(
                        position, FIR_TRUNK, crossRotation,
                        new BlockPosition(position.x(), position.y() - 1, position.z()), true
                    ));
                }
            }
        }

        if (placements.isEmpty()) return null;
        BlockPosition work = task.startSliceIndex() > 0
            ? slices.get(task.startSliceIndex() - 1).floorCenter()
            : slices.get(task.startSliceIndex()).floorCenter();
        return new ResolvedTask(workTarget(work), List.copyOf(placements));
    }

    private static BlockPosition solidBelow(World world, BlockPosition start) {
        for (int depth = 0; depth < SUPPORT_VERTICAL_SCAN; depth++) {
            BlockPosition candidate = new BlockPosition(start.x(), start.y() - depth, start.z());
            if (!isEmpty(world, candidate)) return candidate;
        }
        return null;
    }

    private static boolean rowEmpty(
        World world,
        BlockPosition center,
        Cardinal cross,
        int minOffset,
        int maxOffset,
        int y
    ) {
        for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
            if (!isEmpty(world, atY(at(center, cross, lateral, 0), y))) return false;
        }
        return true;
    }

    private static boolean isEmpty(World world, BlockPosition position) {
        var chunk = world.getChunkIfLoaded(
            com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(position.x(), position.z())
        );
        if (chunk == null) return false;
        var type = chunk.getBlockType(position.x(), position.y(), position.z());
        return type == null
            || type == com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY
            || type.getMaterial() == com.hypixel.hytale.protocol.BlockMaterial.Empty;
    }

    private static Cardinal localForward(List<MineTunnelGeometry.Slice> slices, int index) {
        BlockPosition before = slices.get(Math.max(0, index - 1)).floorCenter();
        BlockPosition after = slices.get(Math.min(slices.size() - 1, index + 1)).floorCenter();
        Cardinal result = cardinalBetween(before, after);
        return result == null ? Cardinal.EAST : result;
    }

    private static Cardinal cardinalBetween(BlockPosition from, BlockPosition to) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        if (dx == 0 && dz == 0) return null;
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Cardinal.EAST : Cardinal.WEST;
        return dz >= 0 ? Cardinal.SOUTH : Cardinal.NORTH;
    }

    private static BlockPosition at(
        BlockPosition center,
        Cardinal direction,
        int distance,
        int yOffset
    ) {
        return new BlockPosition(
            center.x() + direction.dx() * distance,
            center.y() + yOffset,
            center.z() + direction.dz() * distance
        );
    }

    private static BlockPosition atY(BlockPosition position, int y) {
        return new BlockPosition(position.x(), y, position.z());
    }

    private static RotationTuple trunkRotation(Cardinal axis) {
        // The verified V0 prefab used rotation index 4 for a trunk running along local Z.
        RotationTuple baseAlongZ = RotationTuple.get(4);
        if (axis.dx() != 0) {
            return baseAlongZ.composeOnAxis(Axis.Y, Rotation.Ninety);
        }
        return baseAlongZ;
    }

    private static RotationTuple yawRotation(Cardinal direction) {
        return RotationTuple.NONE.composeOnAxis(Axis.Y, Rotation.ofDegrees(direction.yawDegrees()));
    }

    private static Vector3d workTarget(BlockPosition position) {
        return new Vector3d(position.x() + 0.5, position.y(), position.z() + 0.5);
    }

    public record PlacementStep(
        BlockPosition position,
        String blockId,
        RotationTuple rotation,
        BlockPosition placedAgainst,
        boolean markDeco
    ) {
    }

    public record ResolvedTask(Vector3d workTarget, List<PlacementStep> placements) {
        public ResolvedTask {
            placements = List.copyOf(placements);
        }
    }

    private enum Cardinal {
        NORTH(0, -1, 270),
        EAST(1, 0, 0),
        SOUTH(0, 1, 90),
        WEST(-1, 0, 180);

        private final int dx;
        private final int dz;
        private final int yawDegrees;

        Cardinal(int dx, int dz, int yawDegrees) {
            this.dx = dx;
            this.dz = dz;
            this.yawDegrees = yawDegrees;
        }

        int dx() {
            return dx;
        }

        int dz() {
            return dz;
        }

        int yawDegrees() {
            return yawDegrees;
        }

        Cardinal cross() {
            return new Cardinal[]{NORTH, EAST, SOUTH, WEST}[(ordinal() + 1) % 4];
        }

        Cardinal opposite() {
            return new Cardinal[]{NORTH, EAST, SOUTH, WEST}[(ordinal() + 2) % 4];
        }
    }
}
