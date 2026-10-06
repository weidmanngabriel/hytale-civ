package dev.civilizations.simulation.prefab;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingOrientation;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MinerJob;
import dev.civilizations.simulation.MineSimulationWorld;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Scripted combination fixture using real Core segments/jobs in one shared voxel world.
 * Routes are a geometric test oracle, not Hytale navigation or production job selection.
 */
public final class MineBranchingScenario {
    public static final int INTERRUPT_PROGRESS = 73;
    private static final int[] ITINERARY = {1, 2, 3, 1, 4, 5, 6};
    private final MinePrefabNavigationScenario entrance;
    private final PrefabSimulationModel model;
    private final List<BlockPosition> entrancePath;
    private final BlockPosition workplace;
    private final BlockPosition connector;
    private final List<MineSegment> segments = new ArrayList<>();
    private final MineSimulationWorld world;
    private final ArrayDeque<BlockPosition> route = new ArrayDeque<>();
    private final Set<BlockPosition> entranceFeet;
    private BlockPosition worker;
    private BlockPosition action;
    private MinerJob job;
    private int cursor;
    private int active;
    private int outsideSteps;
    private boolean interrupted;
    private String phase = "ENTRANCE";
    private RoutePurpose routePurpose;

    private MineBranchingScenario(BuildingOrientation orientation) {
        entrance = MinePrefabNavigationScenario.create(orientation);
        var start = entrance.snapshot();
        model = start.model();
        entrancePath = start.pathToConnector();
        entranceFeet = Set.copyOf(entrancePath);
        workplace = start.workplace();
        connector = start.connector();
        worker = start.probe();
        MineSegment root = start.segment();
        segments.add(root);
        segments.add(child(root, root.direction(), 12, 1));
        segments.add(child(root, root.direction().left(), 4, 2));
        segments.add(child(root, root.direction().right(), 5, 3));
        MineSegment trunk = segments.get(1);
        segments.add(child(trunk, trunk.direction().left(), 8, 4));
        segments.add(child(trunk, trunk.direction().right(), 9, 5));
        segments.add(child(trunk, trunk.direction(), 4, 6));
        validatePlan();
        var b = start.tunnelWorld().bounds();
        int minX = b.minX(), maxX = b.maxX(), minZ = b.minZ(), maxZ = b.maxZ();
        for (MineSegment segment : segments) for (BlockPosition p : segment.blocks()) {
            minX = Math.min(minX, p.x() - 4); maxX = Math.max(maxX, p.x() + 4);
            minZ = Math.min(minZ, p.z() - 4); maxZ = Math.max(maxZ, p.z() + 4);
        }
        // Preserve the original mountain mouth: do not bury the authored entrance.
        switch (root.direction()) {
            case NORTH -> maxZ = b.maxZ();
            case SOUTH -> minZ = b.minZ();
            case EAST -> minX = b.minX();
            case WEST -> maxX = b.maxX();
        }
        world = new MineSimulationWorld(root,
            new MineSimulationWorld.Bounds(minX, maxX, b.minY(), b.maxY(), minZ, maxZ));
    }

    public static MineBranchingScenario create(BuildingOrientation orientation) {
        return new MineBranchingScenario(orientation);
    }

    private MineSegment child(MineSegment parent, MineDirection direction, int length, int index) {
        return MineSegment.reserved(new UUID(0, 500 + index), parent.mineId(), parent.id(),
            parent.nextStart(direction), direction, length);
    }

    private void validatePlan() {
        Set<BlockPosition> occupied = new HashSet<>();
        for (MineSegment segment : segments) for (BlockPosition p : segment.blocks()) {
            if (!occupied.add(p) || model.cellAt(p) != null) {
                throw new IllegalStateException("Combination fixture overlaps a tunnel/prefab at " + p);
            }
        }
    }

