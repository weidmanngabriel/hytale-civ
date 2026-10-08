package dev.civilizations.core;

/**
 * Determines when an authored infrastructure job can execute relative to the excavation front.
 * In particular, a step is placed in the LOWER slice; that slice must be excavated first.
 */
public final class MineInfrastructureAvailability {

    private MineInfrastructureAvailability() {
    }

    public static boolean isAvailable(
        MineInfrastructureTask.Type type,
        int startSliceIndex,
        int endSliceIndex,
        int currentSliceIndex,
        boolean frontComplete
    ) {
        if (type == null) throw new IllegalArgumentException("Infrastructure type is required.");
        if (type == MineInfrastructureTask.Type.BUILD_BRIDGE) {
            return !frontComplete && startSliceIndex == currentSliceIndex;
        }
        if (type == MineInfrastructureTask.Type.BUILD_STEP) {
            return frontComplete || endSliceIndex < currentSliceIndex;
        }
        return frontComplete || startSliceIndex < currentSliceIndex;
    }
}
