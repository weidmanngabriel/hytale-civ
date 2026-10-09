package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.hytale.RtsInteractionController;

/** Opens the paged settlement overview. */
final class CivDashboardCommand extends AbstractPlayerCommand {
    private final RtsInteractionController controller;
    CivDashboardCommand(RtsInteractionController controller) {
        super("civ", "Öffnet die Civ-Siedlungsverwaltung.");
        this.controller = controller;
        requireNoPermission();
    }
    @Override
    protected void execute(CommandContext context, Store<EntityStore> store,
                           Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        controller.openDashboard(playerRef, ref, store);
    }
}
