package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class MineBranchSupportBeamContractTest {

    @Test
    void crossbeamsUseTrunksForMainAndBranchTunnels() throws Exception {
        String code = Files.readString(Path.of(
            "src/main/java/dev/civilizations/hytale/MineInfrastructurePlacementResolver.java"
        ));
        assertTrue(code.contains("String beamBlock = FIR_TRUNK;"));
        assertFalse(code.contains("String beamBlock = tunnelKind == MineTunnel.Kind.MAIN ? FIR_TRUNK : FIR_BRANCH;"));
        assertTrue(code.contains("placements.add(new PlacementStep(position, FIR_BRANCH, vertical,"));
        assertTrue(code.contains("placements.add(new PlacementStep(leftPosition, beamBlock, beamRotation,"));
    }
}