    /** One route cell, block, support or lifecycle transition per visible step. */
    public boolean step() {
        action = null;
        if (phase.equals("COMPLETE")) return false;
        if (phase.equals("ENTRANCE")) {
            boolean advanced = entrance.step();
            var s = entrance.snapshot();
            worker = s.probe(); action = s.lastAction(); segments.set(0, s.segment());
            s.tunnelWorld().overrides().forEach((p, cell) -> {
                if (cell == MineSimulationWorld.Cell.AIR) world.breakBlock(p);
            });
            // Supports are copied using the real semantic support contract.
            for (int n = 1; n <= s.segment().supportsPlaced(); n++) {
                world.placeSupport(s.segment(), n * 4);
            }
            if (!advanced) startJob();
            return true;
        }
        if (routePurpose != null) {
            if (!route.isEmpty()) {
                worker = route.removeFirst();
                return true;
            }
            RoutePurpose arrived = routePurpose;
            routePurpose = null;
            switch (arrived) {
                case FACE -> { job.movementArrived(); phase = "MINING"; }
                case EXIT -> { phase = "OUTSIDE"; outsideSteps = 2; }
                case ENTRY -> phase = "MINING";
                case FINAL_EXIT -> { phase = "COMPLETE"; return false; }
            }
            return true;
        }
        if (phase.equals("OUTSIDE")) {
            if (outsideSteps-- > 0) return true;
            cursor++;
            startJob();
            beginRoute(connector, RoutePurpose.ENTRY, "REENTERING_MINE");
            return true;
        }
        if (active == 1 && !interrupted && job.segment().nextBlockIndex() == INTERRUPT_PROGRESS) {
            interrupted = true;
            segments.set(active, job.segment());
            beginRoute(workplace, RoutePurpose.EXIT, "LEAVING_MINE");
            return true;
        }
        switch (job.intent()) {
            case MinerJob.MoveToFaceIntent move -> beginRoute(
                standingPosition(move.segment(), move.depth()), RoutePurpose.FACE, "MOVING_TO_FACE");
            case MinerJob.BreakBlockIntent block -> {
                assertReach(block.block());
                if (world.get(block.block()) != MineSimulationWorld.Cell.SOLID) {
                    throw new IllegalStateException("Repeated excavation at " + block.block());
                }
                action = block.block();
                world.breakBlock(block.block()); job.blockBroken();
            }
            case MinerJob.PlaceSupportIntent support -> {
                action = support.segment().supportOrigin(support.depth());
                world.placeSupport(support.segment(), support.depth()); job.supportPlaced();
            }
            case MinerJob.SegmentCompleteIntent ignored -> {
                cursor++;
                if (cursor == ITINERARY.length) {
                    beginRoute(workplace, RoutePurpose.FINAL_EXIT, "FINAL_RETURN");
                } else startJob();
            }
        }
        segments.set(active, job.segment());
        return true;
    }

    private void startJob() {
        active = ITINERARY[cursor];
        job = new MinerJob(segments.get(active));
        segments.set(active, job.segment());
        phase = active == 1 && interrupted ? "RESUMING_SAVED_WORK" : "MINING";
    }

    private void beginRoute(BlockPosition target, RoutePurpose purpose, String nextPhase) {
        List<BlockPosition> path = findRoute(worker, target);
        if (path.isEmpty()) throw new IllegalStateException("No excavated return/work route: " + worker + " -> " + target);
        route.clear();
        route.addAll(path.subList(1, path.size()));
        routePurpose = purpose;
        phase = nextPhase;
    }

    /** Deterministic two-cell-high reachability inside excavated cells and the authored entrance path. */
    private List<BlockPosition> findRoute(BlockPosition from, BlockPosition to) {
        var queue = new ArrayDeque<BlockPosition>();
        Map<BlockPosition, BlockPosition> previous = new HashMap<>();
        Set<BlockPosition> visited = new HashSet<>();
        queue.add(from); visited.add(from);
        while (!queue.isEmpty()) {
            BlockPosition current = queue.removeFirst();
            if (current.equals(to)) {
                var reversed = new ArrayList<BlockPosition>();
                for (BlockPosition p = to; p != null; p = previous.get(p)) reversed.add(p);
                return reversed.reversed();
            }
            for (int[] offset : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                for (int dy : new int[]{0,1,-1}) {
                    BlockPosition next = new BlockPosition(current.x()+offset[0], current.y()+dy, current.z()+offset[1]);
                    if (walkable(next) && visited.add(next)) {
                        previous.put(next,current); queue.addLast(next);
                    }
                }
            }
        }
        return List.of();
    }

    public boolean walkable(BlockPosition p) {
        if (entranceFeet.contains(p)) return true;
        return p.y() == segments.getFirst().start().y() && world.bounds().contains(p)
            && model.cellAt(p) == null
            && model.cellAt(new BlockPosition(p.x(),p.y()+1,p.z())) == null
            && world.get(p) == MineSimulationWorld.Cell.AIR
            && world.get(new BlockPosition(p.x(),p.y()+1,p.z())) == MineSimulationWorld.Cell.AIR;
    }

    private void assertReach(BlockPosition p) {
        int dx=p.x()-worker.x(), dy=p.y()-worker.y(), dz=p.z()-worker.z();
        if (dx*dx+dy*dy+dz*dz > 16) throw new IllegalStateException("Work outside four-block reach");
    }

    private static BlockPosition standingPosition(MineSegment s, int depth) {
        return new BlockPosition(s.start().x()+s.direction().dx()*(depth-1)-s.direction().dz(),
            s.start().y(), s.start().z()+s.direction().dz()*(depth-1)+s.direction().dx());
    }

    public Snapshot snapshot() {
        BlockPosition target = route.peekLast();
        String state = job == null ? entrance.snapshot().minerState().name() : job.state().name();
        return new Snapshot(model, world.snapshot(), List.copyOf(segments), active, worker,
            target, action, phase, state, interrupted, workplace, connector);
    }

    public void runToCompletion() {
        int remaining = 5_000;
        while (step() && --remaining > 0) { }
        if (remaining == 0) throw new IllegalStateException("Branching scenario exceeded 5000 steps");
    }

    private enum RoutePurpose { FACE, EXIT, ENTRY, FINAL_EXIT }

    public record Snapshot(PrefabSimulationModel model, MineSimulationWorld.Snapshot tunnelWorld,
        List<MineSegment> segments, int activeSegment, BlockPosition worker, BlockPosition target,
        BlockPosition lastAction, String phase, String minerState, boolean interrupted,
        BlockPosition workplace, BlockPosition connector) { }
}
