package dev.civilizations.plugin;

import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.civilizations.hytale.CivNpcMovementSystem;
import dev.civilizations.hytale.CivUnitRegistry;
import dev.civilizations.hytale.RtsCameraController;
import dev.civilizations.hytale.RtsInteractionController;

public final class CivilizationsPlugin extends JavaPlugin {

    public CivilizationsPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    public void setup() {
        CivUnitRegistry unitRegistry = new CivUnitRegistry();
        RtsInteractionController rtsInteractionController =
            new RtsInteractionController(new RtsCameraController(), unitRegistry);

        getEntityStoreRegistry().registerSystem(new CivNpcMovementSystem(unitRegistry));

        getCommandRegistry().registerCommand(new CivTestCommand());
        getCommandRegistry().registerCommand(new CivRtsTestCommand(rtsInteractionController));
        getCommandRegistry().registerCommand(new CivClaimCommand(rtsInteractionController));

        getEventRegistry().register(PlayerMouseButtonEvent.class, rtsInteractionController::handleMouseButton);
        getEventRegistry().register(PlayerDisconnectEvent.class, rtsInteractionController::handleDisconnect);
    }
}
