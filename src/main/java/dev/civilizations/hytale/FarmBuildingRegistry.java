package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.FarmBuilding;
import org.joml.Vector3d;
import org.joml.Vector3i;

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

    public FarmSite registerFarm(UUID worldId, Vector3i entranceBlock) {
        BlockPosition entrance = toCore(entranceBlock);
        BlockPosition exit = new BlockPosition(entrance.x(), entrance.y(), entrance.z() - 2);
        FarmBuilding building = new FarmBuilding(
            "farm-" + nextFarmId.getAndIncrement(),
            entrance,
            exit
        );
        FarmSite site = new FarmSite(worldId, building);
        farms.put(new FarmKey(worldId, entrance), site);
        return site;
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

    public enum AssignmentResult {
        ASSIGNED,
        ALREADY_ASSIGNED,
        OCCUPIED,
        COMPLETE
    }

    public static final class FarmSite {

        private final UUID worldId;
        private final FarmBuilding building;
        private CivUnitRegistry.UnitKey assignedFarmer;

        private FarmSite(UUID worldId, FarmBuilding building) {
            this.worldId = worldId;
            this.building = building;
        }

        public UUID worldId() {
            return worldId;
        }

        public FarmBuilding building() {
            return building;
        }

        public synchronized CivUnitRegistry.UnitKey assignedFarmer() {
            return assignedFarmer;
        }

        private synchronized void setAssignedFarmer(CivUnitRegistry.UnitKey assignedFarmer) {
            this.assignedFarmer = assignedFarmer;
        }

        public Vector3d entranceTarget() {
            BlockPosition block = building.entranceBlock();
            return new Vector3d(block.x() + 0.5, block.y() + 1.0, block.z() + 0.5);
        }

        public Vector3d exitTarget() {
            BlockPosition block = building.exitBlock();
            return new Vector3d(block.x() + 0.5, block.y() + 1.0, block.z() + 0.5);
        }

        private boolean matchesEntranceColumn(Vector3i clickedBlock) {
            BlockPosition entrance = building.entranceBlock();
            return clickedBlock.x == entrance.x()
                && clickedBlock.z == entrance.z()
                && clickedBlock.y >= entrance.y()
                && clickedBlock.y <= entrance.y() + 2;
        }
    }

    private record FarmKey(UUID worldId, BlockPosition entranceBlock) {
    }
}
