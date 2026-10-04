package dev.civilizations.plugin;

import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEventType;
import com.hypixel.hytale.builtin.triggervolumes.event.TriggerVolumeEvent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.VikingNameGenerator;
import dev.civilizations.hytale.BuildingPlacementRegistry;
import dev.civilizations.hytale.CivActivityRegistry;
import dev.civilizations.hytale.CivBuildingBlockProtectionSystem;
import dev.civilizations.hytale.CivBuildingDataResource;
import dev.civilizations.hytale.CivBuildingPersistenceService;
import dev.civilizations.hytale.CivClaimDamageSystem;
import dev.civilizations.hytale.CivInhabitantData;
import dev.civilizations.hytale.CivInhabitantLifecycleSystem;
import dev.civilizations.hytale.CivInhabitantService;
import dev.civilizations.hytale.CivInhabitantUseSystem;
import dev.civilizations.hytale.CivManualMovementSystem;
import dev.civilizations.hytale.CivMineDataResource;
import dev.civilizations.hytale.CivMinePersistenceService;
import dev.civilizations.hytale.CivNameplateStatusSystem;
import dev.civilizations.hytale.CivPathDebugService;
import dev.civilizations.hytale.CivPlayerRigDebugService;
import dev.civilizations.hytale.CivSelectedNpcHudController;
import dev.civilizations.hytale.CivSelectedNpcHudSystem;
import dev.civilizations.hytale.CivUnitRegistry;
import dev.civilizations.hytale.ConstructionWorkSystem;
import dev.civilizations.hytale.FarmBuildingRegistry;
import dev.civilizations.hytale.FarmFieldRegistry;
import dev.civilizations.hytale.FarmNpcWorkSystem;
import dev.civilizations.hytale.MineTunnelRegistry;
import dev.civilizations.hytale.MinerWorkSystem;
import dev.civilizations.hytale.PrefabPlacementService;
import dev.civilizations.hytale.RtsCameraController;
import dev.civilizations.hytale.RtsInteractionController;
import dev.civilizations.hytale.SoldierWorkSystem;
import dev.civilizations.hytale.VikingAppearanceGenerator;
import dev.civilizations.hytale.WoodcutterScanDiagnostics;
import dev.civilizations.hytale.WoodcutterWorkSystem;

public final class CivilizationsPlugin extends JavaPlugin {

    private static final String CIV_INHABITANT_DATA_ID = "CivInhabitantData";
    private static final String CIV_BUILDING_DATA_ID = "CivBuildingData";
    private static final String CIV_MINE_DATA_ID = "CivMineData";

