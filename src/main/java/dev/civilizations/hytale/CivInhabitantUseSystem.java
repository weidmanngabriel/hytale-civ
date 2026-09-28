package dev.civilizations.hytale;

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
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;

/**
 * Traces and handles the entity-use path for claimed inhabitants in First Person.
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
        return NPCEntity.getComponentType();
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull UseEntityEvent.Pre event
    ) {
        Ref<EntityStore> playerEntityRef = event.getContext().getEntity();
        PlayerRef playerRef = playerEntityRef == null || !playerEntityRef.isValid()
            ? null
            : commandBuffer.getComponent(playerEntityRef, PlayerRef.getComponentType());
        Ref<EntityStore> target = event.getTargetEntity();
        boolean targetValid = target != null && target.isValid();
        boolean claimed = targetValid && interactionController.isClaimed(target);

        System.out.println(
            "[CIV-DEBUG] UseEntityEvent.Pre"
                + " type=" + event.getInteractionType()
                + " cancelled=" + event.isCancelled()
                + " player=" + (playerRef == null ? "missing" : "present")
                + " target=" + (targetValid ? "npc" : "missing-or-invalid")
                + " claimed=" + claimed
        );

        if (event.isCancelled() || event.getInteractionType() != InteractionType.Use
            || playerRef == null || !targetValid) {
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
