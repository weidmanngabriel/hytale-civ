package dev.civilizations.hytale;

import com.hypixel.hytale.builtin.adventure.farming.FarmingUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.protocol.BlockRotation;
import com.hypixel.hytale.protocol.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.civilizations.core.FarmBuilding;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.Map;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Translates the core farm work state into native Hytale planting, growth,
 * harvesting, movement and storage operations.
 *
 * <p>Hytale navigation and trigger-volume events keep movement responsive between Civ work
 * sessions. The system itself intentionally runs in short delayed sessions instead of once for
 * every farmer on every engine tick.</p>
 */
public final class FarmNpcWorkSystem extends DelayedEntitySystem<EntityStore> {

    private static final Logger LOGGER = Logger.getLogger(FarmNpcWorkSystem.class.getName());
    private static final float TICK_INTERVAL_SECONDS = 0.75f;
    private static final double ARRIVAL_DISTANCE = 0.45;
    private static final String WHEAT_ITEM_ID = "Plant_Crop_Wheat_Item";
    private static final String WHEAT_SEED_ITEM_ID = "Plant_Seeds_Wheat";
    private static final BlockRotation DEFAULT_BLOCK_ROTATION =
        new BlockRotation(Rotation.None, Rotation.None, Rotation.None);
    private static final Vector3i UP = new Vector3i(0, 1, 0);

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final FarmBuildingRegistry farmRegistry;
    private final FarmFieldRegistry fieldRegistry;
    private final NativeBuildingStorage storage = new NativeBuildingStorage();
    private final FarmPlantingService plantingService = new FarmPlantingService();
    private final Map<CivUnitRegistry.UnitKey, FarmFieldRegistry.FieldSite> activeFields =
        new ConcurrentHashMap<>();
    private final Map<CivUnitRegistry.UnitKey, AccessRoute> accessRoutes =
        new ConcurrentHashMap<>();
    private final Map<CivUnitRegistry.UnitKey, Boolean> plantedInCurrentPass =
        new ConcurrentHashMap<>();

    public FarmNpcWorkSystem(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry,
        FarmBuildingRegistry farmRegistry,
        FarmFieldRegistry fieldRegistry
    ) {
        super(TICK_INTERVAL_SECONDS);
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
        this.farmRegistry = farmRegistry;
        this.fieldRegistry = fieldRegistry;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return NPCEntity.getComponentType();
    }

    @Override
    public void tick(
        float dt,
        int index,
        ArchetypeChunk<EntityStore> archetypeChunk,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        CivUnitRegistry.UnitKey key = unitRegistry.keyOf(ref);
        FarmBuildingRegistry.FarmSite site = farmRegistry.getAssignment(ref);

        if (site == null) {
            forgetFarmRuntime(key);
            return;
        }

        if (!ref.isValid()) {
            forgetFarmRuntime(key);
            farmRegistry.unassignFarmer(ref);
            activityRegistry.forget(ref);
            unitRegistry.forget(ref);
            return;
        }

        TransformComponent transform =
            commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            forgetFarmRuntime(key);
            farmRegistry.unassignFarmer(ref);
            return;
        }

        FarmBuilding building = site.building();
        Vector3d position = transform.getPosition();
        if (!activityRegistry.autonomousWorkAllowed(ref)) {
            return;
        }

