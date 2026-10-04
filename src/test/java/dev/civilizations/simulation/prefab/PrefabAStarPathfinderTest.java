package dev.civilizations.simulation.prefab;

import dev.civilizations.core.BlockPosition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefabAStarPathfinderTest {

    @Test
    void sealedRoomIsUnreachableUntilDoorBecomesPassable() {
        BlockPosition start = new BlockPosition(0, 1, 2);
        BlockPosition goal = new BlockPosition(2, 1, 2);
        PrefabAStarPathfinder pathfinder = new PrefabAStarPathfinder();
        PrefabSimulationModel sealed = room(false);

        assertTrue(pathfinder.findPath(sealed, start, goal).isEmpty());

        PrefabSimulationModel withDoor = room(true);
        List<BlockPosition> path = pathfinder.findPath(withDoor, start, goal);
        assertFalse(path.isEmpty());
        assertTrue(path.contains(new BlockPosition(1, 1, 2)));
    }

    private static PrefabSimulationModel room(boolean door) {
        Map<BlockPosition, PrefabSimulationModel.Cell> cells = new LinkedHashMap<>();
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                cells.put(new BlockPosition(x, 0, z), PrefabSimulationModel.Cell.SOLID);
            }
        }
        for (int y = 1; y <= 2; y++) {
            for (int n = 1; n <= 3; n++) {
                cells.put(new BlockPosition(1, y, n), PrefabSimulationModel.Cell.SOLID);
                cells.put(new BlockPosition(3, y, n), PrefabSimulationModel.Cell.SOLID);
                cells.put(new BlockPosition(n, y, 1), PrefabSimulationModel.Cell.SOLID);
                cells.put(new BlockPosition(n, y, 3), PrefabSimulationModel.Cell.SOLID);
            }
        }
        if (door) {
            cells.put(new BlockPosition(1, 1, 2), PrefabSimulationModel.Cell.DOOR);
            cells.put(new BlockPosition(1, 2, 2), PrefabSimulationModel.Cell.DOOR);
        }
        return new PrefabSimulationModel(0, 0, 0, cells, List.of());
    }
}
