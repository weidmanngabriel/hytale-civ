package dev.civilizations.core;

import java.util.Set;
import java.util.UUID;

/** Persistent semantic room/chamber attached to one logical tunnel. */
public record MineRoom(
    UUID id,
    UUID tunnelId,
    Type type,
    BlockPosition position,
    MineHeading outwardHeading,
    int attachmentSliceIndex,
    State state,
    int excavationWorkUnitIndex,
    Set<Integer> completedBuildSections
) {
    public MineRoom {
        if (id == null || tunnelId == null || type == null || position == null
            || outwardHeading == null || state == null || completedBuildSections == null) {
            throw new IllegalArgumentException("Mine room fields must not be null.");
        }
        if (attachmentSliceIndex < 0 || excavationWorkUnitIndex < 0) {
            throw new IllegalArgumentException("Mine room progress must be non-negative.");
        }
        if (!cardinal(outwardHeading)) {
            throw new IllegalArgumentException("Mine room prefabs currently require a cardinal outward heading.");
        }
        completedBuildSections = Set.copyOf(completedBuildSections);
        if (completedBuildSections.stream().anyMatch(section -> section == null || section < 0)) {
            throw new IllegalArgumentException("Mine room build sections must be non-negative.");
        }
    }

    /** Compatibility constructor for older network fixtures and persistence records. */
    public MineRoom(UUID id, UUID tunnelId, Type type, BlockPosition position) {
        this(id, tunnelId, type, position, MineHeading.EAST, 0, State.PLANNED, 0, Set.of());
    }

    public MineRoom withState(State nextState) {
        return new MineRoom(
            id, tunnelId, type, position, outwardHeading, attachmentSliceIndex,
            nextState, excavationWorkUnitIndex, completedBuildSections
        );
    }

    public MineRoom withExcavationProgress(int nextWorkUnitIndex, State nextState) {
        return new MineRoom(
            id, tunnelId, type, position, outwardHeading, attachmentSliceIndex,
            nextState, nextWorkUnitIndex, completedBuildSections
        );
    }

    public MineRoom withBuildSectionCompleted(int sectionIndex, State nextState) {
        java.util.LinkedHashSet<Integer> completed = new java.util.LinkedHashSet<>(completedBuildSections);
        completed.add(sectionIndex);
        return new MineRoom(
            id, tunnelId, type, position, outwardHeading, attachmentSliceIndex,
            nextState, excavationWorkUnitIndex, completed
        );
    }

    public boolean terminal() {
        return state == State.BUILT;
    }

    public static boolean cardinal(MineHeading heading) {
        return heading == MineHeading.NORTH || heading == MineHeading.EAST
            || heading == MineHeading.SOUTH || heading == MineHeading.WEST;
    }

    public enum State {
        PLANNED,
        EXCAVATING,
        READY_TO_BUILD,
        BUILT
    }

    public enum Type {
        REST_ACCOMMODATION,
        MATERIAL_STORAGE,
        TOOL_WORKSHOP,
        ORE_COLLECTION,
        LARGE_NATURAL_CHAMBER,
        SMALL_NICHE,
        SUPPORT_SUPPLY,
        WATER_DRAINAGE,
        LARGE_WORK_HALL
    }
}
