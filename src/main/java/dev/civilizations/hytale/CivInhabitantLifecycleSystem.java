package dev.civilizations.hytale;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

/**
 * Event-driven bridge for Civ inhabitant entity lifecycle cleanup and rehydration.
 *
 * <p>No polling is performed. Runtime-only state is forgotten when Hytale removes an entity from
 * the active ECS store. Persistent {@link CivInhabitantData} is never deleted on UNLOAD; Hytale
 * owns persistence and restores it when the entity loads again.</p>
 */
public final class CivInhabitantLifecycleSystem extends RefSystem<EntityStore> {
    private final Query<EntityStore> query;
    private final CivInhabitantService inhabitantService;
    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final FarmBuildingRegistry farmRegistry;
    private final FarmNpcWorkSystem farmWorkSystem;
    private final WoodcutterWorkSystem woodcutterWorkSystem;
    private final ConstructionWorkSystem constructionWorkSystem;

    public CivInhabitantLifecycleSystem(
        ComponentType<EntityStore, CivInhabitantData> inhabitantDataType,
        CivInhabitantService inhabitantService,
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry,
        FarmBuildingRegistry farmRegistry,
        FarmNpcWorkSystem farmWorkSystem,
        WoodcutterWorkSystem woodcutterWorkSystem,
        ConstructionWorkSystem constructionWorkSystem
    ) {
        this.query = Archetype.of(inhabitantDataType, NPCEntity.getComponentType());
        this.inhabitantService = inhabitantService;
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
        this.farmRegistry = farmRegistry;
        this.farmWorkSystem = farmWorkSystem;
        this.woodcutterWorkSystem = woodcutterWorkSystem;
        this.constructionWorkSystem = constructionWorkSystem;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void onEntityAdded(
        Ref<EntityStore> ref,
        AddReason reason,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        // LOAD and SPAWN both need the runtime Player presentation rebuilt from persisted Civ data.
        inhabitantService.ensureInhabitant(ref, commandBuffer);
        // The persistent component is the authority for Civ membership. Re-register every loaded
        // inhabitant so workplace lookups do not depend on a player interacting with it first.
        unitRegistry.trackLoaded(ref);
    }

    @Override
    public void onEntityRemove(
        Ref<EntityStore> ref,
        RemoveReason reason,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        // Worker state and reservations are active-memory state, so they are always discarded.
        // The persistent Civ component itself stays Hytale-owned and survives UNLOAD.
        farmWorkSystem.forgetRuntime(ref);
        woodcutterWorkSystem.forgetRuntime(ref);
        constructionWorkSystem.forgetRuntime(ref);
        activityRegistry.forget(ref);
        unitRegistry.forget(ref);

        // A chunk UNLOAD is not deletion. Keep the farm relationship intact so normal streaming
        // cannot silently unassign a farmer. Real removal (including /entityclean) releases it.
        if (reason != RemoveReason.UNLOAD) {
            farmRegistry.unassignFarmer(ref);
        }
    }
}
