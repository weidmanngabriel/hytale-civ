package dev.civilizations.hytale;

import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import dev.civilizations.core.BuildingOrientation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HytalePrefabOrientationTest {

    @Test
    void mapsCivClockwiseFacingToHytalePrefabRotation() {
        assertEquals(PrefabRotation.ROTATION_0,
            HytalePrefabOrientation.prefabRotation(BuildingOrientation.NORTH));
        assertEquals(PrefabRotation.ROTATION_270,
            HytalePrefabOrientation.prefabRotation(BuildingOrientation.EAST));
        assertEquals(PrefabRotation.ROTATION_180,
            HytalePrefabOrientation.prefabRotation(BuildingOrientation.SOUTH));
        assertEquals(PrefabRotation.ROTATION_90,
            HytalePrefabOrientation.prefabRotation(BuildingOrientation.WEST));
    }
}
