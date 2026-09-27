package dev.civilizations.plugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WikiAssetValidationTest {

    private static final Path PAGES = Path.of("asset-pack/Common/UI/Custom/Pages");

    @Test
    void wikiHomeExposesAllRequestedSections() throws IOException {
        String home = read("CivWikiHome.ui");

        assertTrue(home.contains("#ProfessionsButton"));
        assertTrue(home.contains("#ResourcesButton"));
        assertTrue(home.contains("#BuildingsButton"));
        assertTrue(home.contains("#AnimalsButton"));
    }

    @Test
    void implementedEntriesContainCrossLinks() throws IOException {
        String professions = read("CivWikiProfessions.ui");
        String resources = read("CivWikiResources.ui");
        String buildings = read("CivWikiBuildings.ui");

        assertTrue(professions.contains("#WoodResourceLink"));
        assertTrue(professions.contains("#FarmBuildingLink"));
        assertTrue(professions.contains("#WheatResourceLink"));

        assertTrue(resources.contains("#WoodcutterProfessionLink"));
        assertTrue(resources.contains("#FarmerProfessionLink"));
        assertTrue(resources.contains("#FarmBuildingLink"));

        assertTrue(buildings.contains("#FarmerProfessionLink"));
        assertTrue(buildings.contains("#WheatResourceLink"));
    }

    @Test
    void animalsPageDoesNotClaimUnimplementedGameplay() throws IOException {
        String animals = read("CivWikiAnimals.ui");

        assertTrue(animals.contains("noch keine Tiere mit eigener Civ-Funktion"));
        assertFalse(animals.contains("Schaf"));
        assertFalse(animals.contains("Kuh"));
    }

    private static String read(String file) throws IOException {
        return Files.readString(PAGES.resolve(file));
    }
}
