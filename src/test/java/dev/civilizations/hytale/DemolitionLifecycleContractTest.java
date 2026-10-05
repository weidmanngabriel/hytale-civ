package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class DemolitionLifecycleContractTest {
    @Test
    void upgradesReleaseTheirConstructionReservationAndDemolitionEvacuatesWorkers() throws Exception {
        String construction = Files.readString(Path.of(
            "src/main/java/dev/civilizations/hytale/ConstructionWorkSystem.java"
        ));
        String controller = Files.readString(Path.of(
            "src/main/java/dev/civilizations/hytale/RtsInteractionController.java"
        ));
        assertTrue(construction.contains("buildingRegistry.release(site.worldId(), site.id());"));
        assertTrue(controller.contains("evacuateWorkersForDemolition(world, building)"));
        assertTrue(controller.contains("releaseOrphanedReservationsOverlapping"));
        assertTrue(controller.contains("mineTunnelRegistry.removeMine(world, buildingId)"));
        assertTrue(controller.contains("clearWorkplace"));
    }
}
