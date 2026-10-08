package dev.civilizations.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Hytale-independent planner for recurring tunnel infrastructure.
 *
 * <p>The planner decides when semantic work is due. World-sensitive shape resolution (for example
 * the exact support-post depth or a bridge landing check) is intentionally executed by the Hytale
 * adapter and reported back as success/failure.</p>
 */
public final class MineInfrastructurePlanner {

    public static final int SUPPORT_MIN_SPACING = 6;
    public static final int SUPPORT_MAX_SPACING = 10;
    public static final int SUPPORT_SEARCH_RADIUS = 3;
    public static final int MAIN_SUPPORT_MIN_OPEN_WIDTH = 4;
    public static final int BRANCH_SUPPORT_MIN_OPEN_WIDTH = 3;
    public static final int SUPPORT_MIN_OPEN_HEIGHT = 3;

    public static final int MAIN_LIGHT_MIN_SPACING = 8;
    public static final int MAIN_LIGHT_MAX_SPACING = 14;
    public static final int BRANCH_LIGHT_MIN_SPACING = 7;
    public static final int BRANCH_LIGHT_MAX_SPACING = 12;

    public static final int SUPPORT_PRIORITY = 5;
    public static final int LIGHT_PRIORITY = 5;
    public static final int DECORATION_PRIORITY = 2;
    public static final int PASSABILITY_PRIORITY = 10;

    public static final int MAIN_DECORATION_MIN_SPACING = 10;
    public static final int MAIN_DECORATION_MAX_SPACING = 18;
    public static final int BRANCH_DECORATION_MIN_SPACING = 18;
    public static final int BRANCH_DECORATION_MAX_SPACING = 30;

    private static final long SUPPORT_SEED_SALT = 0x6A09E667F3BCC909L;
    private static final long LIGHT_SEED_SALT = 0xBB67AE8584CAA73BL;
    private static final long DECORATION_SEED_SALT = 0x3C6EF372FE94F82BL;

    private MineInfrastructurePlanner() {
    }

    public static List<MineInfrastructureTask> plan(
        UUID tunnelId,
        MineTunnelGeometry geometry
    ) {
        return plan(null, tunnelId, geometry, MineDecisionSink.NONE);
    }

    public static List<MineInfrastructureTask> plan(
        UUID mineId,
        UUID tunnelId,
        MineTunnelGeometry geometry,
        MineDecisionSink decisionSink
    ) {
        if (tunnelId == null || geometry == null) {
            throw new IllegalArgumentException("infrastructure planning inputs must not be null");
        }
        decisionSink = decisionSink == null ? MineDecisionSink.NONE : decisionSink;

        List<MineInfrastructureTask> result = new ArrayList<>();
        result.addAll(planSteps(tunnelId, geometry));
        result.addAll(planSupports(mineId, tunnelId, geometry, decisionSink));
        result.addAll(planLights(tunnelId, geometry));
        result.addAll(planDecorations(mineId, tunnelId, geometry, result, decisionSink));
        result.sort(Comparator
            .comparingInt(MineInfrastructureTask::startSliceIndex)
            .thenComparing(task -> task.type().ordinal()));
        return List.copyOf(result);
    }

    public static MineInfrastructureTask bridgeTask(
        UUID tunnelId,
        int startSliceIndex,
        int endSliceIndex,
        BlockPosition anchor
    ) {
        return new MineInfrastructureTask(
            taskId(tunnelId, MineInfrastructureTask.Type.BUILD_BRIDGE,
                startSliceIndex, endSliceIndex),
            tunnelId,
            MineInfrastructureTask.Type.BUILD_BRIDGE,
            PASSABILITY_PRIORITY,
            startSliceIndex,
            endSliceIndex,
            anchor
        );
    }

