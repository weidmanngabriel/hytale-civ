package dev.civilizations.hytale;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.event.events.ecs.UseEntityEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * First-person adapter for Civ NPC claiming.
 *
 * <p>Hytale dispatches UseEntityEvent on the acting player. The clicked entity is carried by
 * the event, so this system deliberately uses an empty query and validates the target itself.</p>
 */
public final class CivClaimInteractionSystem
    extends EntityEventSystem<EntityStore, UseEntityEvent.Pre> {

    private final RtsInteractionController interactionController;

    public CivClaimInteractionSystem(RtsInteractionController interactionController) {
        super(UseEntityEvent.Pre.class);
        this.interactionController = interactionController;
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull UseEntityEvent.Pre event
    ) {
        if (event.isCancelled() || event.getInteractionType() != InteractionType.Use) {
            return;
        }

        Ref<EntityStore> playerEntity = archetypeChunk.getReferenceTo(index);
        PlayerRef playerRef = store.getComponent(playerEntity, PlayerRef.getComponentType());
        if (playerRef == null || !interactionController.consumeArmedClaim(playerRef)) {
            return;
        }

        Ref<EntityStore> target = event.getTargetEntity();
        interactionController.handleClaim(target, playerRef);
        event.setCancelled(true);
    }
}
