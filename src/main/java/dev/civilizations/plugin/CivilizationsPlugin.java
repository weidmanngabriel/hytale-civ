package dev.civilizations.plugin;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.builtin.triggervolumes.event.TriggerVolumeEvent;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEventType;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.VikingNameGenerator;
import dev.civilizations.hytale.CivActivityRegistry;
import dev.civilizations.hytale.CivClaimDamageSystem;
import dev.civilizations.hytale.CivBuildingBlockProtectionSystem;
import dev.civilizations.hytale.CivBuildingDataResource;
import dev.civilizations.hytale.CivBuildingPersistenceService;
import dev.civilizations.hytale.CivInhabitantData;
import dev.civilizations.hytale.CivInhabitantService;
import dev.civilizations.hytale.CivInhabitantUseSystem;
import dev.civilizations.hytale.CivManualMovementSystem;
import dev.civilizations.hytale.CivUnitRegistry;
import dev.civilizations.hytale.ConstructionWorkSystem;
import dev.civilizations.hytale.BuildingPlacementRegistry;
import dev.civilizations.hytale.FarmBuildingRegistry;
import dev.civilizations.hytale.FarmFieldRegistry;
import dev.civilizations.hytale.FarmNpcWorkSystem;
import dev.civilizations.hytale.PrefabPlacementService;
import dev.civilizations.hytale.RtsCameraController;
import dev.civilizations.hytale.RtsInteractionController;
import dev.civilizations.hytale.WoodcutterWorkSystem;

public final class CivilizationsPlugin extends JavaPlugin {

    private static final String CIV_INHABITANT_DATA_ID = "CivInhabitantData";
    private static final String CIV_BUILDING_DATA_ID = "CivBuildingData";

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
        CivBuildingPersistenceService buildingPersistence =
            new CivBuildingPersistenceService(buildingDataType);

        CivInhabitantService inhabitantService = new CivInhabitantService(
            inhabitantDataType,
            new VikingNameGenerator()
        );
        CivUnitRegistry unitRegistry = new CivUnitRegistry(inhabitantService);
        CivActivityRegistry activityRegistry = new CivActivityRegistry(unitRegistry);
        FarmBuildingRegistry farmRegistry = new FarmBuildingRegistry(unitRegistry);
        FarmFieldRegistry fieldRegistry = new FarmFieldRegistry();
        BuildingPlacementRegistry buildingRegistry = new BuildingPlacementRegistry();
        PrefabPlacementService prefabPlacementService = new PrefabPlacementService();
        RtsInteractionController rtsInteractionController =
            new RtsInteractionController(
                new RtsCameraController(),
                unitRegistry,
                activityRegistry,
                farmRegistry,
                fieldRegistry,
                buildingRegistry,
                prefabPlacementService,
                buildingPersistence
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
        getEntityStoreRegistry().registerSystem(farmNpcWorkSystem);
        getEntityStoreRegistry().registerSystem(
            new WoodcutterWorkSystem(unitRegistry, activityRegistry)
        );
        getEntityStoreRegistry().registerSystem(
            new ConstructionWorkSystem(
                unitRegistry,
                activityRegistry,
                farmRegistry,
                fieldRegistry,
                buildingRegistry,
                prefabPlacementService,
                buildingPersistence
            )
        );

        getCommandRegistry().registerCommand(new CivTestCommand());
        getCommandRegistry().registerCommand(new CivRtsTestCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(new CivClaimCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(new CivFarmCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(new CivBuildCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(new CivBuildCancelCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(new CivWikiCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(new CivDebugCommand(buildingRegistry, buildingPersistence));

        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> rtsInteractionController.handleWorldJoin(event.getWorld()));
        getEventRegistry().registerGlobal(TriggerVolumeEvent.class, event -> {
            if (event.getTriggerEventType() == TriggerEventType.ENTER) {
                farmNpcWorkSystem.handleTriggerEnter(event.getEntityRef(), event.getVolumeId());
            }
        });
        getEventRegistry().register(PlayerMouseButtonEvent.class, rtsInteractionController::handleMouseButton);
        getEventRegistry().register(PlayerMouseMotionEvent.class, rtsInteractionController::handleMouseMotion);
        getEventRegistry().register(PlayerDisconnectEvent.class, rtsInteractionController::handleDisconnect);
    }
}
