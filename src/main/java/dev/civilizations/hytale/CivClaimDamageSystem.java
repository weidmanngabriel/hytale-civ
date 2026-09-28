package dev.civilizations.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;

/**
 * First Person claim path. An armed player attack against an NPC is consumed as a Civ claim
 * before damage is applied, then delegated to the same claim handler used by RTS.
 */
public final class CivClaimDamageSystem extends EntityEventSystem<EntityStore, Damage> {
    private final RtsInteractionController interactionController;

    public CivClaimDamageSystem(RtsInteractionController interactionController) {
        super(Damage.class);
        this.interactionController = interactionController;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return NPCEntity.getComponentType();
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage) {
        if (damage.isCancelled() || !(damage.getSource() instanceof Damage.EntitySource source)) return;
        Ref<EntityStore> attackerRef = source.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;
        Player player = commandBuffer.getComponent(attackerRef, Player.getComponentType());
        PlayerRef playerRef = commandBuffer.getComponent(attackerRef, PlayerRef.getComponentType());
        if (player == null || playerRef == null || !interactionController.consumeArmedClaim(playerRef)) return;

        damage.setCancelled(true);
        interactionController.handleClaim(chunk.getReferenceTo(index), playerRef);
    }
}
