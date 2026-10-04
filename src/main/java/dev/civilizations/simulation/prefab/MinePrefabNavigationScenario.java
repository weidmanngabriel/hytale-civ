package dev.civilizations.simulation.prefab;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingOrientation;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MinerJob;
import dev.civilizations.simulation.MineSimulationWorld;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Interactive mine fixture joining the real Mine_01 prefab to the real Core MinerJob. */
public final class MinePrefabNavigationScenario {

    public static final Path PREFAB_PATH = Path.of(
        "asset-pack", "Server", "Prefabs", "Civilizations", "Mine", "Mine_01.prefab.json"
    );
    public static final Path SUPPORT_PREFAB_PATH = Path.of(
        "asset-pack", "Server", "Prefabs", "Civilizations", "Mine", "Mine_Support_01.prefab.json"
    );

    public static final double WORK_REACH_BLOCKS = 4.0;

    private static final UUID SEGMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID MINE_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final int MOUNTAIN_SIDE_PADDING = 7;
    private static final int MOUNTAIN_FORWARD_EXTENSION = 14;
    private static final int MOUNTAIN_DOWN_EXTENSION = 4;
    private static final int MOUNTAIN_UP_EXTENSION = 8;

    private final PrefabSimulationModel model;
    private final PrefabSimulationModel supportPrefab;
    private final BlockPosition workplace;
    private final BlockPosition connector;
    private final List<BlockPosition> pathToConnector;
    private final MineSimulationWorld world;
    private final MinerJob job;
    private final MineDirection simulationDirection;
    private final BuildingOrientation orientation;
    private int pathIndex;
    private Phase phase = Phase.NAVIGATING_TO_CONNECTOR;
    private BlockPosition workerPosition;
    private BlockPosition lastAction;

    private MinePrefabNavigationScenario(
        PrefabSimulationModel model,
        PrefabSimulationModel supportPrefab,
        BlockPosition workplace,
        BlockPosition connector,
        List<BlockPosition> pathToConnector,
        MineSimulationWorld world,
        MinerJob job,
        MineDirection simulationDirection,
        BuildingOrientation orientation
    ) {
        this.model = model;
        this.supportPrefab = supportPrefab;
        this.workplace = workplace;
        this.connector = connector;
        this.pathToConnector = List.copyOf(pathToConnector);
        this.world = world;
        this.job = job;
        this.simulationDirection = simulationDirection;
        this.orientation = orientation;
        this.workerPosition = this.pathToConnector.get(0);
    }

    public static MinePrefabNavigationScenario create() {
        return create(BuildingOrientation.NORTH);
    }

