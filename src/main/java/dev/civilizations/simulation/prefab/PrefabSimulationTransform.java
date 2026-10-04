package dev.civilizations.simulation.prefab;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingOrientation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Applies Civ's neutral building orientation to imported prefab geometry. */
public final class PrefabSimulationTransform {
    private PrefabSimulationTransform() {
    }

    public static PrefabSimulationModel rotate(
        PrefabSimulationModel model,
        BuildingOrientation orientation
    ) {
        if (orientation == BuildingOrientation.NORTH) {
            return model;
        }
        Map<BlockPosition, PrefabSimulationModel.Cell> cells = new LinkedHashMap<>();
        model.cells().forEach((position, cell) ->
            cells.put(orientation.rotateAround(position, model.anchorX(), model.anchorZ()), cell)
        );
        List<PrefabSimulationModel.Marker> markers = model.markers().stream()
            .map(marker -> new PrefabSimulationModel.Marker(
                marker.name(),
                marker.type(),
                marker.building(),
                rotateBox(marker.bounds(), model.anchorX(), model.anchorZ(), orientation)
            ))
            .toList();
        return new PrefabSimulationModel(
            model.anchorX(), model.anchorY(), model.anchorZ(), cells, markers
        );
    }

    private static PrefabSimulationModel.Box rotateBox(
        PrefabSimulationModel.Box box,
        int anchorX,
        int anchorZ,
        BuildingOrientation orientation
    ) {
        double[][] corners = {
            {box.minX(), box.minZ()},
            {box.minX(), box.maxZ()},
            {box.maxX(), box.minZ()},
            {box.maxX(), box.maxZ()}
        };
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double[] corner : corners) {
            double localX = corner[0] - anchorX;
            double localZ = corner[1] - anchorZ;
            double x;
            double z;
            switch (orientation) {
                case NORTH -> { x = localX; z = localZ; }
                case EAST -> { x = -localZ; z = localX; }
                case SOUTH -> { x = -localX; z = -localZ; }
                case WEST -> { x = localZ; z = -localX; }
                default -> throw new IllegalStateException("Unhandled orientation " + orientation);
            }
            x += anchorX;
            z += anchorZ;
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
        }
        return new PrefabSimulationModel.Box(
            minX, box.minY(), minZ,
            maxX, box.maxY(), maxZ
        );
    }
}