    private static List<MineInfrastructureTask> planSteps(
        UUID tunnelId,
        MineTunnelGeometry geometry
    ) {
        List<MineInfrastructureTask> result = new ArrayList<>();
        for (MineTunnelGeometry.StepTransition transition : geometry.stepTransitions()) {
            result.add(new MineInfrastructureTask(
                taskId(tunnelId, MineInfrastructureTask.Type.BUILD_STEP,
                    transition.fromSliceIndex(), transition.toSliceIndex()),
                tunnelId,
                MineInfrastructureTask.Type.BUILD_STEP,
                PASSABILITY_PRIORITY,
                transition.fromSliceIndex(),
                transition.toSliceIndex(),
                transition.fromFloorCenter()
            ));
        }
        return result;
    }

    private static List<MineInfrastructureTask> planSupports(
        UUID mineId,
        UUID tunnelId,
        MineTunnelGeometry geometry,
        MineDecisionSink decisionSink
    ) {
        List<MineInfrastructureTask> result = new ArrayList<>();
        List<MineTunnelGeometry.Slice> slices = geometry.slices();
        if (slices.size() < SUPPORT_MIN_SPACING + 1) {
            if (mineId != null) {
                decisionSink.record(
                    mineId, tunnelId, MineDecisionCategory.PLANNING, "BUILD_SUPPORT_SKIPPED",
                    "reason", "TUNNEL_TOO_SHORT",
                    "sliceCount", slices.size(),
                    "minimum", SUPPORT_MIN_SPACING + 1
                );
            }
            return result;
        }

        SplittableRandom random = new SplittableRandom(geometry.seed() ^ SUPPORT_SEED_SALT);
        int previous = 0;
        while (true) {
            int spacing = random.nextInt(SUPPORT_MIN_SPACING, SUPPORT_MAX_SPACING + 1);
            int desired = previous + spacing;
            if (desired >= slices.size() - 1) {
                if (mineId != null) {
                    decisionSink.record(
                        mineId, tunnelId, MineDecisionCategory.PLANNING, "BUILD_SUPPORT_SKIPPED",
                        "reason", "END_OF_TUNNEL",
                        "previous", previous,
                        "spacing", spacing,
                        "desiredSlice", desired,
                        "sliceCount", slices.size()
                    );
                }
                break;
            }

            SupportChoice choice = chooseSupportSliceDetailed(
                slices, desired, geometry.tunnelKind()
            );
            int chosen = choice.chosenIndex();
            if (chosen < 0 || chosen <= previous) {
                if (mineId != null) {
                    decisionSink.record(
                        mineId, tunnelId, MineDecisionCategory.PLANNING, "BUILD_SUPPORT_SKIPPED",
                        "reason", chosen < 0 ? "NO_GEOMETRY_CANDIDATE" : "NOT_FORWARD_OF_PREVIOUS",
                        "previous", previous,
                        "spacing", spacing,
                        "desiredSlice", desired,
                        "searchFrom", choice.searchFrom(),
                        "searchTo", choice.searchTo(),
                        "rejectedTooSmall", choice.rejectedTooSmall()
                    );
                }
                previous = desired;
                continue;
            }

            MineTunnelGeometry.Slice slice = slices.get(chosen);
            MineInfrastructureTask task = new MineInfrastructureTask(
                taskId(tunnelId, MineInfrastructureTask.Type.BUILD_SUPPORT, chosen, chosen),
                tunnelId,
                MineInfrastructureTask.Type.BUILD_SUPPORT,
                SUPPORT_PRIORITY,
                chosen,
                chosen,
                slice.floorCenter()
            );
            result.add(task);
            if (mineId != null) {
                decisionSink.record(
                    mineId, task.id(), MineDecisionCategory.PLANNING, "BUILD_SUPPORT_PLANNED",
                    "tunnel", tunnelId,
                    "tunnelKind", geometry.tunnelKind(),
                    "previous", previous,
                    "spacing", spacing,
                    "desiredSlice", desired,
                    "chosenSlice", chosen,
                    "width", slice.widthBlocks(),
                    "height", slice.heightBlocks(),
                    "turnPenalty", choice.turnPenalty(),
                    "distanceFromDesired", Math.abs(chosen - desired)
                );
            }
            previous = chosen;
        }
        return result;
    }

