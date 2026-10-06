package dev.civilizations.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Concrete, Hytale-independent voxel result for one planned tunnel path.
 *
 * <p>The world remains authoritative for blocks that were actually excavated. This model is the
 * deterministic Layer-3 plan used to turn a Layer-2 centerline into excavation work.</p>
 */
public record MineTunnelGeometry(
    MineTunnel.Kind tunnelKind,
    long seed,
    List<Slice> slices,
    Set<BlockPosition> excavationBlocks,
    Set<BlockPosition> navigationCoreBlocks,
    List<StepTransition> stepTransitions
) {
    public MineTunnelGeometry {
        if (tunnelKind == null || slices == null || excavationBlocks == null
            || navigationCoreBlocks == null || stepTransitions == null) {
            throw new IllegalArgumentException("Mine tunnel geometry fields must not be null.");
        }
        slices = List.copyOf(slices);
        excavationBlocks = Set.copyOf(excavationBlocks);
        navigationCoreBlocks = Set.copyOf(navigationCoreBlocks);
        stepTransitions = List.copyOf(stepTransitions);
        if (slices.isEmpty()) {
            throw new IllegalArgumentException("Mine tunnel geometry requires at least one slice.");
        }
        if (!excavationBlocks.containsAll(navigationCoreBlocks)) {
            throw new IllegalArgumentException("Navigation core must be part of the excavated volume.");
        }
    }

    public record Slice(
        int index,
        BlockPosition floorCenter,
        int widthBlocks,
        int heightBlocks,
        Set<BlockPosition> excavationBlocks,
        Set<BlockPosition> navigationCoreBlocks
    ) {
        public Slice {
            if (index < 0 || floorCenter == null || excavationBlocks == null || navigationCoreBlocks == null) {
                throw new IllegalArgumentException("Mine excavation slice fields must be valid.");
            }
            if (widthBlocks <= 0 || heightBlocks <= 0) {
                throw new IllegalArgumentException("Mine excavation slice dimensions must be positive.");
            }
            excavationBlocks = Set.copyOf(new LinkedHashSet<>(excavationBlocks));
            navigationCoreBlocks = Set.copyOf(new LinkedHashSet<>(navigationCoreBlocks));
            if (!excavationBlocks.containsAll(navigationCoreBlocks)) {
                throw new IllegalArgumentException("Slice navigation core must be excavated.");
            }
        }
    }

    /** One one-block floor-height transition that later infrastructure can treat with stairs. */
    public record StepTransition(
        int fromSliceIndex,
        int toSliceIndex,
        BlockPosition fromFloorCenter,
        BlockPosition toFloorCenter
    ) {
        public StepTransition {
            if (fromSliceIndex < 0 || toSliceIndex <= fromSliceIndex
                || fromFloorCenter == null || toFloorCenter == null) {
                throw new IllegalArgumentException("Mine step transition fields must be valid.");
            }
            if (Math.abs(toFloorCenter.y() - fromFloorCenter.y()) != 1) {
                throw new IllegalArgumentException("Layer-3 step transitions must change exactly one block in Y.");
            }
        }
    }
}
