package dev.civilizations.core;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Hytale-independent Layer-2 planner for mine tunnel centerlines and form phases. */
public final class MinePathPlanner {

    public static final int FOOTPRINT_SIZE_BLOCKS = 500;
    public static final double FOOTPRINT_HALF_EXTENT_BLOCKS = FOOTPRINT_SIZE_BLOCKS / 2.0;

    private static final int MAIN_MIN_PHASE_LENGTH = 10;
    private static final int MAIN_MAX_PHASE_LENGTH = 18;
    private static final int BRANCH_MIN_PHASE_LENGTH = 6;
    private static final int BRANCH_MAX_PHASE_LENGTH = 12;

    private static final int MAIN_MIN_SIZE = 6;
    private static final int MAIN_MAX_SIZE = 8;
    private static final int BRANCH_MIN_SIZE = 3;
    private static final int BRANCH_MAX_SIZE = 5;

    private MinePathPlanner() {
    }

    public static MineTunnelPath plan(
        MineTunnel.Kind tunnelKind,
        BlockPosition origin,
        MineHeading initialHeading,
        int forwardBlocks,
        long seed
    ) {
        return plan(tunnelKind, origin, origin, initialHeading, forwardBlocks, seed);
    }

    public static MineTunnelPath plan(
        MineTunnel.Kind tunnelKind,
        BlockPosition origin,
        BlockPosition footprintCenter,
        MineHeading initialHeading,
        int forwardBlocks,
        long seed
    ) {
        if (tunnelKind == null || origin == null || footprintCenter == null || initialHeading == null) {
            throw new IllegalArgumentException("Mine path planning inputs must not be null.");
        }
        if (forwardBlocks <= 0) {
            throw new IllegalArgumentException("Mine path length must be positive.");
        }

        SplittableRandom random = new SplittableRandom(seed);
        List<MinePathPoint> points = new ArrayList<>(forwardBlocks + 1);
        List<MineFormPhase> phases = new ArrayList<>();

        int minSize = tunnelKind == MineTunnel.Kind.MAIN ? MAIN_MIN_SIZE : BRANCH_MIN_SIZE;
        int maxSize = tunnelKind == MineTunnel.Kind.MAIN ? MAIN_MAX_SIZE : BRANCH_MAX_SIZE;
        int currentWidth = random.nextInt(minSize, maxSize + 1);
        int currentHeight = random.nextInt(minSize, maxSize + 1);
        double currentLateralOffset = 0.0;
        MineHeading currentHeading = initialHeading;
        double currentAngle = initialHeading.angleDegrees();

        double baseX = origin.x();
        double baseY = origin.y();
        double baseZ = origin.z();
        double actualX = baseX;
        double actualZ = baseZ;

        points.add(new MinePathPoint(
            0,
            actualX,
            baseY,
            actualZ,
            currentWidth,
            currentHeight,
            currentHeading,
            currentAngle,
            0
        ));

        int generatedBlocks = 0;
        int phaseIndex = 0;
        while (generatedBlocks < forwardBlocks) {
            int phaseLength = Math.min(
                randomPhaseLength(tunnelKind, random),
                forwardBlocks - generatedBlocks
            );

            MineHeading targetHeading = chooseHeading(
                tunnelKind,
                currentHeading,
                actualX,
                actualZ,
                footprintCenter.x(),
                footprintCenter.z(),
                random
            );
            int targetWidth = nextSize(currentWidth, minSize, maxSize, random);
            int targetHeight = nextSize(currentHeight, minSize, maxSize, random);
            double targetLateralOffset = nextLateralOffset(currentLateralOffset, random);
            int verticalDelta = chooseVerticalDelta(tunnelKind, random);

            MineFormPhase phase = new MineFormPhase(
                phaseIndex,
                generatedBlocks,
                phaseLength,
                currentHeading,
                targetHeading,
                currentWidth,
                targetWidth,
                currentHeight,
                targetHeight,
                currentLateralOffset,
                targetLateralOffset,
                verticalDelta
            );
            phases.add(phase);

            double startAngle = currentAngle;
            double headingDelta = MineHeading.shortestSignedAngleDegrees(currentHeading, targetHeading);
            double phaseStartY = baseY;
            double previousOffset = currentLateralOffset;

            for (int step = 1; step <= phaseLength; step++) {
                double t = (double) step / phaseLength;
                double tangentAngle = startAngle + headingDelta * t;
                double radians = Math.toRadians(tangentAngle);
                double forwardX = Math.cos(radians);
                double forwardZ = Math.sin(radians);

                baseX += forwardX;
                baseZ += forwardZ;

                double lateralOffset = lerp(currentLateralOffset, targetLateralOffset, t);
                double offsetDelta = lateralOffset - previousOffset;
                actualX = baseX - Math.sin(radians) * lateralOffset;
                actualZ = baseZ + Math.cos(radians) * lateralOffset;
                previousOffset += offsetDelta;

                double y = phaseStartY + verticalDelta * t;
                double width = lerp(currentWidth, targetWidth, t);
                double height = lerp(currentHeight, targetHeight, t);

                points.add(new MinePathPoint(
                    generatedBlocks + step,
                    actualX,
                    y,
                    actualZ,
                    width,
                    height,
                    targetHeading,
                    normalizeAngle(tangentAngle),
                    phaseIndex
                ));
            }

            generatedBlocks += phaseLength;
            baseY += verticalDelta;
            currentHeading = targetHeading;
            currentAngle = normalizeAngle(startAngle + headingDelta);
            currentWidth = targetWidth;
            currentHeight = targetHeight;
            currentLateralOffset = targetLateralOffset;
            phaseIndex++;
        }

        return new MineTunnelPath(tunnelKind, seed, points, phases);
    }

