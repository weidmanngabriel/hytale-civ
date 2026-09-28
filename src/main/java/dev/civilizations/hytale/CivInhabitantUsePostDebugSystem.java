package dev.civilizations.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.UseEntityEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;

/**
 * Temporary diagnostic system for the post phase of NPC entity-use interactions.
 */
public final class CivInhabitantUsePostDebugSystem
    extends EntityEventSystem<EntityStore, UseEntityEvent.Post> {

    private final RtsInteractionController interactionController;

    public CivInhabitantUsePostDebugSystem(RtsInteractionController interactionController) {
        super(UseEntityEvent.Post.class);
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
        @Nonnull UseEntityEvent.Post event
    ) {
        Ref<EntityStore> target = event.getTargetEntity();
        boolean targetValid = target != null && target.isValid();

        System.out.println(
            "[CIV-DEBUG] UseEntityEvent.Post"
                + " type=" + event.getInteractionType()
                + " target=" + (targetValid ? "npc" : "missing-or-invalid")
                + " claimed=" + (targetValid && interactionController.isClaimed(target))
        );
    }
}
