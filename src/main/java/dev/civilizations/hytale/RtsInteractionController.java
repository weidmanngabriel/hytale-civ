package dev.civilizations.hytale;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.DelegateItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.civilizations.core.BuildingBounds;
import dev.civilizations.core.BuildingTypes;
import dev.civilizations.core.Profession;
import dev.civilizations.core.WorldPosition;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Main RTS input adapter. Clicks select Civ targets; a second left click opens their detail UI. */
public final class RtsInteractionController {

    private static final String TYPE_TAG = "civ.type";
    private static final String BUILDING_TAG = "civ.building";

    private final RtsCameraController cameraController;
    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final FarmBuildingRegistry farmRegistry;
    private final FarmFieldRegistry fieldRegistry;
    private final BuildingPlacementRegistry placementRegistry;
    private final PrefabPlacementService placementService;
    private final CivBuildingPersistenceService buildingPersistence;
    private final MineTunnelRegistry mineTunnelRegistry;
    private final ConstructionSiteRegistry constructionRegistry;
    private final CivConstructionPersistenceService constructionPersistence;
    private final CivSelectedNpcHudController npcHudController;
    private final CivSelectedBuildingHudController buildingHudController;
    private final CivBoundaryDisplayService boundaryDisplay;
    private final CivPlacementFootprintProbe footprintProbe = new CivPlacementFootprintProbe();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> claimArmed = ConcurrentHashMap.newKeySet();

    public RtsInteractionController(
        RtsCameraController cameraController,
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry,
        FarmBuildingRegistry farmRegistry,
        FarmFieldRegistry fieldRegistry,
        BuildingPlacementRegistry placementRegistry,
        PrefabPlacementService placementService,
        CivBuildingPersistenceService buildingPersistence,
        MineTunnelRegistry mineTunnelRegistry,
        ConstructionSiteRegistry constructionRegistry,
        CivConstructionPersistenceService constructionPersistence,
        CivSelectedNpcHudController npcHudController,
        CivSelectedBuildingHudController buildingHudController,
        CivBoundaryDisplayService boundaryDisplay
    ) {
        this.cameraController = cameraController;
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
        this.farmRegistry = farmRegistry;
        this.fieldRegistry = fieldRegistry;
        this.placementRegistry = placementRegistry;
        this.placementService = placementService;
        this.buildingPersistence = buildingPersistence;
        this.mineTunnelRegistry = mineTunnelRegistry;
        this.constructionRegistry = constructionRegistry;
        this.constructionPersistence = constructionPersistence;
        this.npcHudController = npcHudController;
        this.buildingHudController = buildingHudController;
        this.boundaryDisplay = boundaryDisplay;
    }

    public boolean toggle(PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        Session active = sessions.get(playerId);
        if (active != null) {
            clearPlacement(playerRef, active);
            clearSelection(playerRef, active);
            cameraController.disable(playerRef);
            sessions.remove(playerId, active);
            playerRef.sendMessage(Message.raw("Civ RTS test disabled."));
            return false;
        }
        sessions.put(playerId, new Session());
        cameraController.enable(playerRef);
        npcHudController.setRtsActive(playerRef, true);
        playerRef.sendMessage(Message.raw(
            "Civ RTS test enabled. /civbuild öffnet das Baumenü; /civwiki öffnet die Civ-Hilfe."
        ));
        return true;
    }

    public void armClaim(PlayerRef playerRef) {
        claimArmed.add(playerRef.getUuid());
        playerRef.sendMessage(Message.raw("Civ claim armed. Left click an NPC in First Person or RTS mode."));
    }

    public void armFarmPlacement(PlayerRef playerRef) {
        Session session = sessions.get(playerRef.getUuid());
        if (session == null) {
            playerRef.sendMessage(Message.raw("Enable /civrtstest before placing a farm."));
            return;
        }
        startPlacement(playerRef, session, PrefabPlacementService.FARM);
    }

