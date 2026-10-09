package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class DwarvenConstructionPersistenceTest {
    @Test
    void everyDwarvenPhaseRoundTripsThroughConstructionPersistence() throws Exception {
        Method encode = CivConstructionPersistenceService.class.getDeclaredMethod(
            "definitionToken", PrefabPlacementService.PlacementDefinition.class);
        Method decode = CivConstructionPersistenceService.class.getDeclaredMethod(
            "definition", String.class);
        encode.setAccessible(true);
        decode.setAccessible(true);
        var phases = new PrefabPlacementService.PlacementDefinition[]{
            PrefabPlacementService.DWARF_MINE,
            PrefabPlacementService.DWARF_MINE_02,
            PrefabPlacementService.DWARF_MINE_03
        };
        for (var phase : phases) {
            String token = (String) encode.invoke(null, phase);
            assertSame(phase, decode.invoke(null, token));
        }
    }
}
