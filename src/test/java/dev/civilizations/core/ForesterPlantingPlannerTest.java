package dev.civilizations.core;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ForesterPlantingPlannerTest {
    @Test void seededSelectionIsDeterministicAndWithinHomeArea() {
        var a = new ForesterPlantingPlanner(new BlockPosition(0,0,0), 18, 4, 19L);
        var b = new ForesterPlantingPlanner(new BlockPosition(0,0,0), 18, 4, 19L);
        for (int i = 0; i < 12; i++) {
            var p = a.choose(new BlockPosition(2,0,1), Set.of(), Set.of(), point -> true);
            assertEquals(p, b.choose(new BlockPosition(2,0,1), Set.of(), Set.of(), point -> true));
            assertNotNull(p);
            assertTrue(Math.abs(p.x()) <= 18 && Math.abs(p.z()) <= 18);
        }
    }
    @Test void excludesOccupiedReservedAndInvalidSites() {
        var planner = new ForesterPlantingPlanner(new BlockPosition(0,0,0), 12, 4, 7L);
        var occupied = new HashSet<BlockPosition>();
        occupied.add(new BlockPosition(0,0,0));
        var picked = planner.choose(new BlockPosition(0,0,0), occupied, Set.of(),
            p -> p.x() >= 0 && p.z() >= 0);
        if (picked != null) {
            assertTrue(picked.x() >= 0 && picked.z() >= 0);
            assertTrue(picked.x() * picked.x() + picked.z() * picked.z() >= 16);
        }
        assertNull(planner.choose(new BlockPosition(0,0,0), occupied, Set.of(), p -> false));
    }
}
