package dev.civilizations.simulation.prefab;

import dev.civilizations.core.BlockPosition;
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

/**
 * Interactive mine fixture that joins the real Mine_01 prefab to the real Core MinerJob.
 *
 * <p>The tunnel connector position is authored prefab data. The initial tunnel direction is an
 * explicit simulation choice because the current simplified prefab model does not claim to read
 * Hytale orientation semantics.</p>
 */
public final class MinePrefabNavigationScenario {

    public static final Path PREFAB_PATH = Path.of(
        "asset-pack", "Server", "Prefabs", "Civilizations", "Mine", "Mine_01.prefab.json"
    );
    public static final Path SUPPORT_PREFAB_PATH = Path.of(
        "asset-pack", "Server", "Prefabs", "Civilizations", "Mine", "Mine_Support_01.prefab.json"
    );
    public static final MineDirection SIMULATION_DIRECTION = MineDirection.NORTH;

    private static final UUID SEGMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID MINE_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");

    private final PrefabSimulationModel model;
    private final PrefabSimulationModel supportPrefab;
    private final BlockPosition workplace;
    private final BlockPosition connector;
    private final List<BlockPosition> pathToConnector;
    private final MineSimulationWorld world;
    private final MinerJob job;
    private int pathIndex;
    private Phase phase = Phase.NAVIGATING_TO_CONNECTOR;
    private BlockPosition lastAction;

    private MinePrefabNavigationScenario(
        PrefabSimulationModel model,
        PrefabSimulationModel supportPrefab,
        BlockPosition workplace,
        BlockPosition connector,
        List<BlockPosition> pathToConnector,
        MineSimulationWorld world,
        MinerJob job
    ) {
        this.model = model;
        this.supportPrefab = supportPrefab;
        this.workplace = workplace;
        this.connector = connector;
        this.pathToConnector = List.copyOf(pathToConnector);
        this.world = world;
        this.job = job;
    }

    public static MinePrefabNavigationScenario create() {
        try {
            PrefabSimulationLoader loader = new PrefabSimulationLoader();
            PrefabSimulationModel model = loader.load(PREFAB_PATH);
            PrefabSimulationModel support = loader.load(SUPPORT_PREFAB_PATH);
            BlockPosition workplace = markerTarget(model, model.requireMarker("workplace_access"));
            BlockPosition connector = markerTarget(model, model.requireMarker("mine_tunnel_connector"));
            List<BlockPosition> path = new PrefabAStarPathfinder().findPath(model, workplace, connector);
            if (path.isEmpty()) {
                throw new IllegalStateException("Mine tunnel connector is not reachable from workplace");
            }

            BlockPosition tunnelStart = configuredTunnelStart(connector, SIMULATION_DIRECTION);
            MineSegment segment = MineSegment.reserved(
                SEGMENT_ID, MINE_ID, null, tunnelStart, SIMULATION_DIRECTION
            );
            return new MinePrefabNavigationScenario(
                model,
                support,
                workplace,
                connector,
                path,
                new MineSimulationWorld(segment),
                new MinerJob(segment)
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
                lastAction = pathToConnector.get(pathIndex);
                return true;
            }
            phase = Phase.MINING_SEGMENT;
        }

        MinerJob.Intent intent = job.intent();
        if (intent instanceof MinerJob.MoveToFaceIntent move) {
            int blockIndex = Math.min(
                move.depth() * 16,
                move.segment().blocks().size() - 1
            );
            lastAction = move.segment().blockAtIndex(blockIndex);
            job.movementArrived();
            return true;
        }
        if (intent instanceof MinerJob.BreakBlockIntent block) {
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
        BlockPosition probe = phase == Phase.NAVIGATING_TO_CONNECTOR
            ? pathToConnector.get(pathIndex)
            : lastAction == null ? connector : lastAction;
        return new Snapshot(
            model,
            supportPrefab,
            workplace,
            connector,
            pathToConnector,
            probe,
            phase,
            job.state(),
            job.segment(),
            world.snapshot(),
            lastAction,
            SIMULATION_DIRECTION,
            false
        );
    }

    private static BlockPosition configuredTunnelStart(
        BlockPosition connector,
        MineDirection direction
    ) {
        // Connector is a feet-position target. Start the first cutting face one block beyond it and
        // center the four-wide face around the connector. This transform is scenario configuration,
        // not an assertion about authored Hytale prefab orientation.
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
        boolean directionAuthored
    ) {
        public Snapshot {
            pathToConnector = List.copyOf(pathToConnector);
        }
    }
}