    static int chooseSupportSlice(
        List<MineTunnelGeometry.Slice> slices,
        int desired,
        MineTunnel.Kind tunnelKind
    ) {
        return chooseSupportSliceDetailed(slices, desired, tunnelKind).chosenIndex();
    }

    private static SupportChoice chooseSupportSliceDetailed(
        List<MineTunnelGeometry.Slice> slices,
        int desired,
        MineTunnel.Kind tunnelKind
    ) {
        int from = Math.max(1, desired - SUPPORT_SEARCH_RADIUS);
        int to = Math.min(slices.size() - 2, desired + SUPPORT_SEARCH_RADIUS);
        int best = -1;
        int bestTurnPenalty = Integer.MAX_VALUE;
        int bestDistance = Integer.MAX_VALUE;
        int bestArea = -1;
        int rejectedTooSmall = 0;

        for (int index = from; index <= to; index++) {
            MineTunnelGeometry.Slice slice = slices.get(index);
            int minimumOpenWidth = tunnelKind == MineTunnel.Kind.MAIN
                ? MAIN_SUPPORT_MIN_OPEN_WIDTH
                : BRANCH_SUPPORT_MIN_OPEN_WIDTH;
            // Two side posts consume two cells. Main keeps four clear; branches keep three.
            if (slice.widthBlocks() < minimumOpenWidth + 2
                || slice.heightBlocks() < SUPPORT_MIN_OPEN_HEIGHT + 1) {
                rejectedTooSmall++;
                continue;
            }

            int turnPenalty = localTurnPenalty(slices, index);
            int distance = Math.abs(index - desired);
            int area = slice.widthBlocks() * slice.heightBlocks();
            if (turnPenalty < bestTurnPenalty
                || (turnPenalty == bestTurnPenalty && distance < bestDistance)
                || (turnPenalty == bestTurnPenalty && distance == bestDistance && area > bestArea)) {
                best = index;
                bestTurnPenalty = turnPenalty;
                bestDistance = distance;
                bestArea = area;
            }
        }
        return new SupportChoice(
            best,
            from,
            to,
            rejectedTooSmall,
            best < 0 ? -1 : bestTurnPenalty
        );
    }

    private record SupportChoice(
        int chosenIndex,
        int searchFrom,
        int searchTo,
        int rejectedTooSmall,
        int turnPenalty
    ) {
    }

    private static int localTurnPenalty(List<MineTunnelGeometry.Slice> slices, int index) {
        BlockPosition before = slices.get(index - 1).floorCenter();
        BlockPosition current = slices.get(index).floorCenter();
        BlockPosition after = slices.get(index + 1).floorCenter();
        int ax = Integer.compare(current.x() - before.x(), 0);
        int az = Integer.compare(current.z() - before.z(), 0);
        int bx = Integer.compare(after.x() - current.x(), 0);
        int bz = Integer.compare(after.z() - current.z(), 0);
        if (ax == bx && az == bz) return 0;
        if ((ax == 0 && az == 0) || (bx == 0 && bz == 0)) return 1;
        return 2;
    }

