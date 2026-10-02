package dev.civilizations.simulation;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.FarmBuilding;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.scenario.WoodcutterBasicScenario;

import java.util.List;

/**
 * Small built-in scenario catalog for deterministic development and regression testing.
 */
public final class SimulationScenarios {

    public static final SimulationScenario DEMO_SETTLEMENT = new SimulationScenario(
        "demo-settlement",
        "Demo Settlement",
        "Gemischtes Startdorf mit Holzfäller, Bauarbeiter, Bauer, Bäumen, Baustellen und Feld.",
        SimulationScenarios::createDemoSettlement
    );

    public static final SimulationScenario WOODCUTTER_BASIC = new SimulationScenario(
        WoodcutterBasicScenario.ID,
        WoodcutterBasicScenario.DISPLAY_NAME,
        "Ein Holzfäller mit drei Bäumen. Zeigt Suche, Weg, Arbeit und erneute Zielsuche.",
        SimulationScenarios::createWoodcutterBasic
    );

    public static final SimulationScenario BUILDER_BASIC = new SimulationScenario(
        "builder-basic",
        "Builder Basic",
        "Ein Bauarbeiter mit einer offenen Baustelle.",
        SimulationScenarios::createBuilderBasic
    );

    public static final SimulationScenario FARMER_BASIC = new SimulationScenario(
        "farmer-basic",
        "Farmer Basic",
        "Ein Bauer mit zugewiesener Farm und einem verfügbaren Feld.",
        SimulationScenarios::createFarmerBasic
    );

    public static final SimulationScenario WAITING_WORKERS = new SimulationScenario(
        "waiting-workers",
        "Waiting Workers",
        "Arbeiter ohne passende Ziele. Gut zum Prüfen von Retry-Taktung und Leerlauf.",
        SimulationScenarios::createWaitingWorkers
    );

    private static final List<SimulationScenario> ALL = List.of(
        DEMO_SETTLEMENT,
        WOODCUTTER_BASIC,
        BUILDER_BASIC,
        FARMER_BASIC,
        WAITING_WORKERS
    );

    private SimulationScenarios() {
    }

    public static List<SimulationScenario> all() {
        return ALL;
    }

    private static SimulationRuntime createDemoSettlement() {
        SimulationRuntime runtime = new SimulationRuntime();

        runtime.addWoodcutter(
            "woodcutter-1",
            new WorldPosition(-7.0, 0.0, -4.0)
        );
        runtime.addConstructionWorker(
            "builder-1",
            new WorldPosition(-7.0, 0.0, 2.5)
        );

        FarmBuilding farm = new FarmBuilding(
            "farm-1",
            new BlockPosition(2, 0, 6),
            new BlockPosition(2, 0, 5)
        );
        runtime.addFarmer(
            "farmer-1",
            new WorldPosition(-6.0, 0.0, 6.5),
            farm
        );

        runtime.addTree(new BlockPosition(4, 0, -5));
        runtime.addTree(new BlockPosition(7, 0, -3));
        runtime.addTree(new BlockPosition(5, 0, 0));

        runtime.addConstructionSite(
            "house-site",
            new WorldPosition(5.0, 0.0, 2.5),
            8
        );
        runtime.addConstructionSite(
            "storage-site",
            new WorldPosition(8.0, 0.0, 4.5),
            12
        );

        runtime.addFarmField(
            farm.id(),
            new WorldPosition(7.5, 0.0, 7.0)
        );

        return runtime;
    }

    private static SimulationRuntime createWoodcutterBasic() {
        SimulationRuntime runtime = new SimulationRuntime();
        runtime.addWoodcutter("woodcutter-1", WoodcutterBasicScenario.WOODCUTTER_START);
        for (BlockPosition tree : WoodcutterBasicScenario.TREE_BASES) {
            runtime.addTree(tree);
        }
        return runtime;
    }

    private static SimulationRuntime createBuilderBasic() {
        SimulationRuntime runtime = new SimulationRuntime();
        runtime.addConstructionWorker(
            "builder-1",
            new WorldPosition(0.0, 0.0, 0.0)
        );
        runtime.addConstructionSite(
            "house-site",
            new WorldPosition(4.0, 0.0, 0.0),
            8
        );
        return runtime;
    }

    private static SimulationRuntime createFarmerBasic() {
        SimulationRuntime runtime = new SimulationRuntime();
        FarmBuilding farm = new FarmBuilding(
            "farm-1",
            new BlockPosition(0, 0, 0),
            new BlockPosition(0, 0, 0)
        );
        WorldPosition field = new WorldPosition(4.5, 0.0, 0.5);
        runtime.addFarmField(farm.id(), field);
        runtime.addFarmer(
            "farmer-1",
            new WorldPosition(0.5, 0.0, 0.5),
            farm
        );
        return runtime;
    }

    private static SimulationRuntime createWaitingWorkers() {
        SimulationRuntime runtime = new SimulationRuntime();
        runtime.addWoodcutter(
            "woodcutter-1",
            new WorldPosition(-2.0, 0.0, 0.0)
        );
        runtime.addConstructionWorker(
            "builder-1",
            new WorldPosition(0.0, 0.0, 0.0)
        );

        FarmBuilding farm = new FarmBuilding(
            "farm-1",
            new BlockPosition(2, 0, 0),
            new BlockPosition(2, 0, 0)
        );
        runtime.addFarmer(
            "farmer-1",
            new WorldPosition(2.5, 0.0, 0.5),
            farm
        );

        return runtime;
    }
}
