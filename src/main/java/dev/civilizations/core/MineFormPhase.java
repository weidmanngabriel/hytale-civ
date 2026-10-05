package dev.civilizations.core;

/**
 * One coherent planning phase of a tunnel centerline.
 *
 * <p>Widths and heights are continuous planning values. Layer 3 is responsible for turning
 * them into concrete voxel volumes.</p>
 */
public record MineFormPhase(
    int index,
    int startPointIndex,
    int lengthBlocks,
    MineHeading startHeading,
    MineHeading targetHeading,
    double startWidth,
    double targetWidth,
    double startHeight,
    double targetHeight,
    double startLateralOffset,
    double targetLateralOffset,
    int verticalDeltaBlocks
) {
    public MineFormPhase {
        if (index < 0 || startPointIndex < 0 || lengthBlocks <= 0) {
            throw new IllegalArgumentException("Mine form phase indices and length must be valid.");
        }
        if (startHeading == null || targetHeading == null) {
            throw new IllegalArgumentException("Mine form phase headings must not be null.");
        }
        if (startWidth <= 0.0 || targetWidth <= 0.0 || startHeight <= 0.0 || targetHeight <= 0.0) {
            throw new IllegalArgumentException("Mine form phase dimensions must be positive.");
        }
        if (Math.abs(targetLateralOffset - startLateralOffset) > 1.000001) {
            throw new IllegalArgumentException("A form phase may drift laterally by at most one block.");
        }
        if (verticalDeltaBlocks < -1 || verticalDeltaBlocks > 1) {
            throw new IllegalArgumentException("A form phase may change elevation by at most one block.");
        }
    }
}
