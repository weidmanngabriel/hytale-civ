package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.FarmBuilding;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runtime bridge between placed Hytale farm prefabs and the core farm simulation.
 */
public final class FarmBuildingRegistry {

    private final CivUnitRegistry unitRegistry;
    private final AtomicLong nextFarmId = new AtomicLong(1);
    private final Map<FarmKey, FarmSite> farms = new ConcurrentHashMap<>();
    private final Map<CivUnitRegistry.UnitKey, FarmSite> farmerAssignments = new ConcurrentHashMap<>();

    public FarmBuildingRegistry(CivUnitRegistry unitRegistry) {
        this.unitRegistry = unitRegistry;
    }

    public FarmSite registerFarm(
        UUID worldId,
        UUID buildingInstanceId,
        List<Vector3i> entranceBlocks,
        PrefabPlacementService.PlacementFootprint footprint,
        Map<BlockPosition, Integer> replacedFloorBlocks
    ) {
        if (entranceBlocks == null || entranceBlocks.isEmpty()) {
            throw new IllegalArgumentException("A farm requires at least one entrance.");
        }

        List<BlockPosition> entrances = entranceBlocks.stream()
            .map(FarmBuildingRegistry::toCore)
            .toList();

        BlockPosition primaryEntrance = entrances.getFirst();
        BlockPosition exit = exteriorExitFor(primaryEntrance);
        FarmBuilding building = new FarmBuilding(
            "farm-" + nextFarmId.getAndIncrement(),
            primaryEntrance,
            exit
        );
        FarmSite site = new FarmSite(
            worldId,
            buildingInstanceId,
            building,
            entrances,
            footprint,
            replacedFloorBlocks
        );
        farms.put(new FarmKey(worldId, building.id()), site);
        return site;
    }

    public boolean overlaps(UUID worldId, PrefabPlacementService.PlacementFootprint footprint) {
        if (worldId == null || footprint == null) {
            return false;
        }

        return farms.values().stream()
            .filter(site -> site.worldId().equals(worldId))
            .map(FarmSite::footprint)
            .filter(existing -> existing != null)
            .anyMatch(footprint::overlaps);
    }

    public FarmSite findByBuildingInstance(UUID worldId, UUID buildingInstanceId) {
        if (worldId == null || buildingInstanceId == null) {
            return null;
        }
        return farms.values().stream()
            .filter(site -> site.worldId().equals(worldId))
            .filter(site -> buildingInstanceId.equals(site.buildingInstanceId()))
            .findFirst()
            .orElse(null);
    }

    public FarmSite findByEntranceHit(UUID worldId, Vector3i clickedBlock) {
        if (worldId == null || clickedBlock == null) {
            return null;
        }

        return farms.values().stream()
            .filter(site -> site.worldId().equals(worldId))
            .filter(site -> site.matchesEntranceColumn(clickedBlock))
            .findFirst()
            .orElse(null);
    }

    public AssignmentResult assignFarmer(Ref<EntityStore> ref, FarmSite site) {
        CivUnitRegistry.UnitKey key = unitRegistry.keyOf(ref);

        synchronized (site) {
            CivUnitRegistry.UnitKey current = site.assignedFarmer();
            if (current != null && !current.equals(key)) {
                return AssignmentResult.OCCUPIED;
            }

            FarmSite previous = farmerAssignments.get(key);
            if (previous == site && current != null) {
                return AssignmentResult.ALREADY_ASSIGNED;
            }

            if (previous != null) {
                unassignFarmer(ref);
            }

            if (!site.building().assignFarmer(workerId(site.worldId(), key))) {
                return site.building().workState() == FarmBuilding.WorkState.COMPLETE
                    ? AssignmentResult.COMPLETE
                    : AssignmentResult.OCCUPIED;
            }

            TransformComponent transform = ref.getStore().getComponentConcurrent(
                ref,
                TransformComponent.getComponentType()
            );
            site.selectEntranceFor(transform == null ? null : transform.getPosition());

            site.setAssignedFarmer(key);
            farmerAssignments.put(key, site);
            return AssignmentResult.ASSIGNED;
        }
    }

    public FarmSite getAssignment(Ref<EntityStore> ref) {
        return farmerAssignments.get(unitRegistry.keyOf(ref));
    }

