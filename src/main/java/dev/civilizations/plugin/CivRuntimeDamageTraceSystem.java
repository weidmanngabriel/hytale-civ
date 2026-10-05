package dev.civilizations.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;

/** Runtime-probe-only observer for native Hytale NPC damage events. */
final class CivRuntimeDamageTraceSystem extends EntityEventSystem<EntityStore, Damage> {

    CivRuntimeDamageTraceSystem() {
        super(Damage.class);
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
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        Ref<EntityStore> victim = chunk.getReferenceTo(index);
        Ref<EntityStore> attacker = null;
        if (damage.getSource() instanceof Damage.EntitySource entitySource) {
            attacker = entitySource.getRef();
        }

        System.out.println(
            "CIV_RUNTIME_DAMAGE"
                + " victim=" + victim.getIndex()
                + " attacker=" + (attacker != null && attacker.isValid() ? attacker.getIndex() : -1)
                + " amount=" + damage.getAmount()
                + " initialAmount=" + damage.getInitialAmount()
                + " cause=" + damage.getCause()
                + " cancelled=" + damage.isCancelled()
                + " victimHealth=" + health(commandBuffer, victim)
        );
    }

    private static float health(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> ref) {
        EntityStatMap stats = commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        return health == null ? Float.NaN : health.get();
    }
}