        switch (building.workState()) {
            case WAITING_FOR_FARMER, WAITING_FOR_INPUTS -> unitRegistry.clearMoveTarget(ref);
            case WALKING_TO_FARM -> {
                Vector3d target = site.entranceTarget();
                unitRegistry.setMoveTarget(ref, target);
                if (hasArrived(position, target)) {
                    unitRegistry.clearMoveTarget(ref);
                    building.arriveAtFarm();
                }
            }
            case WALKING_TO_FIELD -> walkToField(ref, key, site, building, position);
            case SOWING_FIELD -> {
                unitRegistry.clearMoveTarget(ref);
                FarmFieldRegistry.FieldSite field = requireActiveField(ref, key, site, building);
                if (field == null) break;

                PlantResult result = plantOneSeed(ref, field, commandBuffer);
                if (result == PlantResult.PLANTED) {
                    plantedInCurrentPass.put(key, true);
                } else if (result == PlantResult.NO_MORE_WORK || result == PlantResult.UNSUPPORTED) {
                    boolean plantedAny = plantedInCurrentPass.remove(key) != null;
                    if (result == PlantResult.UNSUPPORTED) {
                        LOGGER.info("[CivFarm] Farmer paused because native wheat planting is currently unavailable.");
                    }
                    building.sowingComplete(plantedAny || fieldHasCrop(site.worldId(), field));
                }
            }
            case WAITING_FOR_GROWTH -> {
                unitRegistry.clearMoveTarget(ref);
                FarmFieldRegistry.FieldSite field = requireActiveField(ref, key, site, building);
                if (field == null) break;

                if (!fieldHasCrop(site.worldId(), field)) {
                    building.fieldCycleFinished();
                    break;
                }
                if (harvestOneRipeCrop(ref, field, commandBuffer)) {
                    accessRoutes.put(key, AccessRoute.TO_STORAGE);
                    building.cropHarvested();
                }
            }
            case HARVESTING_FIELD -> {
                unitRegistry.clearMoveTarget(ref);
                FarmFieldRegistry.FieldSite field = requireActiveField(ref, key, site, building);
                if (field == null) break;

                if (!fieldHasCrop(site.worldId(), field)) {
                    building.fieldCycleFinished();
                } else if (harvestOneRipeCrop(ref, field, commandBuffer)) {
                    accessRoutes.put(key, AccessRoute.TO_STORAGE);
                    building.cropHarvested();
                } else {
                    building.noRipeCropYet();
                }
            }
            case RETURNING_TO_STORAGE -> walkToStorage(ref, key, site, building, position);
            case STORING_OUTPUT -> {
                unitRegistry.clearMoveTarget(ref);
                World world = Universe.get().getWorld(site.worldId());
                InventorySlot wheat = findInventoryItem(ref, WHEAT_ITEM_ID);
                if (wheat != null
                    && storage.tryStore(
                        world,
                        site.outputStorageMarker(),
                        new ItemStack(WHEAT_ITEM_ID, 1)
                    )) {
                    wheat.container().removeItemStackFromSlot(
                        wheat.slot(),
                        1,
                        true,
                        true
                    );
                    building.outputStored();
                }
            }
        }
    }

    public void forgetRuntime(Ref<EntityStore> ref) {
        if (ref != null) {
            forgetFarmRuntime(unitRegistry.keyOf(ref));
        }
    }

    private void walkToField(
        Ref<EntityStore> ref,
        CivUnitRegistry.UnitKey key,
        FarmBuildingRegistry.FarmSite site,
        FarmBuilding building,
        Vector3d position
    ) {
        if (accessRoutes.get(key) == AccessRoute.TO_FIELD) {
            Vector3d target = site.entranceTarget();
            unitRegistry.setMoveTarget(ref, target);
            if (hasArrived(position, target)) {
                accessRoutes.remove(key);
                unitRegistry.clearMoveTarget(ref);
            }
            return;
        }

        FarmFieldRegistry.FieldSite field = requireActiveField(ref, key, site, building);
        if (field == null) return;

        Vector3d target = field.workTarget();
        unitRegistry.setMoveTarget(ref, target);
        if (hasArrived(position, target)) {
            unitRegistry.clearMoveTarget(ref);
            building.arriveAtField();
        }
    }

    private void walkToStorage(
        Ref<EntityStore> ref,
        CivUnitRegistry.UnitKey key,
        FarmBuildingRegistry.FarmSite site,
        FarmBuilding building,
        Vector3d position
    ) {
        if (accessRoutes.get(key) == AccessRoute.TO_STORAGE) {
            Vector3d target = site.entranceTarget();
            unitRegistry.setMoveTarget(ref, target);
            if (hasArrived(position, target)) {
                accessRoutes.remove(key);
                unitRegistry.clearMoveTarget(ref);
            }
            return;
        }

        Vector3d target = site.outputStorageTarget();
        unitRegistry.setMoveTarget(ref, target);
        if (hasArrived(position, target)) {
            unitRegistry.clearMoveTarget(ref);
            building.arriveAtFarm();
        }
    }

    private FarmFieldRegistry.FieldSite requireActiveField(
        Ref<EntityStore> ref,
        CivUnitRegistry.UnitKey key,
        FarmBuildingRegistry.FarmSite site,
        FarmBuilding building
    ) {
        FarmFieldRegistry.FieldSite field = activeFields.get(key);
        if (!fieldRegistry.isRegistered(field)) {
            field = fieldRegistry.nearestField(site.worldId(), building.entranceBlock());
            if (field == null) {
                activeFields.remove(key);
                unitRegistry.clearMoveTarget(ref);
                return null;
            }
            activeFields.put(key, field);
        }
        return field;
    }

    private PlantResult plantOneSeed(
        Ref<EntityStore> ref,
        FarmFieldRegistry.FieldSite field,
        CommandBuffer<EntityStore> entityAccessor
    ) {
        InventorySlot seed = findInventoryItem(ref, WHEAT_SEED_ITEM_ID);
        if (seed == null) {
            LOGGER.warning("[CivFarm] Planting reached SOWING_FIELD but no wheat seed was found in Storage, Hotbar or Backpack");
            return PlantResult.NO_MORE_WORK;
        }

        World world = Universe.get().getWorld(field.worldId());
        if (world == null) {
            LOGGER.warning("[CivFarm] Planting aborted: farm world is not loaded: " + field.worldId());
            return PlantResult.NO_MORE_WORK;
        }

        Store<ChunkStore> chunkAccessor = world.getChunkStore().getStore();
        for (Vector3i soil : fieldSoilPositions(field)) {
            Vector3i crop = new Vector3i(soil).add(UP);
            Ref<ChunkStore> cropSection =
                world.getChunkStore().getChunkSectionReferenceAtBlock(crop.x, crop.y, crop.z);
            if (cropSection == null || !cropSection.isValid()) continue;
            BlockSection section = chunkAccessor.getComponent(
                cropSection,
                BlockSection.getComponentType()
            );
            if (section == null || section.get(crop.x, crop.y, crop.z) != BlockType.EMPTY_ID) {
                continue;
            }

            Ref<ChunkStore> soilSection =
                world.getChunkStore().getChunkSectionReferenceAtBlock(soil.x, soil.y, soil.z);
            if (soilSection == null || !soilSection.isValid()) continue;
            BlockSection soilBlocks = chunkAccessor.getComponent(
                soilSection,
                BlockSection.getComponentType()
            );
            int soilBlockId = soilBlocks == null
                ? -1
                : soilBlocks.get(soil.x, soil.y, soil.z);
            BlockType soilBlockType = soilBlockId < 0
                ? null
                : BlockType.getAssetMap().getAsset(soilBlockId);
            if (soilBlockType == null || soilBlockType.getFarming() == null) {
                continue;
            }

            FarmPlantingService.Result result = plantingService.plantWheat(
                ref,
                world,
                soil,
                seed.stack(),
                entityAccessor
            );
            if (result == FarmPlantingService.Result.PLANTED) {
                return PlantResult.PLANTED;
            }
            if (result == FarmPlantingService.Result.UNSUPPORTED) {
                LOGGER.info("[CivFarm] Wheat planting is currently unavailable: Hytale exposes no verified native server-side NPC seed-placement API for this runtime. Farmer remains idle until a supported engine path is available.");
                return PlantResult.UNSUPPORTED;
            }
            LOGGER.warning("[CivFarm] Native wheat planting failed at soil=" + soil);
            return PlantResult.NO_MORE_WORK;
        }

        LOGGER.warning("[CivFarm] Wheat seed is available but no valid empty farming position was found in field volume: "
            + field.fieldBounds());
        return PlantResult.NO_MORE_WORK;
    }

    private boolean harvestOneRipeCrop(
        Ref<EntityStore> ref,
        FarmFieldRegistry.FieldSite field,
        CommandBuffer<EntityStore> entityAccessor
    ) {
        World world = Universe.get().getWorld(field.worldId());
        if (world == null) return false;
        Store<ChunkStore> chunkAccessor = world.getChunkStore().getStore();

        for (Vector3i soil : fieldSoilPositions(field)) {
            Vector3i crop = new Vector3i(soil).add(UP);
            if (FarmingUtil.harvest(chunkAccessor, entityAccessor, ref, crop)) {
                return true;
            }
        }
        return false;
    }

    private boolean fieldHasCrop(java.util.UUID worldId, FarmFieldRegistry.FieldSite field) {
        World world = Universe.get().getWorld(worldId);
        if (world == null) return false;
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> accessor = chunkStore.getStore();

        for (Vector3i soil : fieldSoilPositions(field)) {
            Vector3i crop = new Vector3i(soil).add(UP);
            Ref<ChunkStore> sectionRef =
                chunkStore.getChunkSectionReferenceAtBlock(crop.x, crop.y, crop.z);
            if (sectionRef == null || !sectionRef.isValid()) continue;
            BlockSection section = accessor.getComponent(sectionRef, BlockSection.getComponentType());
            if (section == null) continue;
            BlockType blockType = BlockType.getAssetMap().getAsset(
                section.get(crop.x, crop.y, crop.z)
            );
            if (blockType != null && blockType.getFarming() != null) {
                return true;
            }
        }
        return false;
    }

    private static java.util.List<Vector3i> fieldSoilPositions(
        FarmFieldRegistry.FieldSite field
    ) {
        dev.civilizations.core.BuildingBounds bounds = field.fieldBounds();
        if (bounds == null) {
            LOGGER.warning("[CivFarm] Field has no bounds; skipping soil scan: field="
                + field.id());
            return java.util.List.of();
        }
        java.util.List<Vector3i> positions = new java.util.ArrayList<>();
        int minX = (int) Math.floor(bounds.minX());
        int maxX = (int) Math.ceil(bounds.maxX()) - 1;
        int minZ = (int) Math.floor(bounds.minZ());
        int maxZ = (int) Math.ceil(bounds.maxZ()) - 1;
        int soilY = (int) Math.floor(bounds.minY());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                positions.add(new Vector3i(x, soilY, z));
            }
        }
        return positions;
    }

    private static InventorySlot findInventoryItem(
        Ref<EntityStore> ref,
        String itemId
    ) {
        InventorySlot found = findIn(ref, InventoryComponent.Storage.getComponentType(), itemId);
        if (found != null) return found;
        found = findIn(ref, InventoryComponent.Hotbar.getComponentType(), itemId);
        if (found != null) return found;
        return findIn(ref, InventoryComponent.Backpack.getComponentType(), itemId);
    }

    private static <T extends InventoryComponent> InventorySlot findIn(
        Ref<EntityStore> ref,
        com.hypixel.hytale.component.ComponentType<EntityStore, T> type,
        String itemId
    ) {
        T component = ref.getStore().getComponent(ref, type);
        if (component == null || component.getInventory() == null) return null;
        ItemContainer inventory = component.getInventory();
        for (short slot = 0; slot < inventory.getCapacity(); slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (stack != null && itemId.equals(stack.getItemId()) && stack.getQuantity() > 0) {
                return new InventorySlot(inventory, slot, stack);
            }
        }
        return null;
    }

    public void handleTriggerEnter(Ref<EntityStore> ref, String volumeId) {
        if (ref == null || !ref.isValid() || volumeId == null) return;
        FarmBuildingRegistry.FarmSite site = farmRegistry.getAssignment(ref);
        if (site == null || !activityRegistry.autonomousWorkAllowed(ref)) return;

        FarmBuilding building = site.building();
        CivUnitRegistry.UnitKey key = unitRegistry.keyOf(ref);
        switch (building.workState()) {
            case WALKING_TO_FARM -> {
                if (site.hasEntranceVolume(volumeId)) {
                    unitRegistry.clearMoveTarget(ref);
                    building.arriveAtFarm();
                }
            }
            case RETURNING_TO_STORAGE -> {
                if (site.hasEntranceVolume(volumeId)
                    && accessRoutes.get(key) == AccessRoute.TO_STORAGE) {
                    accessRoutes.remove(key);
                    unitRegistry.clearMoveTarget(ref);
                } else if (site.hasOutputStorageVolume(volumeId)
                    && accessRoutes.get(key) != AccessRoute.TO_STORAGE) {
                    unitRegistry.clearMoveTarget(ref);
                    building.arriveAtFarm();
                }
            }
            case WALKING_TO_FIELD -> {
                if (site.hasEntranceVolume(volumeId)
                    && accessRoutes.get(key) == AccessRoute.TO_FIELD) {
                    accessRoutes.remove(key);
                    unitRegistry.clearMoveTarget(ref);
                    break;
                }
                if (accessRoutes.get(key) == AccessRoute.TO_FIELD) break;
                FarmFieldRegistry.FieldSite field = activeFields.get(key);
                if (field != null && volumeId.equals(field.workVolumeId())) {
                    unitRegistry.clearMoveTarget(ref);
                    building.arriveAtField();
                }
            }
            default -> {
                // Other farming phases are advanced by native world state.
            }
        }
    }

    private void forgetFarmRuntime(CivUnitRegistry.UnitKey key) {
        activeFields.remove(key);
        accessRoutes.remove(key);
        plantedInCurrentPass.remove(key);
    }

    private enum AccessRoute {
        TO_STORAGE,
        TO_FIELD
    }

    private enum PlantResult {
        PLANTED,
        NO_MORE_WORK,
        UNSUPPORTED
    }

    private record InventorySlot(ItemContainer container, short slot, ItemStack stack) {
    }

    private static boolean hasArrived(Vector3d position, Vector3d target) {
        double dx = position.x - target.x;
        double dz = position.z - target.z;
        return dx * dx + dz * dz <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
    }
}
