package dev.civilizations.plugin;

import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.civilizations.hytale.RtsCameraController;
import dev.civilizations.hytale.RtsInteractionController;

public final class CivilizationsPlugin extends JavaPlugin {

    public CivilizationsPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    public void setup() {
        RtsInteractionController rtsInteractionController =
            new RtsInteractionController(new RtsCameraController());

        getCommandRegistry().registerCommand(new CivTestCommand());
        getCommandRegistry().registerCommand(new CivRtsTestCommand(rtsInteractionController));
        getEventRegistry().register(PlayerMouseButtonEvent.class, rtsInteractionController::handleMouseButton);
    }
}