    public void handleMouseButton(PlayerMouseButtonEvent event) {
        PlayerRef playerRef = event.getPlayerRefComponent();
        if (event.getMouseButton().state != MouseButtonState.Pressed) return;
        MouseButtonType button = event.getMouseButton().mouseButtonType;
        Session session = sessions.get(playerRef.getUuid());

        if (button == MouseButtonType.Left
            && claimArmed.contains(playerRef.getUuid())
            && (session == null || session.placementDefinition == null)) {
            claimArmed.remove(playerRef.getUuid());
            handleClaim(event.getTargetEntityRef(), playerRef);
            event.setCancelled(true);
            return;
        }
        if (session == null) return;

        if (session.placementDefinition != null) {
            if (button == MouseButtonType.Left) {
                confirmPlacement(playerRef, session, event.getTargetBlock());
            } else if (button == MouseButtonType.Right) {
                String name = session.placementDefinition.displayName();
                clearPlacement(playerRef, session);
                playerRef.sendMessage(Message.raw(name + "-Platzierung abgebrochen."));
            }
            event.setCancelled(true);
            return;
        }

        Ref<EntityStore> targetEntity = event.getTargetEntityRef();
        if (targetEntity != null && targetEntity.isValid() && unitRegistry.isClaimed(targetEntity)) {
            if (sameNpc(session, targetEntity)) {
                if (button == MouseButtonType.Left) openPersonActions(event, playerRef, targetEntity);
            } else {
                selectNpc(playerRef, session, targetEntity);
            }
            event.setCancelled(true);
            return;
        }

        Vector3i targetBlock = event.getTargetBlock();
        UUID worldId = playerRef.getWorldUuid();
        if (targetBlock != null && worldId != null) {
            ConstructionSiteRegistry.SiteState siteState = constructionRegistry.findAt(worldId, targetBlock);
            if (siteState != null) {
                UUID siteId = siteState.site().id();
                if (siteId.equals(session.selectedSiteId)) {
                    if (button == MouseButtonType.Left) openConstructionDetails(event, playerRef, siteState);
                } else {
                    selectSite(playerRef, session, siteState);
                }
                event.setCancelled(true);
                return;
            }

            BuildingPlacementRegistry.BuildingInstance building = placementRegistry.findAt(worldId, targetBlock);
            if (building != null) {
                if (building.id().equals(session.selectedBuildingId)) {
                    if (button == MouseButtonType.Left) {
                        openBuildingActions(event, playerRef, session, building);
                    } else if (button == MouseButtonType.Right) {
                        handleBuildingContextRightClick(playerRef, session, building);
                    }
                } else {
                    selectBuilding(playerRef, session, building);
                }
                event.setCancelled(true);
                return;
            }
        }

        if (button == MouseButtonType.Right && session.commandNpc != null && targetBlock != null) {
            removeInvalidCommandNpc(session);
            if (session.commandNpc != null) {
                boolean accepted = activityRegistry.orderManualMove(
                    session.commandNpc,
                    new WorldPosition(targetBlock.x + 0.5, targetBlock.y + 1.0, targetBlock.z + 0.5)
                );
                playerRef.sendMessage(Message.raw(
                    "Bewegungsbefehl " + targetBlock.x + ", " + targetBlock.y + ", " + targetBlock.z
                        + " an " + (accepted ? 1 : 0) + " Civ-Bewohner."
                ));
                event.setCancelled(true);
                return;
            }
        }

        if (button == MouseButtonType.Left) {
            clearSelection(playerRef, session);
            event.setCancelled(true);
        }
    }

    public void handleMouseMotion(PlayerMouseMotionEvent event) {
        PlayerRef playerRef = event.getPlayer() == null ? null : event.getPlayer().getPlayerRef();
        if (playerRef == null) return;
        Session session = sessions.get(playerRef.getUuid());
        if (session == null || session.placementDefinition == null) return;
        Vector3i target = event.getTargetBlock();
        if (target == null) {
            if (session.collisionBoundaryVisible) boundaryDisplay.clear(playerRef);
            session.collisionBoundaryVisible = false;
            session.previewTarget = null;
            return;
        }
        if (session.previewTarget != null && session.previewTarget.equals(target)) return;
        session.previewTarget = new Vector3i(target);
        updatePlacementBoundaries(playerRef, session, target);
    }

    private void updatePlacementBoundaries(PlayerRef playerRef, Session session, Vector3i target) {
        UUID worldId = playerRef.getWorldUuid();
        if (worldId == null || session.placementDefinition == null) return;
        PrefabPlacementService.PlacementFootprint footprint;
        try {
            footprint = footprintProbe.footprint(target, session.placementDefinition);
        } catch (RuntimeException exception) {
            boundaryDisplay.clear(playerRef);
            session.collisionBoundaryVisible = false;
            return;
        }
        List<BuildingBounds> buildings = placementRegistry.buildings(worldId).stream()
            .map(BuildingPlacementRegistry.BuildingInstance::bounds)
            .filter(bounds -> bounds.overlapsHorizontal(
                footprint.minX(), footprint.minZ(), footprint.maxX() + 1.0, footprint.maxZ() + 1.0
            ))
            .toList();
        List<PrefabPlacementService.PlacementFootprint> sites = constructionRegistry
            .overlapping(worldId, footprint, null).stream()
            .map(state -> state.site().candidate().footprint())
            .toList();
        if (buildings.isEmpty() && sites.isEmpty()) {
            if (session.collisionBoundaryVisible) boundaryDisplay.clear(playerRef);
            session.collisionBoundaryVisible = false;
            return;
        }
        boundaryDisplay.showPlacementCollision(playerRef, footprint, buildings, sites);
        session.collisionBoundaryVisible = true;
    }

