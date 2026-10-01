package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.hytale.CivSelectedNpcHudController;
import dev.civilizations.hytale.RtsInteractionController;

final class CivRtsTestCommand extends AbstractPlayerCommand {

    private final RtsInteractionController interactionController;
    private final CivSelectedNpcHudController hudController;

    CivRtsTestCommand(
        RtsInteractionController interactionController,
        CivSelectedNpcHudController hudController
    ) {
        super("civrtstest", "Toggles the Civilizations RTS camera/input validation mode.");
        this.interactionController = interactionController;
        this.hudController = hudController;
        requireNoPermission();
    }

    @Override
    protected void execute(
        CommandContext context,
        Store<EntityStore> store,
        Ref<EntityStore> ref,
        PlayerRef playerRef,
        World world
    ) {
        boolean active = interactionController.toggle(playerRef);
        hudController.setRtsActive(playerRef, active);
    }
}
