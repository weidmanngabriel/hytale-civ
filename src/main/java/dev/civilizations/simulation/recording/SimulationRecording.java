package dev.civilizations.simulation.recording;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.WorldPosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned presentation data. No engine objects or gameplay decisions cross this boundary. */
public record SimulationRecording(
    int schemaVersion,
    String id,
    String title,
    String description,
    String timeUnit,
    String status,
    String error,
    List<int[]> initialVoxels,
    List<Marker> markers,
    List<Frame> frames
) {
    public static final int SCHEMA_VERSION = 1;
    public static final int AIR = 0, ROCK = 1, PREFAB = 2, SUPPORT = 3,
        WOOD = 4, FIELD = 5, DOOR = 6, CONSTRUCTION = 7;

    public record Marker(String id, String type, double[] bounds) { }

    public record Resident(
        String id, String profession, WorldPosition position, WorldPosition target,
        String state, Map<String, Object> details
    ) { }

    /** Frame zero has no changes; later frames are ordered deltas from the previous frame. */
    public record Frame(
        int step, double time, List<int[]> changes, List<Resident> residents,
        BlockPosition action, Map<String, Object> metrics
    ) { }

    public static final class Recorder {
        private static final Comparator<BlockPosition> ORDER = Comparator.comparingInt(BlockPosition::x)
            .thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z);
        private final List<int[]> initial;
        private final List<Frame> frames = new ArrayList<>();
        private Map<BlockPosition, Integer> previous;

        public Recorder(Map<BlockPosition, Integer> world) {
            previous = Map.copyOf(world);
            initial = cells(world);
        }

        public void capture(
            double time, Map<BlockPosition, Integer> world, List<Resident> residents,
            BlockPosition action, Map<String, Object> metrics
        ) {
            Map<BlockPosition, Integer> delta = new LinkedHashMap<>();
            if (!frames.isEmpty()) {
                world.forEach((p, material) -> {
                    if (!material.equals(previous.get(p))) delta.put(p, material);
                });
                previous.keySet().forEach(p -> {
                    if (!world.containsKey(p)) delta.put(p, AIR);
                });
            }
            frames.add(new Frame(frames.size(), time, cells(delta), List.copyOf(residents),
                action, Map.copyOf(metrics)));
            previous = Map.copyOf(world);
        }

        public SimulationRecording finish(
            String id, String title, String description, String timeUnit,
            List<Marker> markers, String error
        ) {
            return new SimulationRecording(SCHEMA_VERSION, id, title, description, timeUnit,
                error == null ? "completed" : "failed", error, initial, List.copyOf(markers),
                List.copyOf(frames));
        }

        private static List<int[]> cells(Map<BlockPosition, Integer> world) {
            return world.entrySet().stream().sorted(Map.Entry.comparingByKey(ORDER))
                .map(e -> new int[]{e.getKey().x(), e.getKey().y(), e.getKey().z(), e.getValue()})
                .toList();
        }
    }
}