    static double headingWeight(
        MineTunnel.Kind tunnelKind,
        MineHeading currentHeading,
        MineHeading candidate,
        double x,
        double z,
        double centerX,
        double centerZ
    ) {
        if (candidate != currentHeading && candidate != currentHeading.left45() && candidate != currentHeading.right45()) {
            return 0.0;
        }

        double baseWeight;
        if (candidate == currentHeading) {
            baseWeight = tunnelKind == MineTunnel.Kind.MAIN ? 78.0 : 50.0;
        } else {
            baseWeight = tunnelKind == MineTunnel.Kind.MAIN ? 11.0 : 25.0;
        }

        double dx = x - centerX;
        double dz = z - centerZ;
        double squareDistance = Math.max(Math.abs(dx), Math.abs(dz));
        double normalized = squareDistance / FOOTPRINT_HALF_EXTENT_BLOCKS;
        if (normalized <= 0.70 || squareDistance < 0.000001) {
            return baseWeight;
        }

        double pressure = Math.min(1.0, (normalized - 0.70) / 0.30);
        double distance = Math.hypot(dx, dz);
        double radialX = dx / distance;
        double radialZ = dz / distance;
        double outwardDot = radialX * candidate.unitX() + radialZ * candidate.unitZ();

        if (outwardDot > 0.0) {
            return baseWeight * Math.max(0.05, 1.0 - pressure * 0.95 * outwardDot);
        }
        if (outwardDot < 0.0) {
            return baseWeight * (1.0 + pressure * 1.5 * -outwardDot);
        }
        return baseWeight;
    }

    private static MineHeading chooseHeading(
        MineTunnel.Kind tunnelKind,
        MineHeading currentHeading,
        double x,
        double z,
        double centerX,
        double centerZ,
        SplittableRandom random
    ) {
        MineHeading[] candidates = {
            currentHeading.left45(),
            currentHeading,
            currentHeading.right45()
        };
        double[] weights = new double[candidates.length];
        double total = 0.0;
        for (int i = 0; i < candidates.length; i++) {
            weights[i] = headingWeight(tunnelKind, currentHeading, candidates[i], x, z, centerX, centerZ);
            total += weights[i];
        }

        double roll = random.nextDouble(total);
        for (int i = 0; i < candidates.length; i++) {
            roll -= weights[i];
            if (roll <= 0.0) return candidates[i];
        }
        return currentHeading;
    }

    private static int randomPhaseLength(MineTunnel.Kind tunnelKind, SplittableRandom random) {
        int min = tunnelKind == MineTunnel.Kind.MAIN ? MAIN_MIN_PHASE_LENGTH : BRANCH_MIN_PHASE_LENGTH;
        int max = tunnelKind == MineTunnel.Kind.MAIN ? MAIN_MAX_PHASE_LENGTH : BRANCH_MAX_PHASE_LENGTH;
        return random.nextInt(min, max + 1);
    }

    private static int nextSize(int current, int min, int max, SplittableRandom random) {
        int delta = random.nextInt(-1, 2);
        return Math.max(min, Math.min(max, current + delta));
    }

    private static double nextLateralOffset(double current, SplittableRandom random) {
        double target = current + random.nextInt(-1, 2);
        return Math.max(-2.0, Math.min(2.0, target));
    }

    private static int chooseVerticalDelta(MineTunnel.Kind tunnelKind, SplittableRandom random) {
        int roll = random.nextInt(100);
        if (tunnelKind == MineTunnel.Kind.MAIN) {
            if (roll < 68) return -1;
            if (roll < 95) return 0;
            return 1;
        }
        if (roll < 35) return -1;
        if (roll < 65) return 0;
        return 1;
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private static double normalizeAngle(double angle) {
        double normalized = angle % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }
}
