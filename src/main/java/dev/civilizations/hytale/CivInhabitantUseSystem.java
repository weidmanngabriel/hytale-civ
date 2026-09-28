package dev.civilizations.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseEntityEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Opens the existing Civ person-actions page when a claimed inhabitant is used in First Person.
 */
public final class CivInhabitantUseSystem
    extends EntityEventSystem<EntityStore, UseEntityEvent.Pre> {

    private final RtsInteractionController interactionController;

    public CivInhabitantUseSystem(RtsInteractionController interactionController) {
        super(UseEntityEvent.Pre.class);
        this.interactionController = interactionController;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull UseEntityEvent.Pre event
    ) {
        if (event.isCancelled() || event.getInteractionType() != InteractionType.Use) {
            return;
        }

        Ref<EntityStore> playerEntityRef = chunk.getReferenceTo(index);
        Player player = commandBuffer.getComponent(playerEntityRef, Player.getComponentType());
        PlayerRef playerRef = commandBuffer.getComponent(playerEntityRef, PlayerRef.getComponentType());
        Ref<EntityStore> target = event.getTargetEntity();
        if (player == null || playerRef == null || target == null || !target.isValid()) {
            return;
        }

        if (interactionController.openFirstPersonActions(
            playerEntityRef,
            playerRef,
            target,
            store
        )) {
            event.setCancelled(true);
        }
    }
}
