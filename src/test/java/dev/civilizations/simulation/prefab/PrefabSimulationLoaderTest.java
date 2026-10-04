package dev.civilizations.simulation.prefab;

import dev.civilizations.core.BlockPosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefabSimulationLoaderTest {

    @Test
    void realFarmPrefabKeepsGeometryDoorsAndSemanticMarkers() throws Exception {
        PrefabSimulationModel model = new PrefabSimulationLoader().load(
            FarmPrefabNavigationScenario.PREFAB_PATH
        );

        assertEquals(234, model.cells().size());
        assertEquals(PrefabSimulationModel.Cell.DOOR, model.cellAt(new BlockPosition(-6, 1, 0)));
        assertEquals(PrefabSimulationModel.Cell.DOOR, model.cellAt(new BlockPosition(-6, 2, 0)));
        assertTrue(model.doorFeet().contains(new BlockPosition(-6, 1, 0)));

        assertEquals(3, model.markers().size());
        PrefabSimulationModel.Marker workplace = model.requireMarker("workplace_access");
        assertEquals("civ_farm_workplace", workplace.name());
        assertEquals(-6.5, workplace.bounds().minX());
        assertEquals(1.0, workplace.bounds().minY());
        assertEquals(-0.5, workplace.bounds().minZ());
        assertEquals(-4.5, workplace.bounds().maxX());
        assertEquals(3.0, workplace.bounds().maxY());
        assertEquals(1.5, workplace.bounds().maxZ());

        assertEquals("civ_farm_output_storage", model.requireMarker("output_storage").name());
        assertEquals("civ_farm_building", model.requireMarker("building_bounds").name());
    }
}
