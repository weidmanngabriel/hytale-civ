package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Presentation-only controller for the compact HUD of the currently inspected building/site. */
public final class CivSelectedBuildingHudController {

    private final BuildingPlacementRegistry buildingRegistry;
    private final ConstructionSiteRegistry constructionRegistry;
    private final PrefabPlacementService placementService;
    private final CivUnitRegistry unitRegistry;
    private final Map<UUID, Selection> selectedByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> siteLayerCounts = new ConcurrentHashMap<>();

    public CivSelectedBuildingHudController(
        BuildingPlacementRegistry buildingRegistry,
        ConstructionSiteRegistry constructionRegistry,
        PrefabPlacementService placementService,
        CivUnitRegistry unitRegistry
    ) {
        this.buildingRegistry = buildingRegistry;
        this.constructionRegistry = constructionRegistry;
        this.placementService = placementService;
        this.unitRegistry = unitRegistry;
    }

    public void selectBuilding(PlayerRef playerRef, UUID worldId, UUID buildingId) {
        if (playerRef == null || worldId == null || buildingId == null) return;
        selectedByPlayer.put(playerRef.getUuid(), new Selection(worldId, buildingId, false));
        refresh(playerRef);
    }

    public void selectSite(PlayerRef playerRef, UUID worldId, UUID siteId) {
        if (playerRef == null || worldId == null || siteId == null) return;
        selectedByPlayer.put(playerRef.getUuid(), new Selection(worldId, siteId, true));
        refresh(playerRef);
    }

    public void refresh(Player player) {
        if (player != null) refresh(player.getPlayerRef());
    }

    public void refresh(PlayerRef playerRef) {
        if (playerRef == null) return;
        Selection selection = selectedByPlayer.get(playerRef.getUuid());
        if (selection == null) return;
        BuildingInfoSnapshot snapshot = selection.site()
            ? siteSnapshot(selection.worldId(), selection.id())
            : buildingSnapshot(selection.worldId(), selection.id());
        if (snapshot == null) {
            clear(playerRef);
            return;
        }
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) return;
        Player player = playerEntityRef.getStore().getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) return;
        var hudManager = player.getHudManager();
        var existing = hudManager.getCustomHud(CivBuildingCompactHud.HUD_KEY);
        if (existing instanceof CivBuildingCompactHud compactHud) {
            compactHud.refresh(snapshot);
        } else {
            hudManager.addCustomHud(playerRef, new CivBuildingCompactHud(playerRef, snapshot));
        }
    }

    public void clear(PlayerRef playerRef) {
        if (playerRef == null) return;
        selectedByPlayer.remove(playerRef.getUuid());
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) return;
        Player player = playerEntityRef.getStore().getComponent(playerEntityRef, Player.getComponentType());
        if (player != null) {
            player.getHudManager().removeCustomHud(playerRef, CivBuildingCompactHud.HUD_KEY);
        }
    }

    public void handleDisconnect(PlayerDisconnectEvent event) {
        if (event != null && event.getPlayerRef() != null) {
            selectedByPlayer.remove(event.getPlayerRef().getUuid());
        }
    }

    private BuildingInfoSnapshot buildingSnapshot(UUID worldId, UUID buildingId) {
        BuildingPlacementRegistry.BuildingInstance building =
            buildingRegistry.findIncludingUpgrading(worldId, buildingId);
        if (building == null) return null;
        String name = building.placement() == null
            ? "Gebäude" : building.placement().definition().displayName();
        boolean upgrading = buildingRegistry.isUpgrading(worldId, buildingId);
        int workers = unitRegistry.workersAt(building.id()).size();
        return new BuildingInfoSnapshot(
            name,
            "Phase " + building.phase(),
            upgrading ? "Ausbau läuft" : "Fertig",
            upgrading ? "Baustelle ausgewählt" : "—",
            workers + "/" + building.workerCapacity()
        );
    }

    private BuildingInfoSnapshot siteSnapshot(UUID worldId, UUID siteId) {
        ConstructionSiteRegistry.SiteState state = constructionRegistry.get(siteId);
        if (state == null || !worldId.equals(state.site().worldId())) return null;
        PrefabPlacementService.ConstructionSite site = state.site();
        int total = siteLayerCounts.computeIfAbsent(
            siteId,
            ignored -> placementService.constructionLayerCount(site)
        );
        int phase = site.isUpgrade()
            ? site.targetPhase()
            : PrefabPlacementService.phaseForDefinition(site.definition());
        return new BuildingInfoSnapshot(
            site.definition().displayName(),
            "Phase " + phase,
            "Im Bau",
            state.completedLayers() + "/" + total + " Bauabschnitte",
            "Bauarbeiter automatisch"
        );
    }

    private record Selection(UUID worldId, UUID id, boolean site) {
    }
}
