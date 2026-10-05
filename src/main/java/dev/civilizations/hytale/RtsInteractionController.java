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
        MineTunnelRegistry mineTunnelRegistry
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
    }

    public boolean toggle(PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        Session active = sessions.get(playerId);
        if (active != null) {
            clearPlacement(playerRef, active);
            cameraController.disable(playerRef);
            sessions.remove(playerId, active);
            playerRef.sendMessage(Message.raw("Civ RTS test disabled."));
            return false;
        }
        sessions.put(playerId, new Session());
        cameraController.enable(playerRef);
        playerRef.sendMessage(Message.raw(
            "Civ RTS test enabled. /civbuild öffnet das Baumenü; /civwiki öffnet die Civ-Hilfe."
        ));
        return true;
    }

    public void armClaim(PlayerRef playerRef) {
        claimArmed.add(playerRef.getUuid());
        playerRef.sendMessage(Message.raw(
            "Civ claim armed. Left click an NPC in First Person or RTS mode."
        ));
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

        Session session = sessions.get(playerRef.getUuid());
        MouseButtonType button = event.getMouseButton().mouseButtonType;
        if (session == null && button == MouseButtonType.Right) {
            Ref<EntityStore> target = event.getTargetEntityRef();
            playerRef.sendMessage(Message.raw(
                "[Civ debug] FP right-click event received; target="
                    + (target == null ? "none" : (target.isValid() ? "valid" : "invalid"))
            ));
        }

        if (button == MouseButtonType.Left
            && claimArmed.contains(playerRef.getUuid())
            && (session == null || session.placementDefinition == null)) {
            claimArmed.remove(playerRef.getUuid());
            handleClaim(event.getTargetEntityRef(), playerRef);
            event.setCancelled(true);
            return;
        }
        if (session == null) return;

        if (button == MouseButtonType.Left) {
            if (session.placementDefinition != null) {
                confirmPlacement(playerRef, session, event.getTargetBlock());
            } else {
                handleSelection(event, playerRef, session);
            }
            event.setCancelled(true);
            return;
        }

        if (button == MouseButtonType.Right) {
            if (session.placementDefinition != null) {
                String name = session.placementDefinition.displayName();
                clearPlacement(playerRef, session);
                playerRef.sendMessage(Message.raw(name + "-Platzierung abgebrochen."));
            } else {
                handleRightClick(event, playerRef, session);
            }
            event.setCancelled(true);
        }
    }

    public void handleMouseMotion(PlayerMouseMotionEvent event) {
        // Native Builder Paste owns the construction ghost.
    }

    public void handleDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        Session session = sessions.remove(playerRef.getUuid());
        claimArmed.remove(playerRef.getUuid());
        if (session != null) clearPlacement(playerRef, session);
        releaseConstructionReservations(playerRef);
        placementService.cancelConstructionSites(playerRef);
    }

    public void cancelConstructionSites(PlayerRef playerRef) {
        releaseConstructionReservations(playerRef);
        int removed = placementService.cancelConstructionSites(playerRef);
        playerRef.sendMessage(Message.raw(
            removed == 0
                ? "Keine Civ-Baustellenvorschau zum Entfernen."
                : removed + " Civ-Baustellenvorschau(en) entfernt."
        ));
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
        player.getPageManager().openCustomPage(
            playerEntityRef,
            store,
            new BuildingMenuPage(
                playerRef,
                () -> startPlacement(playerRef, session, PrefabPlacementService.FARM),
                () -> startPlacement(playerRef, session, PrefabPlacementService.MINE),
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
            PrefabPlacementService.PlacementCandidate candidate =
                validatePlacement(worldId, world, targetBlock, definition);
            if (!candidate.valid()) {
                playerRef.sendMessage(Message.raw(
                    definition.displayName() + " kann hier nicht gebaut werden: " + candidate.invalidReason()
                ));
                session.previewTarget = new Vector3i(targetBlock);
                session.previewCandidate = candidate;
                return;
            }
            PrefabPlacementService.ConstructionSite site =
                placementService.createConstructionSiteAtClick(playerRef, candidate);
            placementRegistry.reserve(worldId, site.id(), candidate.footprint());
            session.placementDefinition = null;
            session.previewTarget = null;
            session.previewCandidate = null;
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
            sessions.values().forEach(otherSession -> {
                if (otherSession.selected != null
                    && unitRegistry.keyOf(otherSession.selected).equals(key)) {
                    otherSession.selected = null;
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

    private void handleSelection(
        PlayerMouseButtonEvent event,
        PlayerRef playerRef,
        Session session
    ) {
        Ref<EntityStore> target = event.getTargetEntityRef();
        if (target == null) {
            session.selected = null;
            playerRef.sendMessage(Message.raw("Selection cleared."));
            return;
        }
        if (!unitRegistry.isClaimed(target)) {
            playerRef.sendMessage(Message.raw("That entity is not a Civ unit. Use /civclaim first."));
            return;
        }
        session.selected = target;
        playerRef.sendMessage(Message.raw(
            "Civ-Bewohner ausgewählt. Rechtsklick auf ihn öffnet die Aktionen."
        ));
    }

    private void handleRightClick(
        PlayerMouseButtonEvent event,
        PlayerRef playerRef,
        Session session
    ) {
        removeInvalidSelection(session);
        Ref<EntityStore> targetEntity = event.getTargetEntityRef();
        if (targetEntity != null && isSelected(session, targetEntity)) {
            openPersonActions(event, playerRef, session.selected);
            return;
        }

        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) {
            playerRef.sendMessage(Message.raw("Kein Bodenziel unter dem Cursor."));
            return;
        }

        UUID worldId = playerRef.getWorldUuid();
        BuildingPlacementRegistry.BuildingInstance building = placementRegistry.findAt(worldId, targetBlock);
        if (building != null) {
            FarmBuildingRegistry.FarmSite farm =
                farmRegistry.findByBuildingInstance(worldId, building.id());
            if (session.selected != null && farm != null) {
                assignSelectedFarmer(playerRef, session, farm);
            } else if (session.selected != null
                && "mine".equals(building.buildingType())
                && unitRegistry.getProfession(session.selected) == Profession.MINER) {
                assignSelectedMiner(playerRef, session, building);
            } else {
                openBuildingActions(event, playerRef, session, building);
            }
            return;
        }

        if (session.selected == null) {
            playerRef.sendMessage(Message.raw("Wähle zuerst einen Civ-Bewohner aus."));
            return;
        }
        boolean accepted = activityRegistry.orderManualMove(
            session.selected,
            new WorldPosition(targetBlock.x + 0.5, targetBlock.y + 1.0, targetBlock.z + 0.5)
        );
        playerRef.sendMessage(Message.raw(
            "Bewegungsbefehl " + targetBlock.x + ", " + targetBlock.y + ", " + targetBlock.z
                + " an " + (accepted ? 1 : 0) + " Civ-Bewohner."
        ));
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

    private void selectWorkerFromBuilding(
        PlayerRef playerRef,
        Session session,
        Ref<EntityStore> worker
    ) {
        if (worker == null || !worker.isValid() || !unitRegistry.isClaimed(worker)) {
            playerRef.sendMessage(Message.raw("Der Arbeiter ist nicht mehr verfügbar."));
            return;
        }
        session.selected = worker;
        CivInhabitantData data = unitRegistry.getInhabitantData(worker);
        String name = data == null || !data.hasIdentity() ? "Civ-Bewohner" : data.fullName();
        playerRef.sendMessage(Message.raw(
            name + " ausgewählt. Rechtsklick auf den Boden gibt einen manuellen Bewegungsbefehl."
        ));
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

        try {
            placementService.createUpgradeConstructionSite(playerRef, world, building, targetPhase);
            int evacuated = evacuateMineWorkers(world, building);
            playerRef.sendMessage(Message.raw(
                "Mine wird auf Phase " + targetPhase + " erweitert. " + evacuated
                    + " Arbeiter wurden nach draußen gebracht; die Mine bleibt bis zur Fertigstellung gesperrt."
            ));
        } catch (RuntimeException exception) {
            placementRegistry.cancelUpgrade(worldId, buildingId);
            playerRef.sendMessage(Message.raw(
                "Ausbau konnte nicht gestartet werden: " + exception.getMessage()
            ));
        }
    }

    private int evacuateMineWorkers(
        World world,
        BuildingPlacementRegistry.BuildingInstance building
    ) {
        List<Ref<EntityStore>> workers = unitRegistry.workersAt(building.id());
        Vector3d baseTarget = safePointOutsideMine(world, building);
        int evacuated = 0;
        for (int index = 0; index < workers.size(); index++) {
            Ref<EntityStore> worker = workers.get(index);
            if (worker == null || !worker.isValid()) continue;
            Store<EntityStore> store = worker.getStore();
            TransformComponent transform = store.getComponent(
                worker, TransformComponent.getComponentType()
            );
            if (transform == null) continue;

            activityRegistry.cancelManualMove(worker);
            unitRegistry.cancelMoveTarget(worker);
            double sideOffset = (index - (workers.size() - 1) * 0.5) * 1.25;
            Vector3d target = new Vector3d(baseTarget.x + sideOffset, baseTarget.y, baseTarget.z);
            store.putComponent(
                worker,
                Teleport.getComponentType(),
                new Teleport(target, transform.getRotation())
            );
            evacuated++;
        }
        return evacuated;
    }

    private static Vector3d safePointOutsideMine(
        World world,
        BuildingPlacementRegistry.BuildingInstance building
    ) {
        PrefabPlacementService.PlacedMarker entrance = building.semanticVolumes().stream()
            .filter(volume -> volume.hasTag(TYPE_TAG, "workplace_access"))
            .filter(volume -> volume.hasTag(BUILDING_TAG, "mine"))
            .findFirst()
            .orElse(null);
        entrance = hydrateMarkerBounds(world, entrance);
        if (entrance != null && entrance.bounds() != null) {
            BuildingBounds bounds = entrance.bounds();
            double entranceX = (bounds.minX() + bounds.maxX()) * 0.5;
            double entranceZ = (bounds.minZ() + bounds.maxZ()) * 0.5;
            double buildingX = (building.bounds().minX() + building.bounds().maxX()) * 0.5;
            double buildingZ = (building.bounds().minZ() + building.bounds().maxZ()) * 0.5;
            double dx = entranceX - buildingX;
            double dz = entranceZ - buildingZ;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 0.01) {
                dx = 0.0;
                dz = -1.0;
                length = 1.0;
            }
            return new Vector3d(
                entranceX + dx / length * 3.0,
                bounds.minY(),
                entranceZ + dz / length * 3.0
            );
        }

        int floorY = building.placement() == null
            ? (int) Math.floor(building.bounds().minY())
            : building.placement().footprint().floorY() + 1;
        return new Vector3d(
            (building.bounds().minX() + building.bounds().maxX()) * 0.5,
            floorY,
            building.bounds().minZ() - 3.0
        );
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
        if ("mine".equals(building.buildingType())) {
            mineTunnelRegistry.removeMine(world, buildingId);
        }
        farmRegistry.removeByBuildingInstance(worldId, buildingId);
        fieldRegistry.removeByBuildingInstance(worldId, buildingId);
        placementRegistry.remove(worldId, buildingId);
        buildingPersistence.save(world, placementRegistry.buildings(worldId));
        playerRef.sendMessage(Message.raw(
            buildingDisplayName(building) + " abgerissen. Der ursprüngliche Boden wurde wiederhergestellt."
        ));
    }

    public void handleWorldJoin(World world) {
        if (world == null) return;
        UUID worldId = world.getWorldConfig().getUuid();
        List<BuildingPlacementRegistry.BuildingInstance> restored = buildingPersistence.load(world);
        placementRegistry.restoreWorld(worldId, restored);
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
                    PrefabPlacementService.PlacedMarker hydratedFieldMarker =
                        hydrateMarkerBounds(world, fieldMarker);
                    if (hydratedFieldMarker != null && hydratedFieldMarker.bounds() != null) {
                        fieldRegistry.registerField(
                            building.id(), worldId, hydratedFieldMarker.id(),
                            hydratedFieldMarker.position(), hydratedFieldMarker.bounds()
                        );
                    } else {
                        System.err.println(
                            "[Civ Farm] Could not restore wheat field bounds for volume "
                                + fieldMarker.id() + "; field registration skipped."
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
            com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin.get()
                .getManagerResourceType()
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
        if (building != null && building.placement() != null) {
            return building.placement().definition().displayName();
        }
        return "Gebäude";
    }

    private static String professionDisplayName(Profession profession) {
        if (profession == null) return "";
        return switch (profession) {
            case UNEMPLOYED -> "Arbeitslos";
            case FARMER -> "Bauer";
            case WOODCUTTER -> "Holzfäller";
            case MINER -> "Minenabbauer";
            case CONSTRUCTION_WORKER -> "Bauarbeiter";
            case SOLDIER -> "Soldat";
        };
    }

    public boolean isClaimed(Ref<EntityStore> target) {
        return target != null && target.isValid() && unitRegistry.isClaimed(target);
    }

    public PersonActionsPage createFirstPersonActionsPage(
        PlayerRef playerRef,
        Ref<EntityStore> target
    ) {
        if (target == null || !target.isValid() || !unitRegistry.isClaimed(target)) return null;
        return new PersonActionsPage(
            playerRef,
            () -> assignWoodcutter(playerRef, target),
            () -> assignMinerProfession(playerRef, target),
            () -> assignConstructionWorker(playerRef, target),
            () -> assignFarmerProfession(playerRef, target),
            () -> assignSoldier(playerRef, target),
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
                () -> assignSoldier(playerRef, selected),
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
            playerEntityRef,
            playerStore,
            Page.Bench,
            true,
            new Window[] {new ContainerWindow(readOnlyInventory)}
        );
    }

    private boolean isSelected(Session session, Ref<EntityStore> target) {
        return session.selected != null
            && unitRegistry.isClaimed(target)
            && unitRegistry.keyOf(session.selected).equals(unitRegistry.keyOf(target));
    }

    private void assignSelectedFarmer(
        PlayerRef playerRef,
        Session session,
        FarmBuildingRegistry.FarmSite farm
    ) {
        removeInvalidSelection(session);
        if (session.selected == null) {
            playerRef.sendMessage(Message.raw(
                "Select one claimed Civ NPC before right clicking the farm entrance."
            ));
            return;
        }
        Ref<EntityStore> farmer = session.selected;
        FarmFieldRegistry.FieldSite field =
            fieldRegistry.nearestField(farm.worldId(), farm.building().entranceBlock());
        if (field == null) {
            playerRef.sendMessage(Message.raw(
                "Baue zuerst ein fertiges Weizenfeld in der Nähe der Farm."
            ));
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
                playerRef.sendMessage(Message.raw(
                    "Bauer zugewiesen. Er läuft von der Farm zum nächsten Weizenfeld, arbeitet dort und kehrt zur Farm zurück."
                ));
            }
            case ALREADY_ASSIGNED ->
                playerRef.sendMessage(Message.raw("That NPC is already assigned to this farm."));
            case OCCUPIED ->
                playerRef.sendMessage(Message.raw("That farm already has a Farmer."));
        }
    }

    private void assignSelectedMiner(
        PlayerRef playerRef,
        Session session,
        BuildingPlacementRegistry.BuildingInstance mine
    ) {
        removeInvalidSelection(session);
        if (session.selected == null) {
            playerRef.sendMessage(Message.raw("Wähle zuerst einen Civ-Bewohner aus."));
            return;
        }
        if (placementRegistry.isUpgrading(mine.worldId(), mine.id())) {
            playerRef.sendMessage(Message.raw(
                "Diese Mine wird gerade erweitert und kann bis zur Fertigstellung nicht betreten werden."
            ));
            return;
        }
        Ref<EntityStore> miner = session.selected;
        boolean hasConnector = mine.semanticVolumes().stream()
            .anyMatch(volume -> volume.hasTag(TYPE_TAG, "mine_tunnel_connector")
                && volume.hasTag(BUILDING_TAG, "mine"));
        if (!hasConnector) {
            playerRef.sendMessage(Message.raw(
                "Diese Mine hat keinen gültigen Tunnelanschluss und kann noch keinen Abbauer beschäftigen."
            ));
            return;
        }
        farmRegistry.unassignFarmer(miner);
        activityRegistry.cancelManualMove(miner);
        unitRegistry.cancelMoveTarget(miner);
        unitRegistry.assignProfession(miner, Profession.MINER);
        unitRegistry.assignWorkplace(miner, mine.id());
        playerRef.sendMessage(Message.raw(
            "Minenabbauer zugewiesen. Er geht zum Tunnelanschluss und beginnt dort selbstständig mit dem Stollen."
        ));
    }

    private void assignFarmerProfession(PlayerRef playerRef, Ref<EntityStore> selected) {
        if (!unitRegistry.isClaimed(selected)) {
            playerRef.sendMessage(Message.raw("Der ausgewählte Civ-Bewohner ist nicht mehr verfügbar."));
            return;
        }
        farmRegistry.unassignFarmer(selected);
        unitRegistry.clearWorkplace(selected);
        activityRegistry.cancelManualMove(selected);
        unitRegistry.cancelMoveTarget(selected);
        unitRegistry.assignProfession(selected, Profession.FARMER);
        playerRef.sendMessage(Message.raw(
            "Bauer zugewiesen. Weise ihm jetzt per Rechtsklick den Arbeitsbereich einer fertigen Farm zu."
        ));
    }

    private void assignMinerProfession(PlayerRef playerRef, Ref<EntityStore> selected) {
        if (!unitRegistry.isClaimed(selected)) {
            playerRef.sendMessage(Message.raw("Der ausgewählte Civ-Bewohner ist nicht mehr verfügbar."));
            return;
        }
        farmRegistry.unassignFarmer(selected);
        unitRegistry.clearWorkplace(selected);
        activityRegistry.cancelManualMove(selected);
        unitRegistry.cancelMoveTarget(selected);
        unitRegistry.assignProfession(selected, Profession.MINER);
        playerRef.sendMessage(Message.raw(
            "Minenabbauer zugewiesen. Weise ihm jetzt per Rechtsklick eine fertige Mine zu."
        ));
    }

    private void assignWoodcutter(PlayerRef playerRef, Ref<EntityStore> selected) {
        if (!unitRegistry.isClaimed(selected)) {
            playerRef.sendMessage(Message.raw("The selected Civ NPC is no longer available."));
            return;
        }
        farmRegistry.unassignFarmer(selected);
        unitRegistry.clearWorkplace(selected);
        activityRegistry.cancelManualMove(selected);
        unitRegistry.cancelMoveTarget(selected);
        unitRegistry.assignProfession(selected, Profession.WOODCUTTER);
        playerRef.sendMessage(Message.raw(
            "Woodcutter assigned. The NPC will search nearby for the closest tree and fell it."
        ));
    }

    private void assignConstructionWorker(PlayerRef playerRef, Ref<EntityStore> selected) {
        if (!unitRegistry.isClaimed(selected)) {
            playerRef.sendMessage(Message.raw("The selected Civ NPC is no longer available."));
            return;
        }
        farmRegistry.unassignFarmer(selected);
        unitRegistry.clearWorkplace(selected);
        activityRegistry.cancelManualMove(selected);
        unitRegistry.cancelMoveTarget(selected);
        unitRegistry.assignProfession(selected, Profession.CONSTRUCTION_WORKER);
        playerRef.sendMessage(Message.raw(
            "Bauarbeiter zugewiesen. Der Bewohner übernimmt automatisch die nächste freie Baustelle."
        ));
    }

    private void assignSoldier(PlayerRef playerRef, Ref<EntityStore> selected) {
        if (!unitRegistry.isClaimed(selected)) {
            playerRef.sendMessage(Message.raw("Der ausgewählte Civ-Bewohner ist nicht mehr verfügbar."));
            return;
        }
        farmRegistry.unassignFarmer(selected);
        unitRegistry.clearWorkplace(selected);
        activityRegistry.cancelManualMove(selected);
        unitRegistry.cancelMoveTarget(selected);
        unitRegistry.assignProfession(selected, Profession.SOLDIER);
        playerRef.sendMessage(Message.raw(
            "Soldat zugewiesen. Er greift selbstständig nahe NPCs an, deren native Hytale-Rolle gegenüber Spielern feindlich ist."
        ));
    }

    private void releaseConstructionReservations(PlayerRef playerRef) {
        UUID ownerId = playerRef.getUuid();
        placementService.constructionSites().stream()
            .filter(site -> site.ownerId().equals(ownerId))
            .forEach(site -> {
                placementRegistry.release(site.worldId(), site.id());
                if (site.isUpgrade()) {
                    placementRegistry.cancelUpgrade(site.worldId(), site.upgradeBuildingId());
                }
            });
    }

    private void clearPlacement(PlayerRef playerRef, Session session) {
        placementService.cancelConstructionPreview(playerRef);
        session.placementDefinition = null;
        session.previewTarget = null;
        session.previewCandidate = null;
    }

    private void removeInvalidSelection(Session session) {
        if (session.selected != null && !unitRegistry.isClaimed(session.selected)) {
            session.selected = null;
        }
    }

    public boolean consumeArmedClaim(PlayerRef playerRef) {
        return playerRef != null && claimArmed.remove(playerRef.getUuid());
    }

    private static final class Session {
        private Ref<EntityStore> selected;
        private PrefabPlacementService.PlacementDefinition placementDefinition;
        private Vector3i previewTarget;
        private PrefabPlacementService.PlacementCandidate previewCandidate;
    }
}
