package dev.civilizations.hytale;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Prevents direct player block edits inside completed Civ building bounds.
 *
 * <p>Demolition is intentionally not implemented here. A later Civ demolition command can
 * remove the building registration before changing its blocks.
 */
public final class CivBuildingBlockProtectionSystem {

    private CivBuildingBlockProtectionSystem() {
    }

    private static boolean allowsFarmingPlacement(
        BuildingPlacementRegistry.BuildingInstance building,
        PlaceBlockEvent event
    ) {
        if (!isWheatField(building) || !isAboveFieldFloor(building, event.getTargetBlock())) {
            return false;
        }
        return event.getItemInHand() != null
            && "Plant_Seeds_Wheat".equals(event.getItemInHand().getItemId());
    }

    private static boolean isWheatField(BuildingPlacementRegistry.BuildingInstance building) {
        return building.placement() != null
            && building.placement().definition() != null
            && PrefabPlacementService.WHEAT_FIELD.id().equals(
                building.placement().definition().id()
            );
    }

    private static boolean isAboveFieldFloor(
        BuildingPlacementRegistry.BuildingInstance building,
        org.joml.Vector3i target
    ) {
        return target != null
            && building.placement() != null
            && building.placement().footprint() != null
            && target.y > building.placement().footprint().floorY();
    }

    public static final class BreakProtection
        extends EntityEventSystem<EntityStore, BreakBlockEvent> {

        private final BuildingPlacementRegistry buildings;

        public BreakProtection(BuildingPlacementRegistry buildings) {
            super(BreakBlockEvent.class);
            this.buildings = buildings;
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }

        @Override
        public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull BreakBlockEvent event
        ) {
            PlayerRef player = commandBuffer.getComponent(
                chunk.getReferenceTo(index),
                PlayerRef.getComponentType()
            );
            if (player == null) {
                return;
            }
            BuildingPlacementRegistry.BuildingInstance building =
                buildings.findAt(player.getWorldUuid(), event.getTargetBlock());
            if (building != null) {
                event.setCancelled(true);
            }
        }
    }

    public static final class PlaceProtection
        extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

        private final BuildingPlacementRegistry buildings;

        public PlaceProtection(BuildingPlacementRegistry buildings) {
            super(PlaceBlockEvent.class);
            this.buildings = buildings;
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }

        @Override
        public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull PlaceBlockEvent event
        ) {
            PlayerRef player = commandBuffer.getComponent(
                chunk.getReferenceTo(index),
                PlayerRef.getComponentType()
            );
            if (player == null) {
                return;
            }
            BuildingPlacementRegistry.BuildingInstance building =
                buildings.findAt(player.getWorldUuid(), event.getTargetBlock());
            if (building != null && !allowsFarmingPlacement(building, event)) {
                event.setCancelled(true);
            }
        }
    }
}
