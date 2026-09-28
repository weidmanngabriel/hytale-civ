package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.hytale.RtsInteractionController;

final class CivBuildCommand extends AbstractPlayerCommand {

    private final RtsInteractionController interactionController;

    CivBuildCommand(RtsInteractionController interactionController) {
        super("civbuild", "Opens the Civilizations building menu.");
        this.interactionController = interactionController;
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
        interactionController.openBuildingMenu(playerRef, ref, store);
    }
}
