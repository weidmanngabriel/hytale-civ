package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.Profession;

/** Shared validation and state changes for assigning a Civ miner to a completed mine. */
public final class CivMinerAssignmentService {
    private final CivUnitRegistry units;
    private final CivActivityRegistry activities;
    private final FarmBuildingRegistry farms;
    private final BuildingPlacementRegistry buildings;

    public CivMinerAssignmentService(
        CivUnitRegistry units,
        CivActivityRegistry activities,
        FarmBuildingRegistry farms,
        BuildingPlacementRegistry buildings
    ) {
        this.units = units;
        this.activities = activities;
        this.farms = farms;
        this.buildings = buildings;
    }

    public Result assign(Ref<EntityStore> miner, BuildingPlacementRegistry.BuildingInstance mine) {
        if (miner == null || !miner.isValid() || !units.isClaimed(miner)) return Result.NOT_CIV_INHABITANT;
        if (mine == null || !"mine".equals(mine.buildingType())) return Result.NOT_A_MINE;
        if (buildings.isUpgrading(mine.worldId(), mine.id())) return Result.UPGRADING;
        boolean hasConnector = mine.semanticVolumes().stream()
            .anyMatch(volume -> volume.hasTag("civ.type", "mine_tunnel_connector")
                && volume.hasTag("civ.building", "mine"));
        if (!hasConnector) return Result.MISSING_CONNECTOR;

        farms.unassignFarmer(miner);
        activities.cancelManualMove(miner);
        units.cancelMoveTarget(miner);
        units.assignProfession(miner, Profession.MINER);
        units.assignWorkplace(miner, mine.id());
        return Result.ASSIGNED;
    }

    public enum Result {
        ASSIGNED,
        NOT_CIV_INHABITANT,
        NOT_A_MINE,
        UPGRADING,
        MISSING_CONNECTOR
    }
}