    public CivilizationsPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setup() {
        ComponentType<EntityStore, CivInhabitantData> inhabitantDataType =
            getEntityStoreRegistry().registerComponent(
                CivInhabitantData.class,
                CIV_INHABITANT_DATA_ID,
                CivInhabitantData.CODEC
            );
        ResourceType<EntityStore, CivBuildingDataResource> buildingDataType =
            getEntityStoreRegistry().registerResource(
                CivBuildingDataResource.class,
                CIV_BUILDING_DATA_ID,
                CivBuildingDataResource.CODEC
            );
        ResourceType<EntityStore, CivMineDataResource> mineDataType =
            getEntityStoreRegistry().registerResource(
                CivMineDataResource.class,
                CIV_MINE_DATA_ID,
                CivMineDataResource.CODEC
            );
        CivBuildingPersistenceService buildingPersistence =
            new CivBuildingPersistenceService(buildingDataType);
        CivMinePersistenceService minePersistence = new CivMinePersistenceService(mineDataType);
        MineTunnelRegistry mineTunnelRegistry = new MineTunnelRegistry(minePersistence);

        CivInhabitantService inhabitantService = new CivInhabitantService(
            inhabitantDataType,
            new VikingNameGenerator(),
            new VikingAppearanceGenerator()
        );
        CivUnitRegistry unitRegistry = new CivUnitRegistry(inhabitantService);
        CivPathDebugService pathDebugService = new CivPathDebugService(inhabitantDataType);
        CivPlayerRigDebugService playerRigDebugService =
            new CivPlayerRigDebugService(inhabitantDataType);
        CivActivityRegistry activityRegistry = new CivActivityRegistry(unitRegistry);
        CivSelectedNpcHudController selectedNpcHudController =
            new CivSelectedNpcHudController(unitRegistry, activityRegistry);
        FarmBuildingRegistry farmRegistry = new FarmBuildingRegistry(unitRegistry);
        FarmFieldRegistry fieldRegistry = new FarmFieldRegistry();
        BuildingPlacementRegistry buildingRegistry = new BuildingPlacementRegistry();
        PrefabPlacementService prefabPlacementService = new PrefabPlacementService();
        WoodcutterScanDiagnostics woodcutterScanDiagnostics = new WoodcutterScanDiagnostics();
        RtsInteractionController rtsInteractionController =
            new RtsInteractionController(
                new RtsCameraController(),
                unitRegistry,
                activityRegistry,
                farmRegistry,
                fieldRegistry,
                buildingRegistry,
                prefabPlacementService,
                buildingPersistence,
                mineTunnelRegistry
            );

        getEntityStoreRegistry().registerSystem(
            new CivBuildingBlockProtectionSystem.BreakProtection(buildingRegistry)
        );
        getEntityStoreRegistry().registerSystem(
            new CivBuildingBlockProtectionSystem.PlaceProtection(buildingRegistry)
        );
        getEntityStoreRegistry().registerSystem(new CivClaimDamageSystem(rtsInteractionController));
        getEntityStoreRegistry().registerSystem(new CivInhabitantUseSystem(rtsInteractionController));
        getEntityStoreRegistry().registerSystem(
            new CivManualMovementSystem(unitRegistry, activityRegistry)
        );

        FarmNpcWorkSystem farmNpcWorkSystem =
            new FarmNpcWorkSystem(unitRegistry, activityRegistry, farmRegistry, fieldRegistry);
        WoodcutterWorkSystem woodcutterWorkSystem =
            new WoodcutterWorkSystem(unitRegistry, activityRegistry, woodcutterScanDiagnostics);
        MinerWorkSystem minerWorkSystem =
            new MinerWorkSystem(unitRegistry, activityRegistry, buildingRegistry, mineTunnelRegistry);
        ConstructionWorkSystem constructionWorkSystem =
            new ConstructionWorkSystem(
                unitRegistry,
                activityRegistry,
                farmRegistry,
                fieldRegistry,
                buildingRegistry,
                prefabPlacementService,
                buildingPersistence
            );
        SoldierWorkSystem soldierWorkSystem =
            new SoldierWorkSystem(unitRegistry, activityRegistry);

        getEntityStoreRegistry().registerSystem(farmNpcWorkSystem);
        getEntityStoreRegistry().registerSystem(woodcutterWorkSystem);
        getEntityStoreRegistry().registerSystem(minerWorkSystem);
        getEntityStoreRegistry().registerSystem(constructionWorkSystem);
        getEntityStoreRegistry().registerSystem(soldierWorkSystem);
        getEntityStoreRegistry().registerSystem(
            new CivInhabitantLifecycleSystem(
                inhabitantDataType,
                inhabitantService,
                unitRegistry,
                activityRegistry,
                farmRegistry,
                farmNpcWorkSystem,
                woodcutterWorkSystem,
                minerWorkSystem,
                constructionWorkSystem,
                soldierWorkSystem
            )
        );

        CivNameplateStatusSystem nameplateStatusSystem =
            new CivNameplateStatusSystem(unitRegistry, activityRegistry);
        getEntityStoreRegistry().registerSystem(nameplateStatusSystem);
        getEntityStoreRegistry().registerSystem(
            new CivSelectedNpcHudSystem(selectedNpcHudController)
        );

        getCommandRegistry().registerCommand(new CivTestCommand());
        if (Boolean.getBoolean("civilizations.runtimeProbe")) {
            getCommandRegistry().registerCommand(
                new CivRuntimeProbeCommand(unitRegistry, activityRegistry)
            );
            getCommandRegistry().registerCommand(
                new CivWoodcutterFixtureProbeCommand(unitRegistry)
            );
            getCommandRegistry().registerCommand(
                new CivPersistenceProbeCommand(unitRegistry)
            );
            getCommandRegistry().registerCommand(
                new CivWarmRuntimeBenchmarkCommand(unitRegistry, activityRegistry)
            );
            getCommandRegistry().registerCommand(
                new CivSoldierFixtureProbeCommand(unitRegistry, activityRegistry, soldierWorkSystem)
            );
        }
        getCommandRegistry().registerCommand(
            new CivRtsTestCommand(rtsInteractionController, selectedNpcHudController)
        );
        getCommandRegistry().registerCommand(new CivClaimCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(new CivFarmCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(new CivBuildCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(new CivBuildCancelCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(new CivWikiCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(
            new CivDebugCommand(
                buildingRegistry,
                buildingPersistence,
                pathDebugService,
                woodcutterScanDiagnostics,
                activityRegistry,
                nameplateStatusSystem,
                playerRigDebugService
            )
        );

        getEventRegistry().registerGlobal(StartWorldEvent.class, event -> {
            rtsInteractionController.handleWorldJoin(event.getWorld());
            mineTunnelRegistry.loadWorld(event.getWorld());
        });
        getEventRegistry().registerGlobal(TriggerVolumeEvent.class, event -> {
            if (event.getTriggerEventType() == TriggerEventType.ENTER) {
                farmNpcWorkSystem.handleTriggerEnter(event.getEntityRef(), event.getVolumeId());
            }
        });
        getEventRegistry().register(PlayerMouseButtonEvent.class, selectedNpcHudController::handleMouseButton);
        getEventRegistry().register(PlayerMouseButtonEvent.class, rtsInteractionController::handleMouseButton);
        getEventRegistry().register(PlayerMouseMotionEvent.class, rtsInteractionController::handleMouseMotion);
        getEventRegistry().register(PlayerDisconnectEvent.class, selectedNpcHudController::handleDisconnect);
        getEventRegistry().register(PlayerDisconnectEvent.class, rtsInteractionController::handleDisconnect);
    }
}
