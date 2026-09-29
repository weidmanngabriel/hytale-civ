package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.hytale.BuildingPlacementRegistry;
import dev.civilizations.hytale.CivBuildingPersistenceService;

import java.util.List;

final class CivDebugCommand extends AbstractPlayerCommand {
    private final BuildingPlacementRegistry buildingRegistry;
    private final CivBuildingPersistenceService buildingPersistence;

    CivDebugCommand(BuildingPlacementRegistry buildingRegistry, CivBuildingPersistenceService buildingPersistence) {
        super("civdebug", "Shows read-only Civilizations development diagnostics.");
        this.buildingRegistry = buildingRegistry;
        this.buildingPersistence = buildingPersistence;
        requireNoPermission();
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        List<BuildingPlacementRegistry.BuildingInstance> runtime =
            buildingRegistry.buildings(world.getWorldConfig().getUuid());
        List<BuildingPlacementRegistry.BuildingInstance> persisted = buildingPersistence.load(world);
        context.sendMessage(Message.raw("Civ debug buildings: runtime=" + runtime.size() + ", persisted=" + persisted.size()));
        if (persisted.isEmpty()) {
            context.sendMessage(Message.raw("Keine persistenten Civ-Gebäude gespeichert."));
            return;
        }
        for (BuildingPlacementRegistry.BuildingInstance building : persisted) {
            int snapshotBlocks = building.placement() == null ? 0 : building.placement().replacedFloorBlocks().size();
            String volumes = building.semanticVolumes().stream()
                .map(marker -> marker.tags().getOrDefault("civ.type", "?"))
                .distinct().sorted().reduce((left, right) -> left + "," + right).orElse("-");
            context.sendMessage(Message.raw(
                building.buildingType() + " | id=" + building.id()
                    + " | snapshot=" + snapshotBlocks + " blocks"
                    + " | volumes=" + building.semanticVolumes().size() + " [" + volumes + "]"
            ));
        }
    }
}
