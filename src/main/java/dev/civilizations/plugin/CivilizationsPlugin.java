package dev.civilizations.plugin;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.VikingNameGenerator;
import dev.civilizations.hytale.CivClaimInteractionSystem;
import dev.civilizations.hytale.CivInhabitantData;
import dev.civilizations.hytale.CivInhabitantService;
import dev.civilizations.hytale.CivUnitRegistry;
import dev.civilizations.hytale.FarmBuildingRegistry;
import dev.civilizations.hytale.FarmNpcWorkSystem;
import dev.civilizations.hytale.FarmPrefabService;
import dev.civilizations.hytale.RtsCameraController;
import dev.civilizations.hytale.RtsInteractionController;
import dev.civilizations.hytale.WoodcutterWorkSystem;

public final class CivilizationsPlugin extends JavaPlugin {

    private static final String CIV_INHABITANT_DATA_ID = "CivInhabitantData";

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

        CivInhabitantService inhabitantService = new CivInhabitantService(
            inhabitantDataType,
            new VikingNameGenerator()
        );
        CivUnitRegistry unitRegistry = new CivUnitRegistry(inhabitantService);
        FarmBuildingRegistry farmRegistry = new FarmBuildingRegistry(unitRegistry);
        RtsInteractionController rtsInteractionController =
            new RtsInteractionController(
                new RtsCameraController(),
                unitRegistry,
                farmRegistry,
                new FarmPrefabService()
            );

        getEntityStoreRegistry().registerSystem(new CivClaimInteractionSystem(rtsInteractionController));
        getEntityStoreRegistry().registerSystem(new FarmNpcWorkSystem(unitRegistry, farmRegistry));
        getEntityStoreRegistry().registerSystem(new WoodcutterWorkSystem(unitRegistry));

        getCommandRegistry().registerCommand(new CivTestCommand());
        getCommandRegistry().registerCommand(new CivRtsTestCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(new CivClaimCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(new CivFarmCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(new CivBuildCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(new CivWikiCommand(rtsInteractionController));

        getEventRegistry().register(PlayerMouseButtonEvent.class, rtsInteractionController::handleMouseButton);
        getEventRegistry().register(PlayerMouseMotionEvent.class, rtsInteractionController::handleMouseMotion);
        getEventRegistry().register(PlayerDisconnectEvent.class, rtsInteractionController::handleDisconnect);
    }
}
