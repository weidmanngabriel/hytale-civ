package dev.civilizations.hytale;

import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineInfrastructureTask;
import dev.civilizations.core.MineObstaclePolicy;
import dev.civilizations.core.MineTunnel;
import dev.civilizations.core.MineTunnelGeometry;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
            case BUILD_SUPPORT -> resolveSupport(world, task, tunnelKind, geometry);
            case PLACE_LIGHT -> resolveLight(world, task, tunnelKind, geometry);
            case BUILD_STEP -> resolveStep(world, task, geometry);
            case BUILD_BRIDGE -> resolveBridge(world, task, geometry);
            case PLACE_DECORATION ->
                resolveDecorationDetailed(world, task, tunnelKind, geometry).resolvedTask();
        };
    }

    private static ResolvedTask resolveSupport(
        World world,
        MineInfrastructureTask task,
        MineTunnel.Kind tunnelKind,
        MineTunnelGeometry geometry
    ) {
        for (int index : candidateSliceOrder(task, geometry.slices().size())) {
            ResolvedTask resolved = resolveSupportAt(world, tunnelKind, geometry, index);
            if (resolved != null && placementsAreSafe(world, geometry, resolved)) return resolved;
        }
        return null;
    }

    private static ResolvedTask resolveSupportAt(
        World world,
        MineTunnel.Kind tunnelKind,
        MineTunnelGeometry geometry,
        int index
    ) {
        if (index <= 0 || index >= geometry.slices().size() - 1) return null;
        MineTunnelGeometry.Slice slice = geometry.slices().get(index);
        Cardinal forward = localForward(geometry.slices(), index);
        Cardinal cross = forward.cross();
        int minOffset = -(slice.widthBlocks() / 2);
        int maxOffset = minOffset + slice.widthBlocks() - 1;

        if (tunnelKind == MineTunnel.Kind.MAIN) {
            // The developed main tunnel may use nearby open side pockets for a stronger frame.
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
        }

        int minimumClearWidth = tunnelKind == MineTunnel.Kind.MAIN ? 4 : 3;
        if (maxOffset - minOffset - 1 < minimumClearWidth) return null;

        int minBeamY = slice.floorCenter().y() + 3;
        int maxBeamY = tunnelKind == MineTunnel.Kind.MAIN
            ? slice.floorCenter().y() + Math.max(slice.heightBlocks() + 5, 8)
            : slice.floorCenter().y() + slice.heightBlocks() - 1;
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
        String beamBlock = tunnelKind == MineTunnel.Kind.MAIN ? FIR_TRUNK : FIR_BRANCH;
        // Main uses a heavy trunk beam; branches use the same simple branch timber as the posts.
        int left = minOffset;
        int right = maxOffset;
        while (left <= right) {
            BlockPosition leftPosition = atY(at(slice.floorCenter(), cross, left, 0), beamY);
            placements.add(new PlacementStep(leftPosition, beamBlock, beamRotation,
                new BlockPosition(leftPosition.x(), beamY - 1, leftPosition.z()), true));
            if (right != left) {
                BlockPosition rightPosition = atY(at(slice.floorCenter(), cross, right, 0), beamY);
                placements.add(new PlacementStep(rightPosition, beamBlock, beamRotation,
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
        for (int index : candidateSliceOrder(task, geometry.slices().size())) {
            ResolvedTask resolved = resolveLightAt(world, tunnelKind, geometry, index);
            if (resolved != null && placementsAreSafe(world, geometry, resolved)) return resolved;
        }
        return null;
    }

    private static ResolvedTask resolveLightAt(
        World world,
        MineTunnel.Kind tunnelKind,
        MineTunnelGeometry geometry,
        int index
    ) {
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
                new String[]{"Deco_Lantern"},
                "deco", "lantern"
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

        // Both tunnel kinds use a freestanding floor lantern. The light is supported
        // from below, never attached to a wall (wall-mounted lights are not allowed).
        String pillar = MineBlockPlacement.resolveAsset(
            new String[]{"Stone_Brick_Pillar_Base", "Stone_Brick_Pillar_-_Base"},
            "stone", "brick", "pillar", "base"
        );
        String lantern = MineBlockPlacement.resolveAsset(
            new String[]{"Deco_Lantern"}, "deco", "lantern"
        );
        if (pillar == null || lantern == null) return null;
        for (int lateral : new int[]{minOffset + 1, maxOffset - 1}) {
            if (Math.abs(lateral) <= 1) continue;
            BlockPosition base = at(slice.floorCenter(), cross, lateral, 0);
            BlockPosition floor = new BlockPosition(base.x(), base.y() - 1, base.z());
            BlockPosition top = new BlockPosition(base.x(), base.y() + 1, base.z());
            if (!isEmpty(world, base) || !isEmpty(world, top) || isEmpty(world, floor)) continue;
            return new ResolvedTask(
                workTarget(slice.floorCenter()),
                List.of(
                    new PlacementStep(base, pillar, RotationTuple.NONE, floor, false),
                    new PlacementStep(top, lantern, RotationTuple.NONE, base, false)
                )
            );
        }
        return null;
    }

    public static DecorationResolution resolveDecorationDetailed(
        World world,
        MineInfrastructureTask task,
        MineTunnel.Kind tunnelKind,
        MineTunnelGeometry geometry
    ) {
        LinkedHashMap<String, Integer> reasons = new LinkedHashMap<>();
        List<Integer> triedSlices = new ArrayList<>();
        if (world == null || task == null || tunnelKind == null || geometry == null) {
            increment(reasons, "INVALID_INPUT");
            return new DecorationResolution(null, reasons, triedSlices);
        }
        if (task.type() != MineInfrastructureTask.Type.PLACE_DECORATION) {
            increment(reasons, "NOT_DECORATION_TASK");
            return new DecorationResolution(null, reasons, triedSlices);
        }
        if (task.decorationKind() == null) {
            increment(reasons, "DECORATION_KIND_MISSING");
            return new DecorationResolution(null, reasons, triedSlices);
        }

        for (int index : candidateSliceOrder(task, geometry.slices().size())) {
            triedSlices.add(index);
            ResolvedTask resolved = resolveDecorationAt(
                world,
                task.id(),
                task.decorationKind(),
                tunnelKind,
                geometry,
                index,
                reasons
            );
            if (resolved != null) {
                return new DecorationResolution(resolved, reasons, triedSlices);
            }
        }
        if (triedSlices.isEmpty()) increment(reasons, "NO_CANDIDATE_SLICE");
        return new DecorationResolution(null, reasons, triedSlices);
    }

    private static ResolvedTask resolveDecorationAt(
        World world,
        UUID taskId,
        MineInfrastructureTask.DecorationKind kind,
        MineTunnel.Kind tunnelKind,
        MineTunnelGeometry geometry,
        int index,
        Map<String, Integer> reasons
    ) {
        if (index <= 0 || index >= geometry.slices().size() - 1) {
            increment(reasons, "SLICE_OUT_OF_RANGE");
            return null;
        }
        MineTunnelGeometry.Slice slice = geometry.slices().get(index);
        Cardinal forward = localForward(geometry.slices(), index);
        Cardinal cross = forward.cross();
        int minOffset = -(slice.widthBlocks() / 2);
        int maxOffset = minOffset + slice.widthBlocks() - 1;

        if (tunnelKind == MineTunnel.Kind.BRANCH
            && (kind == MineInfrastructureTask.DecorationKind.HANGING_CHAIN
                || kind == MineInfrastructureTask.DecorationKind.HANGING_LANTERN)) {
            increment(reasons, "UNSUPPORTED_ON_BRANCH");
            return null;
        }

        return switch (kind) {
            case HANGING_CHAIN -> hangingDecoration(
                world, slice, cross, minOffset, maxOffset, false, reasons
            );
            case HANGING_LANTERN -> hangingDecoration(
                world, slice, cross, minOffset, maxOffset, true, reasons
            );
            case TIMBER_PILE -> timberPile(
                world, slice, forward, cross, minOffset, maxOffset, reasons
            );
            case MATERIAL_PILE -> materialPile(
                world, taskId, slice, cross, minOffset, maxOffset, reasons
            );
            case BARREL -> singleFloorDecoration(
                world, slice, cross, minOffset, maxOffset,
                barrelAsset(taskId), reasons
            );
            case CRATE -> singleFloorDecoration(
                world, slice, cross, minOffset, maxOffset,
                "Furniture_Crude_Chest_Small", reasons
            );
        };
    }

    private static String barrelAsset(UUID taskId) {
        return (taskId.hashCode() & 3) == 0
            ? "Furniture_Ancient_Barrel"
            : "Furniture_Tavern_Barrel";
    }

    private static ResolvedTask singleFloorDecoration(
        World world,
        MineTunnelGeometry.Slice slice,
        Cardinal cross,
        int minOffset,
        int maxOffset,
        String asset,
        Map<String, Integer> reasons
    ) {
        if (asset == null) {
            increment(reasons, "ASSET_MISSING");
            return null;
        }
        boolean attemptedSide = false;
        for (int lateral : sideOffsets(minOffset, maxOffset)) {
            if (Math.abs(lateral) <= 1) {
                increment(reasons, "NO_SIDE_CLEARANCE");
                continue;
            }
            attemptedSide = true;
            BlockPosition target = at(slice.floorCenter(), cross, lateral, 0);
            BlockPosition below = new BlockPosition(target.x(), target.y() - 1, target.z());
            if (slice.navigationCoreBlocks().contains(target)) {
                increment(reasons, "NAVIGATION_CORE_CONFLICT");
                continue;
            }
            if (!isEmpty(world, target)) {
                increment(reasons, "TARGET_OCCUPIED");
                continue;
            }
            if (isEmpty(world, below)) {
                increment(reasons, "MISSING_FLOOR_SUPPORT");
                continue;
            }
            Cardinal inward = lateral < 0 ? cross : cross.opposite();
            return new ResolvedTask(
                workTarget(slice.floorCenter()),
                List.of(new PlacementStep(target, asset, yawRotation(inward), below, false))
            );
        }
        if (!attemptedSide) increment(reasons, "NO_USABLE_SIDE");
        return null;
    }

    private static ResolvedTask wallDecoration(
        World world,
        MineTunnelGeometry.Slice slice,
        Cardinal cross,
        int minOffset,
        int maxOffset,
        String asset
    ) {
        if (asset == null) return null;
        int y = slice.floorCenter().y() + Math.min(2, Math.max(1, slice.heightBlocks() - 2));
        for (int lateral : sideOffsets(minOffset, maxOffset)) {
            if (Math.abs(lateral) <= 1) continue;
            BlockPosition target = atY(at(slice.floorCenter(), cross, lateral, 0), y);
            Cardinal outward = lateral < 0 ? cross.opposite() : cross;
            BlockPosition wall = new BlockPosition(
                target.x() + outward.dx(), target.y(), target.z() + outward.dz()
            );
            if (slice.navigationCoreBlocks().contains(target)
                || !isEmpty(world, target)
                || isEmpty(world, wall)) {
                continue;
            }
            return new ResolvedTask(
                workTarget(slice.floorCenter()),
                List.of(new PlacementStep(
                    target, asset, yawRotation(outward.opposite()), wall, false
                ))
            );
        }
        return null;
    }

    private static ResolvedTask timberPile(
        World world,
        MineTunnelGeometry.Slice slice,
        Cardinal forward,
        Cardinal cross,
        int minOffset,
        int maxOffset,
        Map<String, Integer> reasons
    ) {
        for (int lateral : sideOffsets(minOffset, maxOffset)) {
            if (Math.abs(lateral) <= 1) {
                increment(reasons, "NO_SIDE_CLEARANCE");
                continue;
            }
            BlockPosition first = at(slice.floorCenter(), cross, lateral, 0);
            BlockPosition second = at(first, forward, 1, 0);
            BlockPosition belowFirst = new BlockPosition(first.x(), first.y() - 1, first.z());
            BlockPosition belowSecond = new BlockPosition(second.x(), second.y() - 1, second.z());
            if (slice.navigationCoreBlocks().contains(first)
                || slice.navigationCoreBlocks().contains(second)) {
                increment(reasons, "NAVIGATION_CORE_CONFLICT");
                continue;
            }
            if (!isEmpty(world, first) || !isEmpty(world, second)) {
                increment(reasons, "TARGET_OCCUPIED");
                continue;
            }
            if (isEmpty(world, belowFirst) || isEmpty(world, belowSecond)) {
                increment(reasons, "MISSING_FLOOR_SUPPORT");
                continue;
            }
            RotationTuple rotation = trunkRotation(forward);
            return new ResolvedTask(
                workTarget(slice.floorCenter()),
                List.of(
                    new PlacementStep(first, FIR_TRUNK, rotation, belowFirst, true),
                    new PlacementStep(second, FIR_TRUNK, rotation, belowSecond, true)
                )
            );
        }
        return null;
    }

    private static ResolvedTask materialPile(
        World world,
        UUID taskId,
        MineTunnelGeometry.Slice slice,
        Cardinal cross,
        int minOffset,
        int maxOffset,
        Map<String, Integer> reasons
    ) {
        String[] oreBlocks = new String[]{
            "Ore_Iron_Stone",
            "Ore_Copper_Stone",
            "Ore_Gold_Stone"
        };
        String material = oreBlocks[Math.floorMod(taskId.hashCode(), oreBlocks.length)];
        if (MineBlockPlacement.resolveAsset(new String[]{material}, material.toLowerCase()) == null) {
            increment(reasons, "ASSET_MISSING");
            return null;
        }
        for (int lateral : sideOffsets(minOffset, maxOffset)) {
            if (Math.abs(lateral) <= 1) {
                increment(reasons, "NO_SIDE_CLEARANCE");
                continue;
            }
            BlockPosition first = at(slice.floorCenter(), cross, lateral, 0);
            BlockPosition below = new BlockPosition(first.x(), first.y() - 1, first.z());
            if (slice.navigationCoreBlocks().contains(first)) {
                increment(reasons, "NAVIGATION_CORE_CONFLICT");
                continue;
            }
            if (!isEmpty(world, first)) {
                increment(reasons, "TARGET_OCCUPIED");
                continue;
            }
            if (isEmpty(world, below)) {
                increment(reasons, "MISSING_FLOOR_SUPPORT");
                continue;
            }
            return new ResolvedTask(
                workTarget(slice.floorCenter()),
                List.of(new PlacementStep(first, material, RotationTuple.NONE, below, false))
            );
        }
        return null;
    }

    private static ResolvedTask hangingDecoration(
        World world,
        MineTunnelGeometry.Slice slice,
        Cardinal cross,
        int minOffset,
        int maxOffset,
        boolean withLantern,
        Map<String, Integer> reasons
    ) {
        String chain = MineBlockPlacement.resolveAsset(
            new String[]{"Deco_Iron_Chain_Small"}, "deco", "iron", "chain", "small"
        );
        String lantern = withLantern
            ? MineBlockPlacement.resolveAsset(
                new String[]{"Deco_Lantern"}, "deco", "lantern"
            )
            : null;
        if (chain == null) {
            increment(reasons, "CHAIN_ASSET_MISSING");
            return null;
        }
        if (withLantern && lantern == null) {
            increment(reasons, "LANTERN_ASSET_MISSING");
            return null;
        }

        int chainY = slice.floorCenter().y() + slice.heightBlocks() - 1;
        for (int lateral : sideOffsets(minOffset + 1, maxOffset - 1)) {
            if (Math.abs(lateral) <= 1) {
                increment(reasons, "NO_SIDE_CLEARANCE");
                continue;
            }
            BlockPosition chainPos = atY(at(slice.floorCenter(), cross, lateral, 0), chainY);
            BlockPosition ceiling = new BlockPosition(chainPos.x(), chainPos.y() + 1, chainPos.z());
            BlockPosition lanternPos = new BlockPosition(
                chainPos.x(), chainPos.y() - 1, chainPos.z()
            );
            if (slice.navigationCoreBlocks().contains(chainPos)) {
                increment(reasons, "NAVIGATION_CORE_CONFLICT");
                continue;
            }
            if (!isEmpty(world, chainPos)) {
                increment(reasons, "TARGET_OCCUPIED");
                continue;
            }
            if (isEmpty(world, ceiling)) {
                increment(reasons, "MISSING_CEILING_SUPPORT");
                continue;
            }
            if (withLantern && !isEmpty(world, lanternPos)) {
                increment(reasons, "LANTERN_TARGET_OCCUPIED");
                continue;
            }
            if (withLantern && slice.navigationCoreBlocks().contains(lanternPos)) {
                increment(reasons, "LANTERN_NAVIGATION_CORE_CONFLICT");
                continue;
            }

            List<PlacementStep> placements = new ArrayList<>();
            placements.add(new PlacementStep(
                chainPos, chain, RotationTuple.NONE, ceiling, false
            ));
            if (withLantern) {
                placements.add(new PlacementStep(
                    lanternPos, lantern, RotationTuple.NONE, chainPos, false
                ));
            }
            return new ResolvedTask(workTarget(slice.floorCenter()), List.copyOf(placements));
        }
        return null;
    }

    private static int[] sideOffsets(int minOffset, int maxOffset) {
        if (minOffset == maxOffset) return new int[]{minOffset};
        return new int[]{minOffset, maxOffset};
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

    private static List<Integer> candidateSliceOrder(
        MineInfrastructureTask task,
        int sliceCount
    ) {
        if (task.mandatory()) {
            int index = task.startSliceIndex();
            return index >= 0 && index < sliceCount ? List.of(index) : List.of();
        }
        return MineObstaclePolicy.fallbackSliceOrder(task.startSliceIndex(), sliceCount);
    }

    private static boolean placementsAreSafe(
        World world, MineTunnelGeometry geometry, ResolvedTask resolved
    ) {
        for (PlacementStep placement : resolved.placements()) {
            BlockPosition position = placement.position();
            // A support or lantern must never block the reserved walking corridor.
            for (MineTunnelGeometry.Slice slice : geometry.slices()) {
                if (slice.navigationCoreBlocks().contains(position)) return false;
            }
            // Avoid creating work that would overwrite unexcavated stone or
            // any existing player block. Already-installed matching pieces are fine.
            if (!isEmpty(world, position)) {
                var chunk = world.getChunkIfLoaded(
                    com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(
                        position.x(), position.z()
                    )
                );
                var existing = chunk == null ? null
                    : chunk.getBlockType(position.x(), position.y(), position.z());
                if (existing == null || !placement.blockId().equals(existing.getId())) {
                    return false;
                }
            }
        }
        return true;
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

    private static void increment(Map<String, Integer> reasons, String reason) {
        reasons.merge(reason, 1, Integer::sum);
    }

    public record DecorationResolution(
        ResolvedTask resolvedTask,
        Map<String, Integer> reasons,
        List<Integer> triedSlices
    ) {
        public DecorationResolution {
            reasons = Map.copyOf(reasons == null ? Map.of() : reasons);
            triedSlices = List.copyOf(triedSlices == null ? List.of() : triedSlices);
        }
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
