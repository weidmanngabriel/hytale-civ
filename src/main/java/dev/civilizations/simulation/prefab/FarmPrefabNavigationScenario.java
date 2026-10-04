package dev.civilizations.simulation.prefab;

import dev.civilizations.core.BlockPosition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic reachability fixture derived from the real Farm_01 prefab. */
public final class FarmPrefabNavigationScenario {

    public static final Path PREFAB_PATH = Path.of(
        "asset-pack", "Server", "Prefabs", "Civilizations", "Farm", "Farm_01.prefab.json"
    );

    private FarmPrefabNavigationScenario() {
    }

    public static Snapshot create() {
        try {
            PrefabSimulationModel model = new PrefabSimulationLoader().load(PREFAB_PATH);
            BlockPosition door = model.doorFeet().stream()
                .min(Comparator.comparingInt(BlockPosition::x)
                    .thenComparingInt(BlockPosition::z)
                    .thenComparingInt(BlockPosition::y))
                .orElseThrow(() -> new IllegalStateException("Farm prefab has no walkable door"));
            BlockPosition outside = outsideNeighbor(model, door);
            BlockPosition workplace = markerTarget(model, model.requireMarker("workplace_access"));
            BlockPosition storage = markerTarget(model, model.requireMarker("output_storage"));

            PrefabAStarPathfinder pathfinder = new PrefabAStarPathfinder();
            List<BlockPosition> toWorkplace = pathfinder.findPath(model, outside, workplace);
            List<BlockPosition> toStorage = pathfinder.findPath(model, workplace, storage);
            if (toWorkplace.isEmpty()) {
                throw new IllegalStateException("Farm workplace is not reachable from outside");
            }
            if (toStorage.isEmpty()) {
                throw new IllegalStateException("Farm storage is not reachable from workplace");
            }

            List<BlockPosition> combined = new ArrayList<>(toWorkplace);
            combined.addAll(toStorage.subList(1, toStorage.size()));
            return new Snapshot(
                model,
                outside,
                door,
                workplace,
                storage,
                toWorkplace,
                toStorage,
                List.copyOf(combined)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load farm prefab for simulation", exception);
        }
    }

    private static BlockPosition outsideNeighbor(
        PrefabSimulationModel model,
        BlockPosition door
    ) {
        PrefabSimulationModel.Bounds bounds = model.blockBounds();
        List<BlockPosition> candidates = List.of(
            new BlockPosition(door.x() - 1, door.y(), door.z()),
            new BlockPosition(door.x() + 1, door.y(), door.z()),
            new BlockPosition(door.x(), door.y(), door.z() - 1),
            new BlockPosition(door.x(), door.y(), door.z() + 1)
        );
        return candidates.stream()
            .filter(model::isWalkableFeet)
            .filter(candidate -> candidate.x() < bounds.minX()
                || candidate.x() > bounds.maxX()
                || candidate.z() < bounds.minZ()
                || candidate.z() > bounds.maxZ())
            .findFirst()
            .orElseGet(() -> candidates.stream()
                .filter(model::isWalkableFeet)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No walkable cell beside farm door")));
    }

    private static BlockPosition markerTarget(
        PrefabSimulationModel model,
        PrefabSimulationModel.Marker marker
    ) {
        PrefabSimulationModel.Bounds bounds = model.blockBounds();
        PrefabSimulationModel.Bounds search = bounds.expand(3, 3);
        List<BlockPosition> candidates = new ArrayList<>();
        for (int x = search.minX(); x <= search.maxX(); x++) {
            for (int y = Math.max(1, search.minY()); y <= search.maxY(); y++) {
                for (int z = search.minZ(); z <= search.maxZ(); z++) {
                    BlockPosition position = new BlockPosition(x, y, z);
                    if (marker.containsFeet(position)
                        && model.isWalkableFeet(position)
                        && insideHorizontalFootprint(bounds, position)) {
                        candidates.add(position);
                    }
                }
            }
        }
        return candidates.stream()
            .sorted(Comparator
                .comparing((BlockPosition position) -> model.cellAt(position) == PrefabSimulationModel.Cell.DOOR)
                .thenComparingDouble(position -> distanceSquared(position, marker.bounds()))
                .thenComparingInt(BlockPosition::y)
                .thenComparingInt(BlockPosition::x)
                .thenComparingInt(BlockPosition::z))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No walkable interior cell inside prefab marker civ.type=" + marker.type()
            ));
    }

    private static boolean insideHorizontalFootprint(
        PrefabSimulationModel.Bounds bounds,
        BlockPosition position
    ) {
        return position.x() >= bounds.minX() && position.x() <= bounds.maxX()
            && position.z() >= bounds.minZ() && position.z() <= bounds.maxZ();
    }

    private static double distanceSquared(
        BlockPosition position,
        PrefabSimulationModel.Box box
    ) {
        double dx = position.x() + 0.5 - box.centerX();
        double dy = position.y() - box.centerY();
        double dz = position.z() + 0.5 - box.centerZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public record Snapshot(
        PrefabSimulationModel model,
        BlockPosition outsideStart,
        BlockPosition door,
        BlockPosition workplace,
        BlockPosition storage,
        List<BlockPosition> pathToWorkplace,
        List<BlockPosition> pathToStorage,
        List<BlockPosition> combinedPath
    ) {
        public Snapshot {
            pathToWorkplace = List.copyOf(pathToWorkplace);
            pathToStorage = List.copyOf(pathToStorage);
            combinedPath = List.copyOf(combinedPath);
        }
    }
}