    private static List<MineInfrastructureTask> planLights(
        UUID tunnelId,
        MineTunnelGeometry geometry
    ) {
        List<MineInfrastructureTask> result = new ArrayList<>();
        List<MineTunnelGeometry.Slice> slices = geometry.slices();
        int min = geometry.tunnelKind() == MineTunnel.Kind.MAIN
            ? MAIN_LIGHT_MIN_SPACING : BRANCH_LIGHT_MIN_SPACING;
        int max = geometry.tunnelKind() == MineTunnel.Kind.MAIN
            ? MAIN_LIGHT_MAX_SPACING : BRANCH_LIGHT_MAX_SPACING;
        SplittableRandom random = new SplittableRandom(geometry.seed() ^ LIGHT_SEED_SALT);

        Set<Integer> stepSlices = new HashSet<>();
        for (MineTunnelGeometry.StepTransition transition : geometry.stepTransitions()) {
            stepSlices.add(transition.fromSliceIndex());
            stepSlices.add(transition.toSliceIndex());
        }

        int previous = 0;
        while (true) {
            int desired = previous + random.nextInt(min, max + 1);
            if (desired >= slices.size() - 1) break;
            int chosen = nearestOrdinarySlice(slices, desired, stepSlices);
            if (chosen <= previous) {
                previous = desired;
                continue;
            }
            result.add(new MineInfrastructureTask(
                taskId(tunnelId, MineInfrastructureTask.Type.PLACE_LIGHT, chosen, chosen),
                tunnelId,
                MineInfrastructureTask.Type.PLACE_LIGHT,
                LIGHT_PRIORITY,
                chosen,
                chosen,
                slices.get(chosen).floorCenter()
            ));
            previous = chosen;
        }
        return result;
    }

    private static List<MineInfrastructureTask> planDecorations(
        UUID mineId,
        UUID tunnelId,
        MineTunnelGeometry geometry,
        List<MineInfrastructureTask> existingTasks,
        MineDecisionSink decisionSink
    ) {
        List<MineInfrastructureTask> result = new ArrayList<>();
        List<MineTunnelGeometry.Slice> slices = geometry.slices();
        int min = geometry.tunnelKind() == MineTunnel.Kind.MAIN
            ? MAIN_DECORATION_MIN_SPACING : BRANCH_DECORATION_MIN_SPACING;
        int max = geometry.tunnelKind() == MineTunnel.Kind.MAIN
            ? MAIN_DECORATION_MAX_SPACING : BRANCH_DECORATION_MAX_SPACING;
        if (slices.size() < min + 1) {
            if (mineId != null) {
                decisionSink.record(
                    mineId, tunnelId, MineDecisionCategory.PLANNING, "DECORATION_SKIPPED",
                    "reason", "TUNNEL_TOO_SHORT",
                    "tunnelKind", geometry.tunnelKind(),
                    "sliceCount", slices.size(),
                    "minimum", min + 1
                );
            }
            return result;
        }

        Set<Integer> excluded = new HashSet<>();
        for (MineInfrastructureTask task : existingTasks) {
            for (int index = Math.max(0, task.startSliceIndex() - 1);
                 index <= Math.min(slices.size() - 1, task.endSliceIndex() + 1);
                 index++) {
                excluded.add(index);
            }
        }

        SplittableRandom random = new SplittableRandom(geometry.seed() ^ DECORATION_SEED_SALT);
        int previous = 0;
        while (true) {
            int spacing = random.nextInt(min, max + 1);
            int desired = previous + spacing;
            if (desired >= slices.size() - 1) {
                if (mineId != null) {
                    decisionSink.record(
                        mineId, tunnelId, MineDecisionCategory.PLANNING, "DECORATION_SKIPPED",
                        "reason", "END_OF_TUNNEL",
                        "previous", previous,
                        "spacing", spacing,
                        "desiredSlice", desired,
                        "sliceCount", slices.size()
                    );
                }
                break;
            }

            int chosen = nearestDecorationSlice(slices.size(), desired, excluded);
            if (chosen <= previous) {
                if (mineId != null) {
                    decisionSink.record(
                        mineId, tunnelId, MineDecisionCategory.PLANNING, "DECORATION_SKIPPED",
                        "reason", chosen < 0 ? "NO_FREE_SLICE_NEAR_DESIRED" : "NOT_FORWARD_OF_PREVIOUS",
                        "previous", previous,
                        "spacing", spacing,
                        "desiredSlice", desired,
                        "searchRadius", 3,
                        "excludedSlices", excluded.size()
                    );
                }
                previous = desired;
                continue;
            }

            DecorationChoice choice = chooseDecorationKindDetailed(
                geometry.tunnelKind(), random
            );
            MineInfrastructureTask task = new MineInfrastructureTask(
                taskId(tunnelId, MineInfrastructureTask.Type.PLACE_DECORATION, chosen, chosen),
                tunnelId,
                MineInfrastructureTask.Type.PLACE_DECORATION,
                DECORATION_PRIORITY,
                chosen,
                chosen,
                slices.get(chosen).floorCenter(),
                choice.kind()
            );
            result.add(task);
            if (mineId != null) {
                decisionSink.record(
                    mineId, task.id(), MineDecisionCategory.PLANNING, "DECORATION_PLANNED",
                    "tunnel", tunnelId,
                    "tunnelKind", geometry.tunnelKind(),
                    "kind", choice.kind(),
                    "roll", choice.roll(),
                    "spacing", spacing,
                    "desiredSlice", desired,
                    "chosenSlice", chosen
                );
            }
            excluded.add(chosen);
            previous = chosen;
        }
        return result;
    }

