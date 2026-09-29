package dev.civilizations.hytale;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.RoleDebugFlags;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Development-only adapter for Hytale's native NPC path visualization.
 *
 * <p>The service never computes a Civ path. It only toggles Hytale's {@link RoleDebugFlags#VisPath}
 * flag on loaded Civ inhabitants so the engine can render the path it is actually following.</p>
 */
public final class CivPathDebugService {

    private final Archetype<EntityStore> civNpcQuery;
    private final Set<Store<EntityStore>> enabledStores = ConcurrentHashMap.newKeySet();
    private final Set<EntityKey> visPathAddedByCiv = ConcurrentHashMap.newKeySet();

    public CivPathDebugService(ComponentType<EntityStore, CivInhabitantData> inhabitantDataType) {
        this.civNpcQuery = Archetype.of(inhabitantDataType, NPCEntity.getComponentType());
    }

    public ToggleResult toggle(Store<EntityStore> store) {
        boolean enabled = enabledStores.add(store);
        if (!enabled) {
            enabledStores.remove(store);
        }

        int[] matched = {0};
        int[] changed = {0};
        store.forEachChunk(civNpcQuery, (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
                if (npc == null || ref == null || !ref.isValid()) {
                    continue;
                }

                matched[0]++;
                EntityKey key = new EntityKey(store, ref.getIndex());
                if (enabled) {
                    EnumSet<RoleDebugFlags> current = npc.getRoleDebugFlags();
                    if (!current.contains(RoleDebugFlags.VisPath)) {
                        npc.setRoleDebugFlags(withPathFlag(current, true));
                        visPathAddedByCiv.add(key);
                        changed[0]++;
                    }
                } else if (visPathAddedByCiv.remove(key)) {
                    EnumSet<RoleDebugFlags> current = npc.getRoleDebugFlags();
                    if (current.contains(RoleDebugFlags.VisPath)) {
                        npc.setRoleDebugFlags(withPathFlag(current, false));
                        changed[0]++;
                    }
                }
            }
        });

        if (!enabled) {
            visPathAddedByCiv.removeIf(key -> key.store() == store);
        }
        return new ToggleResult(enabled, matched[0], changed[0]);
    }

    static EnumSet<RoleDebugFlags> withPathFlag(
        EnumSet<RoleDebugFlags> current,
        boolean enabled
    ) {
        EnumSet<RoleDebugFlags> updated = current.clone();
        if (enabled) {
            updated.add(RoleDebugFlags.VisPath);
        } else {
            updated.remove(RoleDebugFlags.VisPath);
        }
        return updated;
    }

    public record ToggleResult(boolean enabled, int matchedNpcCount, int changedNpcCount) {
    }

    private record EntityKey(Store<EntityStore> store, int entityIndex) {
    }
}