    public void unassignFarmer(Ref<EntityStore> ref) {
        CivUnitRegistry.UnitKey key = unitRegistry.keyOf(ref);
        FarmSite site = farmerAssignments.remove(key);
        if (site == null) {
            return;
        }

        synchronized (site) {
            if (key.equals(site.assignedFarmer())) {
                site.setAssignedFarmer(null);
                site.resetActiveEntrance();
                site.building().unassignFarmer();
            }
        }
    }

    private static String workerId(UUID worldId, CivUnitRegistry.UnitKey key) {
        return worldId + ":" + key.entityIndex();
    }

    private static BlockPosition toCore(Vector3i position) {
        return new BlockPosition(position.x, position.y, position.z);
    }

    private static BlockPosition exteriorExitFor(BlockPosition entrance) {
        return new BlockPosition(entrance.x(), entrance.y(), entrance.z() - 2);
    }

    public enum AssignmentResult {
        ASSIGNED,
        ALREADY_ASSIGNED,
        OCCUPIED,
        COMPLETE
    }

    public static final class FarmSite {

        private final UUID worldId;
        private final UUID buildingInstanceId;
        private final FarmBuilding building;
        private final List<BlockPosition> entrances;
        private final PrefabPlacementService.PlacementFootprint footprint;
        private final Map<BlockPosition, Integer> replacedFloorBlocks;
        private CivUnitRegistry.UnitKey assignedFarmer;
        private BlockPosition activeEntrance;

        private FarmSite(
            UUID worldId,
            UUID buildingInstanceId,
            FarmBuilding building,
            List<BlockPosition> entrances,
            PrefabPlacementService.PlacementFootprint footprint,
            Map<BlockPosition, Integer> replacedFloorBlocks
        ) {
            this.worldId = worldId;
            this.buildingInstanceId = buildingInstanceId;
            this.building = building;
            this.entrances = List.copyOf(entrances);
            this.footprint = footprint;
            this.replacedFloorBlocks = Map.copyOf(replacedFloorBlocks);
            this.activeEntrance = this.entrances.getFirst();
        }

        public UUID worldId() {
            return worldId;
        }

        public UUID buildingInstanceId() {
            return buildingInstanceId;
        }

        public FarmBuilding building() {
            return building;
        }

        public int entranceCount() {
            return entrances.size();
        }

        public PrefabPlacementService.PlacementFootprint footprint() {
            return footprint;
        }

        public Map<BlockPosition, Integer> replacedFloorBlocks() {
            return replacedFloorBlocks;
        }

        public synchronized CivUnitRegistry.UnitKey assignedFarmer() {
            return assignedFarmer;
        }

        private synchronized void setAssignedFarmer(CivUnitRegistry.UnitKey assignedFarmer) {
            this.assignedFarmer = assignedFarmer;
        }

        private synchronized void selectEntranceFor(Vector3d position) {
            if (position == null || entrances.size() == 1) {
                activeEntrance = entrances.getFirst();
                return;
            }

            activeEntrance = entrances.stream()
                .min((left, right) -> Double.compare(
                    squaredDistance(position, left),
                    squaredDistance(position, right)
                ))
                .orElse(entrances.getFirst());
        }

        private synchronized void resetActiveEntrance() {
            activeEntrance = entrances.getFirst();
        }

        public synchronized Vector3d entranceTarget() {
            return targetAbove(activeEntrance);
        }

        public synchronized Vector3d exitTarget() {
            return targetAbove(exteriorExitFor(activeEntrance));
        }

        private boolean matchesEntranceColumn(Vector3i clickedBlock) {
            return entrances.stream().anyMatch(entrance ->
                clickedBlock.x == entrance.x()
                    && clickedBlock.z == entrance.z()
                    && clickedBlock.y >= entrance.y()
                    && clickedBlock.y <= entrance.y() + 2
            );
        }

        private static double squaredDistance(Vector3d position, BlockPosition entrance) {
            double dx = position.x - (entrance.x() + 0.5);
            double dy = position.y - (entrance.y() + 1.0);
            double dz = position.z - (entrance.z() + 0.5);
            return dx * dx + dy * dy + dz * dz;
        }

        private static Vector3d targetAbove(BlockPosition block) {
            return new Vector3d(block.x() + 0.5, block.y() + 1.0, block.z() + 0.5);
        }
    }

    private record FarmKey(UUID worldId, String buildingId) {
    }
}