    public static MinePrefabNavigationScenario create(BuildingOrientation orientation) {
        try {
            PrefabSimulationLoader loader = new PrefabSimulationLoader();
            PrefabSimulationModel authoredModel = loader.load(PREFAB_PATH);
            PrefabSimulationModel authoredSupport = loader.load(SUPPORT_PREFAB_PATH);

            BlockPosition authoredConnector = markerTarget(
                authoredModel,
                authoredModel.requireMarker("mine_tunnel_connector")
            );
            SegmentCandidate authoredSegment = chooseTunnelSegment(authoredModel, authoredConnector);

            PrefabSimulationModel model = PrefabSimulationTransform.rotate(authoredModel, orientation);
            PrefabSimulationModel support = PrefabSimulationTransform.rotate(authoredSupport, orientation);
            BlockPosition workplace = markerTarget(model, model.requireMarker("workplace_access"));
            BlockPosition connector = markerTarget(model, model.requireMarker("mine_tunnel_connector"));
            List<BlockPosition> path = new PrefabAStarPathfinder().findPath(model, workplace, connector);
            if (path.isEmpty()) {
                throw new IllegalStateException("Mine tunnel connector is not reachable from workplace");
            }

            MineDirection direction = orientation.rotate(authoredSegment.direction());
            MineSegment segment = MineSegment.reserved(
                SEGMENT_ID,
                MINE_ID,
                null,
                configuredTunnelStart(connector, direction),
                direction
            );
            assertNoPrefabOverlap(model, segment);

            MineSimulationWorld world = new MineSimulationWorld(segment, mountainBounds(segment, direction));
            return new MinePrefabNavigationScenario(
                model,
                support,
                workplace,
                connector,
                path,
                world,
                new MinerJob(segment),
                direction,
                orientation
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load mine prefab for simulation", exception);
        }
    }

    /** Executes one visible semantic step: one A* cell, MinerJob move intent, block, or support. */
    public boolean step() {
        if (phase == Phase.COMPLETE) return false;
        if (phase == Phase.NAVIGATING_TO_CONNECTOR) {
            if (pathIndex < pathToConnector.size() - 1) {
                pathIndex++;
                workerPosition = pathToConnector.get(pathIndex);
                lastAction = workerPosition;
                return true;
            }
            phase = Phase.MINING_SEGMENT;
        }

        MinerJob.Intent intent = job.intent();
        if (intent instanceof MinerJob.MoveToFaceIntent move) {
            workerPosition = standingPosition(move.segment(), move.depth());
            lastAction = workerPosition;
            job.movementArrived();
            return true;
        }
        if (intent instanceof MinerJob.BreakBlockIntent block) {
            assertWithinWorkReach(workerPosition, block.block());
            lastAction = block.block();
            world.breakBlock(block.block());
            job.blockBroken();
            return true;
        }
        if (intent instanceof MinerJob.PlaceSupportIntent support) {
            lastAction = support.segment().supportOrigin(support.depth());
            world.placeSupport(support.segment(), support.depth());
            job.supportPlaced();
            return true;
        }
        if (intent instanceof MinerJob.SegmentCompleteIntent) {
            phase = Phase.COMPLETE;
            return false;
        }
        throw new IllegalStateException("Unsupported miner intent: " + intent);
    }

    public void runToCompletion() {
        int guard = 1_000;
        while (step() && --guard > 0) {
            // deterministic bounded scenario
        }
        if (guard == 0) throw new IllegalStateException("Mine scenario did not complete");
    }

    public Snapshot snapshot() {
        return new Snapshot(
            model,
            supportPrefab,
            workplace,
            connector,
            pathToConnector,
            workerPosition,
            phase,
            job.state(),
            job.segment(),
            world.snapshot(),
            lastAction,
            simulationDirection,
            orientation,
            false
        );
    }

    private static BlockPosition standingPosition(MineSegment segment, int depth) {
        MineDirection direction = segment.direction();
        int sideX = -direction.dz();
        int sideZ = direction.dx();
        int standingWidth = 1;
        return new BlockPosition(
            segment.start().x() + direction.dx() * (depth - 1) + sideX * standingWidth,
            segment.start().y(),
            segment.start().z() + direction.dz() * (depth - 1) + sideZ * standingWidth
        );
    }

    private static void assertWithinWorkReach(BlockPosition worker, BlockPosition target) {
        double dx = target.x() - worker.x();
        double dy = target.y() - worker.y();
        double dz = target.z() - worker.z();
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        double reachSquared = WORK_REACH_BLOCKS * WORK_REACH_BLOCKS;
        if (distanceSquared > reachSquared + 1.0e-9) {
            throw new IllegalStateException(
                "Mine work target " + target + " is outside worker reach " + WORK_REACH_BLOCKS
                    + " from " + worker
            );
        }
    }

    private static MineSimulationWorld.Bounds mountainBounds(MineSegment segment, MineDirection direction) {
        int minX = segment.blocks().stream().mapToInt(BlockPosition::x).min().orElseThrow();
        int maxX = segment.blocks().stream().mapToInt(BlockPosition::x).max().orElseThrow();
        int minY = segment.blocks().stream().mapToInt(BlockPosition::y).min().orElseThrow();
        int maxY = segment.blocks().stream().mapToInt(BlockPosition::y).max().orElseThrow();
        int minZ = segment.blocks().stream().mapToInt(BlockPosition::z).min().orElseThrow();
        int maxZ = segment.blocks().stream().mapToInt(BlockPosition::z).max().orElseThrow();

        if (direction.dx() != 0) {
            minZ -= MOUNTAIN_SIDE_PADDING;
            maxZ += MOUNTAIN_SIDE_PADDING;
            if (direction.dx() > 0) maxX += MOUNTAIN_FORWARD_EXTENSION;
            else minX -= MOUNTAIN_FORWARD_EXTENSION;
        } else {
            minX -= MOUNTAIN_SIDE_PADDING;
            maxX += MOUNTAIN_SIDE_PADDING;
            if (direction.dz() > 0) maxZ += MOUNTAIN_FORWARD_EXTENSION;
            else minZ -= MOUNTAIN_FORWARD_EXTENSION;
        }

        return new MineSimulationWorld.Bounds(
            minX,
            maxX,
            minY - MOUNTAIN_DOWN_EXTENSION,
            maxY + MOUNTAIN_UP_EXTENSION,
            minZ,
            maxZ
        );
    }

    private static SegmentCandidate chooseTunnelSegment(
        PrefabSimulationModel model,
        BlockPosition connector
    ) {
        PrefabSimulationModel.Bounds bounds = model.blockBounds();
        double centerX = (bounds.minX() + bounds.maxX() + 1) / 2.0;
        double centerZ = (bounds.minZ() + bounds.maxZ() + 1) / 2.0;
        double connectorX = connector.x() + 0.5;
        double connectorZ = connector.z() + 0.5;

        List<SegmentCandidate> candidates = new ArrayList<>();
        for (MineDirection direction : MineDirection.values()) {
            MineSegment segment = MineSegment.reserved(
                SEGMENT_ID,
                MINE_ID,
                null,
                configuredTunnelStart(connector, direction),
                direction
            );
            long overlaps = segment.blocks().stream()
                .filter(position -> model.cellAt(position) != null)
                .count();
            if (overlaps != 0) continue;

            double outwardScore = direction.dx() * (connectorX - centerX)
                + direction.dz() * (connectorZ - centerZ);
            candidates.add(new SegmentCandidate(direction, segment, outwardScore));
        }

        return candidates.stream()
            .max(Comparator
                .comparingDouble(SegmentCandidate::outwardScore)
                .thenComparingInt(candidate -> -candidate.direction().ordinal()))
            .orElseThrow(() -> new IllegalStateException(
                "No non-overlapping 4x4x8 tunnel can start at mine_tunnel_connector " + connector
            ));
    }

    private static void assertNoPrefabOverlap(PrefabSimulationModel model, MineSegment segment) {
        long overlap = segment.blocks().stream().filter(position -> model.cellAt(position) != null).count();
        if (overlap != 0) {
            throw new IllegalStateException(
                "Rotated mine tunnel overlaps " + overlap + " authored prefab blocks"
            );
        }
    }

    private static BlockPosition configuredTunnelStart(
        BlockPosition connector,
        MineDirection direction
    ) {
        int sideX = -direction.dz();
        int sideZ = direction.dx();
        return new BlockPosition(
            connector.x() + direction.dx() - sideX,
            connector.y(),
            connector.z() + direction.dz() - sideZ
        );
    }

    private static BlockPosition markerTarget(
        PrefabSimulationModel model,
        PrefabSimulationModel.Marker marker
    ) {
        PrefabSimulationModel.Bounds search = model.blockBounds().expand(4, 3);
        List<BlockPosition> candidates = new ArrayList<>();
        for (int x = search.minX(); x <= search.maxX(); x++) {
            for (int y = Math.max(1, search.minY()); y <= search.maxY(); y++) {
                for (int z = search.minZ(); z <= search.maxZ(); z++) {
                    BlockPosition position = new BlockPosition(x, y, z);
                    if (marker.containsFeet(position) && model.isWalkableFeet(position)) {
                        candidates.add(position);
                    }
                }
            }
        }
        return candidates.stream()
            .min(Comparator
                .comparingDouble((BlockPosition position) -> distanceSquared(position, marker.bounds()))
                .thenComparingInt(BlockPosition::y)
                .thenComparingInt(BlockPosition::x)
                .thenComparingInt(BlockPosition::z))
            .orElseThrow(() -> new IllegalStateException(
                "No walkable cell inside prefab marker civ.type=" + marker.type()
            ));
    }

    private static double distanceSquared(BlockPosition position, PrefabSimulationModel.Box box) {
        double dx = position.x() + 0.5 - box.centerX();
        double dy = position.y() - box.centerY();
        double dz = position.z() + 0.5 - box.centerZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private record SegmentCandidate(MineDirection direction, MineSegment segment, double outwardScore) {
    }

    public enum Phase {
        NAVIGATING_TO_CONNECTOR,
        MINING_SEGMENT,
        COMPLETE
    }

    public record Snapshot(
        PrefabSimulationModel model,
        PrefabSimulationModel supportPrefab,
        BlockPosition workplace,
        BlockPosition connector,
        List<BlockPosition> pathToConnector,
        BlockPosition probe,
        Phase phase,
        MinerJob.WorkState minerState,
        MineSegment segment,
        MineSimulationWorld.Snapshot tunnelWorld,
        BlockPosition lastAction,
        MineDirection simulationDirection,
        BuildingOrientation orientation,
        boolean directionAuthored
    ) {
        public Snapshot {
            pathToConnector = List.copyOf(pathToConnector);
        }
    }
}
