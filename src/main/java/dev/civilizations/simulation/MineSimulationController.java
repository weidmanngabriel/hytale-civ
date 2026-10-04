package dev.civilizations.simulation;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MineTuning;
import dev.civilizations.core.MinerJob;
import dev.civilizations.core.Profession;
import dev.civilizations.core.WorldPosition;

import java.util.Objects;

/** Executes one {@link MinerJob} against the deliberately tiny headless mine world. */
final class MineSimulationController {

    private static final double EPSILON = 1.0e-9;

    private final String minerId;
    private final MinerJob job;
    private final MineSimulationWorld world;
    private WorldPosition position;
    private WorldPosition movementTarget;
    private double blockWorkElapsed;

    MineSimulationController(String minerId, WorldPosition position, MineSegment segment) {
        if (minerId == null || minerId.isBlank()) {
            throw new IllegalArgumentException("minerId cannot be blank");
        }
        this.minerId = minerId;
        this.position = Objects.requireNonNull(position, "position");
        this.job = new MinerJob(Objects.requireNonNull(segment, "segment"));
        this.world = new MineSimulationWorld(segment);
    }

    String minerId() {
        return minerId;
    }

    void tick(double tickSeconds, double moveSpeed, SimulationMetrics metrics) {
        MinerJob.Intent intent = job.intent();
        if (intent instanceof MinerJob.MoveToFaceIntent move) {
            blockWorkElapsed = 0.0;
            WorldPosition target = faceApproachPoint(move.segment(), move.depth());
            if (advanceMovement(target, tickSeconds, moveSpeed, metrics)) {
                job.movementArrived();
            }
            return;
        }
        if (intent instanceof MinerJob.BreakBlockIntent breakBlock) {
            movementTarget = null;
            blockWorkElapsed += tickSeconds;
            double secondsPerBlock = MineTuning.secondsPerBlock();
            if (blockWorkElapsed + EPSILON >= secondsPerBlock) {
                blockWorkElapsed -= secondsPerBlock;
                world.breakBlock(breakBlock.block());
                job.blockBroken();
            }
            return;
        }
        if (intent instanceof MinerJob.PlaceSupportIntent support) {
            movementTarget = null;
            world.placeSupport(support.segment(), support.depth());
            job.supportPlaced();
            return;
        }
        movementTarget = null;
        blockWorkElapsed = 0.0;
    }

    SimulationRuntime.ResidentSnapshot residentSnapshot() {
        return new SimulationRuntime.ResidentSnapshot(
            minerId,
            Profession.MINER,
            position,
            movementTarget,
            job.state().name(),
            job.state().name(),
            false
        );
    }

    SimulationRuntime.MineSnapshot mineSnapshot() {
        MinerJob.Intent intent = job.intent();
        String intentName = intent.getClass().getSimpleName();
        BlockPosition targetBlock = intent instanceof MinerJob.BreakBlockIntent breakIntent
            ? breakIntent.block()
            : null;
        return new SimulationRuntime.MineSnapshot(
            world.snapshot(),
            job.segment(),
            job.state().name(),
            intentName,
            job.currentDepth(),
            job.segment().nextBlockIndex(),
            targetBlock,
            blockWorkElapsed
        );
    }

    private boolean advanceMovement(
        WorldPosition target,
        double tickSeconds,
        double moveSpeed,
        SimulationMetrics metrics
    ) {
        if (!target.equals(movementTarget)) {
            movementTarget = target;
            metrics.recordMovementRequest();
        }
        double dx = target.x() - position.x();
        double dy = target.y() - position.y();
        double dz = target.z() - position.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double step = moveSpeed * tickSeconds;
        if (distance <= step + EPSILON) {
            position = target;
            movementTarget = null;
            return true;
        }
        double scale = step / distance;
        position = new WorldPosition(
            position.x() + dx * scale,
            position.y() + dy * scale,
            position.z() + dz * scale
        );
        return false;
    }

    private static WorldPosition faceApproachPoint(MineSegment segment, int depth) {
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        int first = depth * faceSize;
        double x = 0.0;
        double z = 0.0;
        for (int width = 0; width < MineTuning.TUNNEL_WIDTH_BLOCKS; width++) {
            BlockPosition block = segment.blockAtIndex(first + width);
            x += block.x() + 0.5;
            z += block.z() + 0.5;
        }
        x /= MineTuning.TUNNEL_WIDTH_BLOCKS;
        z /= MineTuning.TUNNEL_WIDTH_BLOCKS;
        return new WorldPosition(
            x - segment.direction().dx() * 1.35,
            segment.start().y(),
            z - segment.direction().dz() * 1.35
        );
    }
}