    public void handleDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        Session session = sessions.remove(playerRef.getUuid());
        claimArmed.remove(playerRef.getUuid());
        if (session != null) {
            clearPlacement(playerRef, session);
            clearSelection(playerRef, session);
        }
        // Confirmed ConstructionSites are world state. Disconnect deliberately does not release them.
    }

    public void cancelConstructionSites(PlayerRef playerRef) {
        Session session = sessions.get(playerRef.getUuid());
        if (session != null && session.placementDefinition != null) {
            clearPlacement(playerRef, session);
            playerRef.sendMessage(Message.raw("Aktuelle Civ-Bauplatzierung abgebrochen."));
        } else {
            playerRef.sendMessage(Message.raw("Keine aktive Civ-Bauplatzierung."));
        }
    }

    public void openBuildingMenu(
        PlayerRef playerRef,
        Ref<EntityStore> playerEntityRef,
        Store<EntityStore> store
    ) {
        Session session = sessions.get(playerRef.getUuid());
        if (session == null || playerEntityRef == null || !playerEntityRef.isValid()) return;
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) return;
        clearPlacement(playerRef, session);
        clearSelection(playerRef, session);
        player.getPageManager().openCustomPage(
            playerEntityRef,
            store,
            new BuildingMenuPage(
                playerRef,
                () -> startPlacement(playerRef, session, PrefabPlacementService.FARM),
                () -> startPlacement(playerRef, session, PrefabPlacementService.MINE),
                () -> startPlacement(playerRef, session, PrefabPlacementService.MINE_02),
                () -> startPlacement(playerRef, session, PrefabPlacementService.MINE_03),
                () -> startPlacement(playerRef, session, PrefabPlacementService.WHEAT_FIELD)
            )
        );
    }

    public void openWiki(
        PlayerRef playerRef,
        Ref<EntityStore> playerEntityRef,
        Store<EntityStore> store
    ) {
        Session session = sessions.get(playerRef.getUuid());
        if (session == null || playerEntityRef == null || !playerEntityRef.isValid()) return;
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) return;
        clearPlacement(playerRef, session);
        player.getPageManager().openCustomPage(playerEntityRef, store, new WikiPage(playerRef));
    }

    private void startPlacement(
        PlayerRef playerRef,
        Session session,
        PrefabPlacementService.PlacementDefinition definition
    ) {
        clearPlacement(playerRef, session);
        clearSelection(playerRef, session);
        try {
            if (!placementService.startNativeConstructionGhost(playerRef, definition)) {
                playerRef.sendMessage(Message.raw(
                    definition.displayName() + " konnte nicht an Hytales nativen Ghost übergeben werden."
                ));
                return;
            }
            session.placementDefinition = definition;
            playerRef.sendMessage(Message.raw(
                definition.displayName()
                    + " ausgewählt. Nativer Ghost aktiv; Linksklick wird von Civ als Baustelle übernommen."
            ));
        } catch (RuntimeException exception) {
            playerRef.sendMessage(Message.raw(
                definition.displayName() + " konnte nicht geladen werden: " + exception.getMessage()
            ));
        }
    }

    private void confirmPlacement(PlayerRef playerRef, Session session, Vector3i targetBlock) {
        if (targetBlock == null) {
            playerRef.sendMessage(Message.raw("Keine Bauposition unter dem Cursor."));
            return;
        }
        UUID worldId = playerRef.getWorldUuid();
        World world = worldId == null ? null : Universe.get().getWorld(worldId);
        if (world == null) {
            playerRef.sendMessage(Message.raw("Could not resolve your current world."));
            return;
        }
        PrefabPlacementService.PlacementDefinition definition = session.placementDefinition;
        try {
            PrefabPlacementService.PlacementCandidate candidate = validatePlacement(
                worldId, world, targetBlock, definition
            );
            if (!candidate.valid()) {
                playerRef.sendMessage(Message.raw(
                    definition.displayName() + " kann hier nicht gebaut werden: " + candidate.invalidReason()
                ));
                updatePlacementBoundaries(playerRef, session, targetBlock);
                return;
            }
            PrefabPlacementService.ConstructionSite site =
                placementService.createConstructionSiteAtClick(playerRef, candidate);
            placementRegistry.reserve(worldId, site.id(), candidate.footprint());
            constructionRegistry.register(site);
            constructionPersistence.saveSites(world, constructionRegistry.states());
            session.placementDefinition = null;
            session.previewTarget = null;
            session.previewCandidate = null;
            session.collisionBoundaryVisible = false;
            boundaryDisplay.clear(playerRef);
            playerRef.sendMessage(Message.raw(definition.displayName() + " als Baustelle gesetzt."));
        } catch (RuntimeException exception) {
            playerRef.sendMessage(Message.raw(
                definition.displayName() + " placement failed: " + exception.getMessage()
            ));
        }
    }

    private PrefabPlacementService.PlacementCandidate validatePlacement(
        UUID worldId,
        World world,
        Vector3i targetBlock,
        PrefabPlacementService.PlacementDefinition definition
    ) {
        PrefabPlacementService.PlacementCandidate candidate =
            placementService.validatePlacement(world, targetBlock, definition);
        if (candidate.valid() && placementRegistry.overlaps(worldId, candidate.footprint())) {
            return candidate.invalidate("Die Fläche überschneidet sich mit einem Civ-Bauwerk.");
        }
        return candidate;
    }

    public void handleClaim(Ref<EntityStore> target, PlayerRef playerRef) {
        handleClaim(target, playerRef, null);
    }

    public void handleClaim(
        Ref<EntityStore> target,
        PlayerRef playerRef,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        if (target == null || !target.isValid()) {
            playerRef.sendMessage(Message.raw("No NPC under cursor."));
            return;
        }
        NPCEntity npc = commandBuffer == null
            ? target.getStore().getComponentConcurrent(target, NPCEntity.getComponentType())
            : commandBuffer.getComponent(target, NPCEntity.getComponentType());
        if (npc == null) {
            playerRef.sendMessage(Message.raw("Target is not an NPCEntity and cannot be claimed."));
            return;
        }
        CivUnitRegistry.UnitKey key = unitRegistry.keyOf(target);
        CivUnitRegistry.ClaimResult bufferedResult = commandBuffer == null
            ? null : unitRegistry.toggleClaimBuffered(target, commandBuffer);
        boolean claimed = bufferedResult == null
            ? unitRegistry.toggleClaim(target) : bufferedResult.claimed();
        if (!claimed) {
            activityRegistry.forget(target);
            farmRegistry.unassignFarmer(target);
            sessions.values().forEach(session -> {
                if (session.selectedNpc != null && unitRegistry.keyOf(session.selectedNpc).equals(key)) {
                    session.selectedNpc = null;
                }
                if (session.commandNpc != null && unitRegistry.keyOf(session.commandNpc).equals(key)) {
                    session.commandNpc = null;
                }
            });
        }
        if (claimed) {
            CivInhabitantData data = bufferedResult == null
                ? unitRegistry.getInhabitantData(target) : bufferedResult.inhabitantData();
            String name = data == null || !data.hasIdentity() ? "unknown" : data.fullName();
            playerRef.sendMessage(Message.raw("Civ inhabitant claimed: " + name));
        } else {
            playerRef.sendMessage(Message.raw("NPC released from Civ control."));
        }
    }

    private void selectNpc(PlayerRef playerRef, Session session, Ref<EntityStore> target) {
        session.selectedNpc = target;
        session.commandNpc = target;
        session.selectedBuildingId = null;
        session.selectedSiteId = null;
        buildingHudController.clear(playerRef);
        boundaryDisplay.clear(playerRef);
        npcHudController.select(playerRef, target);
    }

    private void selectBuilding(
        PlayerRef playerRef,
        Session session,
        BuildingPlacementRegistry.BuildingInstance building
    ) {
        session.selectedNpc = null;
        session.selectedSiteId = null;
        session.selectedBuildingId = building.id();
        npcHudController.clear(playerRef);
        buildingHudController.selectBuilding(playerRef, building.worldId(), building.id());
        boundaryDisplay.showBuilding(playerRef, building.bounds(), buildingDisplayName(building));
    }

    private void selectSite(
        PlayerRef playerRef,
        Session session,
        ConstructionSiteRegistry.SiteState state
    ) {
        PrefabPlacementService.ConstructionSite site = state.site();
        session.selectedNpc = null;
        session.selectedBuildingId = null;
        session.selectedSiteId = site.id();
        npcHudController.clear(playerRef);
        buildingHudController.selectSite(playerRef, site.worldId(), site.id());
        boundaryDisplay.showSite(playerRef, site.candidate().footprint(), site.definition().displayName());
    }

    private void clearSelection(PlayerRef playerRef, Session session) {
        session.selectedNpc = null;
        session.commandNpc = null;
        session.selectedBuildingId = null;
        session.selectedSiteId = null;
        npcHudController.clear(playerRef);
        buildingHudController.clear(playerRef);
        boundaryDisplay.clear(playerRef);
    }

    private boolean sameNpc(Session session, Ref<EntityStore> target) {
        return session.selectedNpc != null && session.selectedNpc.isValid()
            && unitRegistry.keyOf(session.selectedNpc).equals(unitRegistry.keyOf(target));
    }

    private void openBuildingActions(
        PlayerMouseButtonEvent event,
        PlayerRef playerRef,
        Session session,
        BuildingPlacementRegistry.BuildingInstance building
    ) {
        Ref<EntityStore> playerEntityRef = event.getPlayerRef();
        Store<EntityStore> store = playerEntityRef.getStore();
        List<BuildingActionsPage.WorkerOption> workers = unitRegistry.workersAt(building.id()).stream()
            .map(worker -> {
                CivInhabitantData data = unitRegistry.getInhabitantData(worker);
                String name = data == null || !data.hasIdentity() ? "Bewohner" : data.fullName();
                String profession = data == null ? "" : professionDisplayName(data.profession());
                String label = profession.isBlank() ? name : name + " — " + profession;
                return new BuildingActionsPage.WorkerOption(
                    label, () -> selectWorkerFromBuilding(playerRef, session, worker)
                );
            })
            .toList();
        int nextPhase = BuildingTypes.nextPhase(building.buildingType(), building.phase());
        boolean upgrading = placementRegistry.isUpgrading(building.worldId(), building.id());
        event.getPlayer().getPageManager().openCustomPage(
            playerEntityRef,
            store,
            new BuildingActionsPage(
                playerRef,
                buildingDisplayName(building),
                building.phase(),
                building.workerCapacity(),
                workers,
                nextPhase,
                upgrading,
                nextPhase > 0 ? () -> upgradeBuilding(playerRef, building.id(), nextPhase) : null,
                () -> demolishBuilding(playerRef, building.id())
            )
        );
    }

    private void openConstructionDetails(
        PlayerMouseButtonEvent event,
        PlayerRef playerRef,
        ConstructionSiteRegistry.SiteState state
    ) {
        PrefabPlacementService.ConstructionSite site = state.site();
        int total = placementService.constructionLayerCount(site);
        int phase = site.isUpgrade()
            ? site.targetPhase()
            : PrefabPlacementService.phaseForDefinition(site.definition());
        BuildingInfoSnapshot snapshot = new BuildingInfoSnapshot(
            site.definition().displayName(),
            "Phase " + phase,
            "Im Bau",
            state.completedLayers() + "/" + total + " Bauabschnitte",
            "Bauarbeiter automatisch"
        );
        Ref<EntityStore> playerEntityRef = event.getPlayerRef();
        event.getPlayer().getPageManager().openCustomPage(
            playerEntityRef,
            playerEntityRef.getStore(),
            new ConstructionDetailsPage(playerRef, snapshot)
        );
    }

    private void handleBuildingContextRightClick(
        PlayerRef playerRef,
        Session session,
        BuildingPlacementRegistry.BuildingInstance building
    ) {
        removeInvalidCommandNpc(session);
        if (session.commandNpc == null) return;
        FarmBuildingRegistry.FarmSite farm =
            farmRegistry.findByBuildingInstance(building.worldId(), building.id());
        if (farm != null) {
            assignSelectedFarmer(playerRef, session, farm);
        } else if ("mine".equals(building.buildingType())
            && unitRegistry.getProfession(session.commandNpc) == Profession.MINER) {
            assignSelectedMiner(playerRef, session, building);
        }
    }

    private void selectWorkerFromBuilding(
        PlayerRef playerRef,
        Session session,
        Ref<EntityStore> worker
    ) {
        if (worker == null || !worker.isValid() || !unitRegistry.isClaimed(worker)) {
            playerRef.sendMessage(Message.raw("Der Arbeiter ist nicht mehr verfügbar."));
            return;
        }
        selectNpc(playerRef, session, worker);
        CivInhabitantData data = unitRegistry.getInhabitantData(worker);
        String name = data == null || !data.hasIdentity() ? "Civ-Bewohner" : data.fullName();
        playerRef.sendMessage(Message.raw(name + " ausgewählt."));
    }

    private void upgradeBuilding(PlayerRef playerRef, UUID buildingId, int targetPhase) {
        UUID worldId = playerRef.getWorldUuid();
        World world = worldId == null ? null : Universe.get().getWorld(worldId);
        BuildingPlacementRegistry.BuildingInstance building = placementRegistry.find(worldId, buildingId);
        if (world == null || building == null) {
            playerRef.sendMessage(Message.raw("Das Gebäude ist nicht mehr verfügbar."));
            return;
        }
        if (!"mine".equals(building.buildingType())) {
            playerRef.sendMessage(Message.raw("Für dieses Gebäude ist noch kein Ausbau verfügbar."));
            return;
        }
        int expectedPhase = BuildingTypes.nextPhase(building.buildingType(), building.phase());
        if (expectedPhase == 0 || expectedPhase != targetPhase) {
            playerRef.sendMessage(Message.raw("Diese Mine kann nicht auf die gewählte Phase erweitert werden."));
            return;
        }
        if (!placementRegistry.beginUpgrade(worldId, buildingId)) {
            playerRef.sendMessage(Message.raw("Diese Mine wird bereits erweitert."));
            return;
        }

        PrefabPlacementService.ConstructionSite site = null;
        try {
            site = placementService.createUpgradeConstructionSite(playerRef, world, building, targetPhase);
            placementRegistry.reserve(worldId, site.id(), site.candidate().footprint());
            constructionRegistry.register(site);
            constructionPersistence.saveSites(world, constructionRegistry.states());
            int evacuated = evacuateMineWorkers(world, building, site.candidate().footprint());
            playerRef.sendMessage(Message.raw(
                "Mine wird auf Phase " + targetPhase + " erweitert. " + evacuated
                    + " Arbeiter wurden nach draußen gebracht; die Mine bleibt bis zur Fertigstellung gesperrt."
            ));
        } catch (RuntimeException exception) {
            if (site != null) {
                constructionRegistry.remove(site.id());
                placementRegistry.release(worldId, site.id());
                placementService.removeConstructionSite(site);
                constructionPersistence.saveSites(world, constructionRegistry.states());
            }
            placementRegistry.cancelUpgrade(worldId, buildingId);
            playerRef.sendMessage(Message.raw("Ausbau konnte nicht gestartet werden: " + exception.getMessage()));
        }
    }

    private int evacuateMineWorkers(
        World world,
        BuildingPlacementRegistry.BuildingInstance building,
        PrefabPlacementService.PlacementFootprint targetFootprint
    ) {
        List<Ref<EntityStore>> workers = unitRegistry.workersAt(building.id());
        EvacuationPlan plan = safeEvacuationPlan(world, building, targetFootprint);
        double sideX = -plan.outwardZ();
        double sideZ = plan.outwardX();
        int evacuated = 0;
        for (int index = 0; index < workers.size(); index++) {
            Ref<EntityStore> worker = workers.get(index);
            if (worker == null || !worker.isValid()) continue;
            Store<EntityStore> store = worker.getStore();
            TransformComponent transform = store.getComponent(worker, TransformComponent.getComponentType());
            if (transform == null) continue;
            activityRegistry.cancelManualMove(worker);
            unitRegistry.cancelMoveTarget(worker);
            double sideOffset = (index - (workers.size() - 1) * 0.5) * 1.25;
            Vector3d target = new Vector3d(
                plan.point().x + sideX * sideOffset,
                plan.point().y,
                plan.point().z + sideZ * sideOffset
            );
            for (int push = 0; push < 12 && !outsideFootprint(targetFootprint, target.x, target.z); push++) {
                target.x += plan.outwardX() * 0.75;
                target.z += plan.outwardZ() * 0.75;
            }
            store.putComponent(
                worker,
                Teleport.getComponentType(),
                new Teleport(target, transform.getRotation())
            );
            evacuated++;
        }
        return evacuated;
    }

    private static EvacuationPlan safeEvacuationPlan(
        World world,
        BuildingPlacementRegistry.BuildingInstance building,
        PrefabPlacementService.PlacementFootprint targetFootprint
    ) {
        PrefabPlacementService.PlacedMarker entrance = building.semanticVolumes().stream()
            .filter(volume -> volume.hasTag(TYPE_TAG, "workplace_access"))
            .filter(volume -> volume.hasTag(BUILDING_TAG, "mine"))
            .findFirst().orElse(null);
        entrance = hydrateMarkerBounds(world, entrance);
        if (entrance != null && entrance.bounds() != null) {
            BuildingBounds bounds = entrance.bounds();
            double x = (bounds.minX() + bounds.maxX()) * 0.5;
            double z = (bounds.minZ() + bounds.maxZ()) * 0.5;
            double buildingX = (building.bounds().minX() + building.bounds().maxX()) * 0.5;
            double buildingZ = (building.bounds().minZ() + building.bounds().maxZ()) * 0.5;
            double dx = x - buildingX;
            double dz = z - buildingZ;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 0.01) {
                dx = 0.0;
                dz = -1.0;
                length = 1.0;
            }
            double outwardX = dx / length;
            double outwardZ = dz / length;
            for (int step = 0; step < 64; step++) {
                x += outwardX;
                z += outwardZ;
                if (outsideFootprint(targetFootprint, x, z)) {
                    return new EvacuationPlan(
                        new Vector3d(
                            x + outwardX * 1.5,
                            bounds.minY(),
                            z + outwardZ * 1.5
                        ),
                        outwardX,
                        outwardZ
                    );
                }
            }
        }
        int floorY = targetFootprint == null
            ? (int) Math.floor(building.bounds().minY())
            : targetFootprint.floorY() + 1;
        double centerX = targetFootprint == null
            ? (building.bounds().minX() + building.bounds().maxX()) * 0.5
            : (targetFootprint.minX() + targetFootprint.maxX() + 1) * 0.5;
        double outsideZ = targetFootprint == null
            ? building.bounds().minZ() - 2.0
            : targetFootprint.minZ() - 2.0;
        return new EvacuationPlan(new Vector3d(centerX, floorY, outsideZ), 0.0, -1.0);
    }

    private static boolean outsideFootprint(
        PrefabPlacementService.PlacementFootprint footprint,
        double x,
        double z
    ) {
        return footprint == null
            || x < footprint.minX() || x >= footprint.maxX() + 1.0
            || z < footprint.minZ() || z >= footprint.maxZ() + 1.0;
    }

    private void demolishBuilding(PlayerRef playerRef, UUID buildingId) {
        UUID worldId = playerRef.getWorldUuid();
        World world = worldId == null ? null : Universe.get().getWorld(worldId);
        BuildingPlacementRegistry.BuildingInstance building = placementRegistry.find(worldId, buildingId);
        if (world == null || building == null) {
            playerRef.sendMessage(Message.raw("Das Gebäude ist nicht mehr verfügbar."));
            return;
        }
        if (placementRegistry.isUpgrading(worldId, buildingId)) {
            playerRef.sendMessage(Message.raw("Ein Gebäude kann während des Ausbaus nicht abgerissen werden."));
            return;
        }
        if (!placementService.demolish(world, building)) {
            playerRef.sendMessage(Message.raw(
                "Abriss nicht möglich: Die Gebäude-Chunks sind noch nicht vollständig geladen."
            ));
            return;
        }
        unitRegistry.workersAt(buildingId).forEach(unitRegistry::clearWorkplace);
        if ("mine".equals(building.buildingType())) mineTunnelRegistry.removeMine(world, buildingId);
        farmRegistry.removeByBuildingInstance(worldId, buildingId);
        fieldRegistry.removeByBuildingInstance(worldId, buildingId);
        placementRegistry.remove(worldId, buildingId);
        buildingPersistence.save(world, placementRegistry.buildings(worldId));
        Session session = sessions.get(playerRef.getUuid());
        if (session != null && buildingId.equals(session.selectedBuildingId)) clearSelection(playerRef, session);
        playerRef.sendMessage(Message.raw(
            buildingDisplayName(building) + " abgerissen. Der ursprüngliche Boden wurde wiederhergestellt."
        ));
    }

    public void handleWorldJoin(World world) {
        if (world == null) return;
        UUID worldId = world.getWorldConfig().getUuid();
        List<BuildingPlacementRegistry.BuildingInstance> restored = buildingPersistence.load(world);
        placementRegistry.restoreWorld(worldId, restored);

        List<ConstructionSiteRegistry.PersistedSite> restoredSites = constructionPersistence.load(world);
        constructionRegistry.restore(restoredSites);
        for (ConstructionSiteRegistry.SiteState state : constructionRegistry.states()) {
            PrefabPlacementService.ConstructionSite site = state.site();
            if (!worldId.equals(site.worldId())) continue;
            placementRegistry.reserve(worldId, site.id(), site.candidate().footprint());
            if (site.isUpgrade() && !placementRegistry.beginUpgrade(worldId, site.upgradeBuildingId())) {
                System.err.println(
                    "[Civ Construction] Could not restore upgrade lock building=" + site.upgradeBuildingId()
                        + " site=" + site.id()
                );
            }
        }

        farmRegistry.clearWorld(worldId);
        fieldRegistry.clearWorld(worldId);
        for (BuildingPlacementRegistry.BuildingInstance building : restored) {
            if (building.placement() == null) continue;
            if ("wheat_field".equals(building.placement().definition().id())) {
                PrefabPlacementService.PlacedMarker fieldMarker = building.semanticVolumes().stream()
                    .filter(volume -> volume.hasTag(TYPE_TAG, "field"))
                    .filter(volume -> volume.hasTag(BUILDING_TAG, "farm"))
                    .findFirst().orElse(null);
                if (fieldMarker != null) {
                    PrefabPlacementService.PlacedMarker hydrated = hydrateMarkerBounds(world, fieldMarker);
                    if (hydrated != null && hydrated.bounds() != null) {
                        fieldRegistry.registerField(
                            building.id(), worldId, hydrated.id(), hydrated.position(), hydrated.bounds()
                        );
                    }
                }
                continue;
            }
            if (!"farm".equals(building.buildingType())) continue;
            List<PrefabPlacementService.PlacedMarker> entrances = building.semanticVolumes().stream()
                .filter(volume -> volume.hasTag(TYPE_TAG, "workplace_access"))
                .filter(volume -> volume.hasTag(BUILDING_TAG, "farm"))
                .toList();
            if (!entrances.isEmpty()) {
                PrefabPlacementService.PlacedMarker outputStorage = building.semanticVolumes().stream()
                    .filter(volume -> volume.hasTag(TYPE_TAG, "output_storage"))
                    .filter(volume -> volume.hasTag(BUILDING_TAG, "farm"))
                    .findFirst().orElse(null);
                farmRegistry.registerFarm(
                    worldId, building.id(), entrances, outputStorage,
                    building.placement().footprint(), building.placement().replacedFloorBlocks()
                );
            }
        }
    }

    private static PrefabPlacementService.PlacedMarker hydrateMarkerBounds(
        World world,
        PrefabPlacementService.PlacedMarker marker
    ) {
        if (marker == null || marker.bounds() != null || world == null) return marker;
        var volumeManager = world.getEntityStore().getStore().getResource(
            com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin.get().getManagerResourceType()
        );
        var volume = volumeManager == null ? null : volumeManager.getVolume(marker.id());
        if (volume == null || volume.getShape() == null || volume.getPosition() == null) return marker;
        Vector3d min = new Vector3d();
        Vector3d max = new Vector3d();
        volume.getShape().getWorldAABB(volume.getPosition(), min, max);
        return new PrefabPlacementService.PlacedMarker(
            marker.id(), marker.position(), marker.tags(),
            new BuildingBounds(min.x, min.y, min.z, max.x, max.y, max.z)
        );
    }

    private static String buildingDisplayName(BuildingPlacementRegistry.BuildingInstance building) {
        return building != null && building.placement() != null
            ? building.placement().definition().displayName() : "Gebäude";
    }

    private static String professionDisplayName(Profession profession) {
        if (profession == null) return "";
        return switch (profession) {
            case UNEMPLOYED -> "Arbeitslos";
            case FARMER -> "Bauer";
            case WOODCUTTER -> "Holzfäller";
            case MINER -> "Minenabbauer";
            case CONSTRUCTION_WORKER -> "Bauarbeiter";
        };
    }

    public boolean isClaimed(Ref<EntityStore> target) {
        return target != null && target.isValid() && unitRegistry.isClaimed(target);
    }

    public PersonActionsPage createFirstPersonActionsPage(PlayerRef playerRef, Ref<EntityStore> target) {
        if (target == null || !target.isValid() || !unitRegistry.isClaimed(target)) return null;
        return new PersonActionsPage(
            playerRef,
            () -> assignWoodcutter(playerRef, target),
            () -> assignMinerProfession(playerRef, target),
            () -> assignConstructionWorker(playerRef, target),
            () -> assignFarmerProfession(playerRef, target),
            () -> openNpcInventory(playerRef, target)
        );
    }

    public boolean openFirstPersonActions(
        Ref<EntityStore> playerEntityRef,
        PlayerRef playerRef,
        Ref<EntityStore> target,
        Store<EntityStore> store
    ) {
        if (playerEntityRef == null || !playerEntityRef.isValid()
            || target == null || !target.isValid() || !unitRegistry.isClaimed(target)) return false;
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) return false;
        PersonActionsPage page = createFirstPersonActionsPage(playerRef, target);
        if (page == null) return false;
        player.getPageManager().openCustomPage(playerEntityRef, store, page);
        return true;
    }

    private void openPersonActions(
        PlayerMouseButtonEvent event,
        PlayerRef playerRef,
        Ref<EntityStore> selected
    ) {
        Ref<EntityStore> playerEntityRef = event.getPlayerRef();
        event.getPlayer().getPageManager().openCustomPage(
            playerEntityRef,
            playerEntityRef.getStore(),
            new PersonActionsPage(
                playerRef,
                () -> assignWoodcutter(playerRef, selected),
                () -> assignMinerProfession(playerRef, selected),
                () -> assignConstructionWorker(playerRef, selected),
                () -> assignFarmerProfession(playerRef, selected),
                () -> openNpcInventory(playerRef, selected)
            )
        );
    }

    private void openNpcInventory(PlayerRef playerRef, Ref<EntityStore> target) {
        if (target == null || !target.isValid() || !unitRegistry.isClaimed(target)) {
            playerRef.sendMessage(Message.raw("Der ausgewählte Civ-Bewohner ist nicht mehr verfügbar."));
            return;
        }
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) return;
        Store<EntityStore> playerStore = playerEntityRef.getStore();
        Player player = playerStore.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) return;
        Store<EntityStore> npcStore = target.getStore();
        CombinedItemContainer inventory = InventoryComponent.getCombined(
            npcStore, target, InventoryComponent.HOTBAR_FIRST
        );
        DelegateItemContainer readOnlyInventory = new DelegateItemContainer(inventory);
        readOnlyInventory.setGlobalFilter(FilterType.DENY_ALL);
        player.getPageManager().setPageWithWindows(
            playerEntityRef, playerStore, Page.Bench, true,
            new Window[] {new ContainerWindow(readOnlyInventory)}
        );
    }

    private void assignSelectedFarmer(
        PlayerRef playerRef,
        Session session,
        FarmBuildingRegistry.FarmSite farm
    ) {
        removeInvalidCommandNpc(session);
        if (session.commandNpc == null) return;
        Ref<EntityStore> farmer = session.commandNpc;
        FarmFieldRegistry.FieldSite field =
            fieldRegistry.nearestField(farm.worldId(), farm.building().entranceBlock());
        if (field == null) {
            playerRef.sendMessage(Message.raw("Baue zuerst ein fertiges Weizenfeld in der Nähe der Farm."));
            return;
        }
        FarmBuildingRegistry.AssignmentResult result = farmRegistry.assignFarmer(farmer, farm);
        switch (result) {
            case ASSIGNED -> {
                activityRegistry.cancelManualMove(farmer);
                unitRegistry.cancelMoveTarget(farmer);
                unitRegistry.assignProfession(farmer, Profession.FARMER);
                unitRegistry.assignWorkplace(farmer, farm.buildingInstanceId());
                unitRegistry.setMoveTarget(farmer, farm.entranceTarget());
                playerRef.sendMessage(Message.raw("Bauer zugewiesen."));
            }
            case ALREADY_ASSIGNED -> playerRef.sendMessage(Message.raw("That NPC is already assigned to this farm."));
            case OCCUPIED -> playerRef.sendMessage(Message.raw("That farm already has a Farmer."));
        }
    }

    private void assignSelectedMiner(
        PlayerRef playerRef,
        Session session,
        BuildingPlacementRegistry.BuildingInstance mine
    ) {
        removeInvalidCommandNpc(session);
        if (session.commandNpc == null) return;
        if (placementRegistry.isUpgrading(mine.worldId(), mine.id())) {
            playerRef.sendMessage(Message.raw(
                "Diese Mine wird gerade erweitert und kann bis zur Fertigstellung nicht betreten werden."
            ));
            return;
        }
        Ref<EntityStore> miner = session.commandNpc;
        boolean hasConnector = mine.semanticVolumes().stream()
            .anyMatch(volume -> volume.hasTag(TYPE_TAG, "mine_tunnel_connector")
                && volume.hasTag(BUILDING_TAG, "mine"));
        if (!hasConnector) {
            playerRef.sendMessage(Message.raw("Diese Mine hat keinen gültigen Tunnelanschluss."));
            return;
        }
        farmRegistry.unassignFarmer(miner);
        activityRegistry.cancelManualMove(miner);
        unitRegistry.cancelMoveTarget(miner);
        unitRegistry.assignProfession(miner, Profession.MINER);
        unitRegistry.assignWorkplace(miner, mine.id());
        playerRef.sendMessage(Message.raw("Minenabbauer zugewiesen."));
    }

    private void assignFarmerProfession(PlayerRef playerRef, Ref<EntityStore> selected) {
        if (!unitRegistry.isClaimed(selected)) return;
        farmRegistry.unassignFarmer(selected);
        unitRegistry.clearWorkplace(selected);
        activityRegistry.cancelManualMove(selected);
        unitRegistry.cancelMoveTarget(selected);
        unitRegistry.assignProfession(selected, Profession.FARMER);
        playerRef.sendMessage(Message.raw("Bauer zugewiesen."));
    }

    private void assignMinerProfession(PlayerRef playerRef, Ref<EntityStore> selected) {
        if (!unitRegistry.isClaimed(selected)) return;
        farmRegistry.unassignFarmer(selected);
        unitRegistry.clearWorkplace(selected);
        activityRegistry.cancelManualMove(selected);
        unitRegistry.cancelMoveTarget(selected);
        unitRegistry.assignProfession(selected, Profession.MINER);
        playerRef.sendMessage(Message.raw("Minenabbauer zugewiesen."));
    }

    private void assignWoodcutter(PlayerRef playerRef, Ref<EntityStore> selected) {
        if (!unitRegistry.isClaimed(selected)) return;
        farmRegistry.unassignFarmer(selected);
        unitRegistry.clearWorkplace(selected);
        activityRegistry.cancelManualMove(selected);
        unitRegistry.cancelMoveTarget(selected);
        unitRegistry.assignProfession(selected, Profession.WOODCUTTER);
        playerRef.sendMessage(Message.raw("Woodcutter assigned."));
    }

    private void assignConstructionWorker(PlayerRef playerRef, Ref<EntityStore> selected) {
        if (!unitRegistry.isClaimed(selected)) return;
        farmRegistry.unassignFarmer(selected);
        unitRegistry.clearWorkplace(selected);
        activityRegistry.cancelManualMove(selected);
        unitRegistry.cancelMoveTarget(selected);
        unitRegistry.assignProfession(selected, Profession.CONSTRUCTION_WORKER);
        playerRef.sendMessage(Message.raw(
            "Bauarbeiter zugewiesen. Der Bewohner übernimmt automatisch die nächste freie Baustelle."
        ));
    }

    private void clearPlacement(PlayerRef playerRef, Session session) {
        placementService.cancelConstructionPreview(playerRef);
        session.placementDefinition = null;
        session.previewTarget = null;
        session.previewCandidate = null;
        session.collisionBoundaryVisible = false;
        boundaryDisplay.clear(playerRef);
    }

    private void removeInvalidCommandNpc(Session session) {
        if (session.commandNpc != null && !unitRegistry.isClaimed(session.commandNpc)) {
            session.commandNpc = null;
        }
    }

    public boolean consumeArmedClaim(PlayerRef playerRef) {
        return playerRef != null && claimArmed.remove(playerRef.getUuid());
    }

    private record EvacuationPlan(Vector3d point, double outwardX, double outwardZ) {
    }

    private static final class Session {
        private Ref<EntityStore> selectedNpc;
        private Ref<EntityStore> commandNpc;
        private UUID selectedBuildingId;
        private UUID selectedSiteId;
        private PrefabPlacementService.PlacementDefinition placementDefinition;
        private Vector3i previewTarget;
        private PrefabPlacementService.PlacementCandidate previewCandidate;
        private boolean collisionBoundaryVisible;
    }
}