    private static int nearestDecorationSlice(int sliceCount, int desired, Set<Integer> excluded) {
        for (int distance = 0; distance <= 3; distance++) {
            int before = desired - distance;
            if (before > 0 && before < sliceCount - 1 && !excluded.contains(before)) return before;
            int after = desired + distance;
            if (after > 0 && after < sliceCount - 1 && !excluded.contains(after)) return after;
        }
        return -1;
    }

    private static MineInfrastructureTask.DecorationKind chooseDecorationKind(
        MineTunnel.Kind tunnelKind,
        SplittableRandom random
    ) {
        return chooseDecorationKindDetailed(tunnelKind, random).kind();
    }

    private static DecorationChoice chooseDecorationKindDetailed(
        MineTunnel.Kind tunnelKind,
        SplittableRandom random
    ) {
        int roll = random.nextInt(100);
        MineInfrastructureTask.DecorationKind kind;
        if (tunnelKind == MineTunnel.Kind.BRANCH) {
            if (roll < 35) kind = MineInfrastructureTask.DecorationKind.CRATE;
            else if (roll < 75) kind = MineInfrastructureTask.DecorationKind.TIMBER_PILE;
            else kind = MineInfrastructureTask.DecorationKind.MATERIAL_PILE;
        } else if (roll < 20) kind = MineInfrastructureTask.DecorationKind.BARREL;
        else if (roll < 40) kind = MineInfrastructureTask.DecorationKind.CRATE;
        else if (roll < 60) kind = MineInfrastructureTask.DecorationKind.TIMBER_PILE;
        else if (roll < 78) kind = MineInfrastructureTask.DecorationKind.MATERIAL_PILE;
        else if (roll < 90) kind = MineInfrastructureTask.DecorationKind.HANGING_CHAIN;
        else kind = MineInfrastructureTask.DecorationKind.HANGING_LANTERN;
        return new DecorationChoice(kind, roll);
    }

    private record DecorationChoice(
        MineInfrastructureTask.DecorationKind kind,
        int roll
    ) {
    }

    private static int nearestOrdinarySlice(
        List<MineTunnelGeometry.Slice> slices,
        int desired,
        Set<Integer> excluded
    ) {
        for (int distance = 0; distance <= 3; distance++) {
            int before = desired - distance;
            if (before > 0 && before < slices.size() - 1 && !excluded.contains(before)) {
                return before;
            }
            int after = desired + distance;
            if (after > 0 && after < slices.size() - 1 && !excluded.contains(after)) {
                return after;
            }
        }
        return desired;
    }

    public static UUID taskId(
        UUID tunnelId,
        MineInfrastructureTask.Type type,
        int startSliceIndex,
        int endSliceIndex
    ) {
        String value = "civ-mine-infrastructure:" + tunnelId + ":" + type + ":"
            + startSliceIndex + ":" + endSliceIndex;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
