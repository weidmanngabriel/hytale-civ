package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.hytale.RtsInteractionController;

final class CivFarmCommand extends AbstractPlayerCommand {

    private final RtsInteractionController interactionController;

    CivFarmCommand(RtsInteractionController interactionController) {
        super("civfarm", "Arms placement of the Hytale Civ farm prefab.");
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
        interactionController.armFarmPlacement(playerRef);
    }
}
