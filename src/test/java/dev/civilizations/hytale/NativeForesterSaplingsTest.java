package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class NativeForesterSaplingsTest {
    @Test void emptyPaletteWaitsWithoutInventingADefault() {
        assertNull(NativeForesterSaplings.choose(List.of(), new Random(15)));
    }

    @Test void seededSelectionReproduciblyIncludesAllThreeSpecies() {
        var species = List.of("Plant_Sapling_Birch", "Plant_Sapling_Oak", "Plant_Sapling_Spruce");
        var a = new Random(1094);
        var b = new Random(1094);
        var seen = new java.util.HashSet<String>();
        for (int i=0; i<250; i++) {
            String next = NativeForesterSaplings.choose(species,a);
            assertEquals(next, NativeForesterSaplings.choose(species,b));
            seen.add(next);
        }
        assertEquals(new java.util.HashSet<>(species),seen);
    }

    @Test void removingOneSpeciesCannotReselectIt() {
        var species = List.of("Plant_Sapling_Oak", "Plant_Sapling_Spruce");
        for (int n=0; n<100; n++)
            assertNotEquals("Plant_Sapling_Birch", NativeForesterSaplings.choose(species,new Random(n)));
    }
}
