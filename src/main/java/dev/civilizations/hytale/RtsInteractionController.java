package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.civilizations.core.Profession;
import org.joml.Vector2fc;
import org.joml.Vector3i;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RtsInteractionController {

    private static final int TOOLBAR_LEFT = 16;
    private static final int TOOLBAR_TOP = 220;
    private static final int TOOLBAR_WIDTH = 124;
    private static final int TOOLBAR_HEIGHT = 72;

    private final RtsCameraController cameraController;
    private final CivUnitRegistry unitRegistry;
    private final FarmBuildingRegistry farmRegistry;
    private final FarmPrefabService farmPrefabService;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> claimArmed = ConcurrentHashMap.newKeySet();

    public RtsInteractionController(
        RtsCameraController cameraController,
        CivUnitRegistry unitRegistry,
        FarmBuildingRegistry farmRegistry,
        FarmPrefabService farmPrefabService
    ) {
        this.cameraController = cameraController;
        this.unitRegistry = unitRegistry;
        this.farmRegistry = farmRegistry;
        this.farmPrefabService = farmPrefabService;
    }

    public boolean toggle(PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        Session removed = sessions.remove(playerId);

        if (removed != null) {
            claimArmed.remove(playerId);
            clearPlacement(playerRef, removed);
            removeToolbar(playerRef);
            cameraController.disable(playerRef);
            playerRef.sendMessage(Message.raw("Civ RTS test disabled."));
            return false;
        }

        sessions.put(playerId, new Session());
        cameraController.enable(playerRef);
        showToolbar(playerRef);
        playerRef.sendMessage(Message.raw(
            "Civ RTS test enabled. Bauen ist links im RTS-Menü; "
                + "Linksklick platziert einen ausgewählten Bau, Rechtsklick bricht ihn ab."
        ));
        return true;
    }

    public void armClaim(PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        if (!sessions.containsKey(playerId)) {
            playerRef.sendMessage(Message.raw("Enable /civrtstest before claiming an NPC."));
            return;
        }

        claimArmed.add(playerId);
        playerRef.sendMessage(Message.raw("Civ claim armed. Left click an NPC to toggle Civ control."));
    }

    /**
     * Debug shortcut retained while the RTS building menu is still being validated.
     */
    public void armFarmPlacement(PlayerRef playerRef) {
        Session session = sessions.get(playerRef.getUuid());
        if (session == null) {
            playerRef.sendMessage(Message.raw("Enable /civrtstest before placing a farm."));
            return;
        }

        startFarmPlacement(playerRef, session);
    }

    public void handleMouseButton(PlayerMouseButtonEvent event) {
        PlayerRef playerRef = event.getPlayerRefComponent();
        Session session = sessions.get(playerRef.getUuid());

        if (session == null || event.getMouseButton().state != MouseButtonState.Pressed) {
            return;
        }

        MouseButtonType button = event.getMouseButton().mouseButtonType;
        if (button == MouseButtonType.Left) {
            handleLeftClick(event, playerRef, session);
            event.setCancelled(true);
            return;
        }

        if (button == MouseButtonType.Right) {
            if (session.placingFarm) {
                clearPlacement(playerRef, session);
                playerRef.sendMessage(Message.raw("Farm-Platzierung abgebrochen."));
            } else {
                handleRightClick(event, playerRef, session);
            }
            event.setCancelled(true);
        }
    }

    public void handleMouseMotion(PlayerMouseMotionEvent event) {
        Ref<EntityStore> playerEntityRef = event.getPlayerRef();
        PlayerRef playerRef = playerEntityRef.getStore().getComponent(
            playerEntityRef,
            PlayerRef.getComponentType()
        );
        if (playerRef == null) {
            return;
        }

        Session session = sessions.get(playerRef.getUuid());
        if (session == null || !session.placingFarm) {
            return;
        }

        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) {
            return;
        }

        if (session.previewTarget != null && session.previewTarget.equals(targetBlock)) {
            return;
        }

        UUID worldId = playerRef.getWorldUuid();
        World world = worldId == null ? null : Universe.get().getWorld(worldId);
        if (world == null) {
            return;
        }

        try {
            FarmPrefabService.PlacementCandidate candidate =
                validateFarmPlacement(worldId, world, targetBlock);
            session.previewTarget = new Vector3i(targetBlock);
            session.previewCandidate = candidate;
            farmPrefabService.showPreview(playerRef, candidate);
        } catch (RuntimeException exception) {
            session.previewTarget = new Vector3i(targetBlock);
            session.previewCandidate = null;
        }
    }

    @SuppressWarnings("deprecation")
    public void handleInteract(PlayerInteractEvent event) {
        if (event.getActionType() != InteractionType.Use) {
            return;
        }

        Ref<EntityStore> playerEntityRef = event.getPlayerRef();
        PlayerRef playerRef = playerEntityRef.getStore().getComponent(
            playerEntityRef,
            PlayerRef.getComponentType()
        );
        if (playerRef == null) {
            return;
        }

        Session session = sessions.get(playerRef.getUuid());
        if (session == null) {
            return;
        }

        if (session.placingFarm) {
            playerRef.sendMessage(Message.raw(
                "Farm-Platzierung aktiv: Linksklick platziert, Rechtsklick bricht ab."
            ));
            event.setCancelled(true);
            return;
        }

        removeInvalidSelection(session);
        if (session.selected == null) {
            playerRef.sendMessage(Message.raw("Select one Civ NPC before pressing F."));
            event.setCancelled(true);
            return;
        }

        Ref<EntityStore> selected = session.selected;
        event.getPlayer().getPageManager().openCustomPage(
            playerEntityRef,
            playerEntityRef.getStore(),
            new PersonActionsPage(
                playerRef,
                () -> assignWoodcutter(playerRef, selected)
            )
        );
        event.setCancelled(true);
    }

    public void handleDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        Session session = sessions.remove(playerRef.getUuid());
        claimArmed.remove(playerRef.getUuid());
        if (session != null) {
            clearPlacement(playerRef, session);
        }
    }

    private void handleLeftClick(
        PlayerMouseButtonEvent event,
        PlayerRef playerRef,
        Session session
    ) {
        if (session.placingFarm) {
            confirmFarmPlacement(playerRef, session, event.getTargetBlock());
            return;
        }

        if (isBuildToolbarClick(event.getScreenPoint())) {
            openBuildingMenu(event, playerRef, session);
            return;
        }

        if (claimArmed.remove(playerRef.getUuid())) {
            handleClaim(event, playerRef);
            return;
        }

        handleSelection(event, playerRef, session);
    }

    private void openBuildingMenu(
        PlayerMouseButtonEvent event,
        PlayerRef playerRef,
        Session session
    ) {
        clearPlacement(playerRef, session);
        Ref<EntityStore> playerEntityRef = event.getPlayerRef();
        event.getPlayer().getPageManager().openCustomPage(
            playerEntityRef,
            playerEntityRef.getStore(),
            new BuildingMenuPage(
                playerRef,
                () -> startFarmPlacement(playerRef, session)
            )
        );
    }

    private void startFarmPlacement(PlayerRef playerRef, Session session) {
        clearPlacement(playerRef, session);
        session.placingFarm = true;
        playerRef.sendMessage(Message.raw(
            "Farm ausgewählt. Vorschau mit der Maus bewegen; "
                + "Linksklick platziert, Rechtsklick bricht ab."
        ));
    }

    private void confirmFarmPlacement(
        PlayerRef playerRef,
        Session session,
        Vector3i targetBlock
    ) {
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

        try {
            // The preview is advisory only. Recompute from current shared world state
            // so simultaneous multiplayer placements cannot both commit.
            FarmPrefabService.PlacementCandidate candidate =
                validateFarmPlacement(worldId, world, targetBlock);
            if (!candidate.valid()) {
                playerRef.sendMessage(Message.raw(
                    "Farm kann hier nicht gebaut werden: " + candidate.invalidReason()
                ));
                session.previewTarget = new Vector3i(targetBlock);
                session.previewCandidate = candidate;
                farmPrefabService.showPreview(playerRef, candidate);
                return;
            }

            FarmPrefabService.PlacedFarm placedFarm =
                farmPrefabService.placeFarm(playerRef, world, candidate);
            FarmBuildingRegistry.FarmSite site = farmRegistry.registerFarm(
                worldId,
                placedFarm.entranceBlocks(),
                placedFarm.footprint(),
                placedFarm.replacedFloorBlocks()
            );

            clearPlacement(playerRef, session);
            playerRef.sendMessage(Message.raw(
                "Farm " + site.building().id() + " gebaut. "
                    + "Der ursprüngliche Boden wurde für einen späteren Abriss gespeichert."
            ));
        } catch (RuntimeException exception) {
            playerRef.sendMessage(Message.raw("Farm placement failed: " + exception.getMessage()));
        }
    }

    private FarmPrefabService.PlacementCandidate validateFarmPlacement(
        UUID worldId,
        World world,
        Vector3i targetBlock
    ) {
        FarmPrefabService.PlacementCandidate candidate =
            farmPrefabService.validatePlacement(world, targetBlock);
        if (candidate.valid() && farmRegistry.overlaps(worldId, candidate.footprint())) {
            return candidate.invalidate("Die Fläche überschneidet sich mit einem Civ-Gebäude.");
        }
        return candidate;
    }

    private void handleClaim(PlayerMouseButtonEvent event, PlayerRef playerRef) {
        Ref<EntityStore> target = event.getTargetEntityRef();
        if (target == null || !target.isValid()) {
            playerRef.sendMessage(Message.raw("No NPC under cursor."));
            return;
        }

        NPCEntity npc = target.getStore().getComponentConcurrent(
            target,
            NPCEntity.getComponentType()
        );
        if (npc == null) {
            playerRef.sendMessage(Message.raw("Target is not an NPCEntity and cannot be claimed."));
            return;
        }

        CivUnitRegistry.UnitKey key = unitRegistry.keyOf(target);
        boolean claimed = unitRegistry.toggleClaim(target);
        if (!claimed) {
            farmRegistry.unassignFarmer(target);
            sessions.values().forEach(otherSession -> {
                if (otherSession.selected != null
                    && unitRegistry.keyOf(otherSession.selected).equals(key)) {
                    otherSession.selected = null;
                }
            });
        }

        playerRef.sendMessage(Message.raw(
            claimed ? "NPC claimed as Civ test unit." : "NPC released from Civ control."
        ));
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
        playerRef.sendMessage(Message.raw("Civ unit selected. Press F for actions."));
    }

    private void handleRightClick(
        PlayerMouseButtonEvent event,
        PlayerRef playerRef,
        Session session
    ) {
        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) {
            playerRef.sendMessage(Message.raw("No ground target under cursor."));
            return;
        }

        UUID worldId = playerRef.getWorldUuid();
        FarmBuildingRegistry.FarmSite farm = farmRegistry.findByEntranceHit(worldId, targetBlock);
        if (farm != null) {
            assignSelectedFarmer(playerRef, session, farm);
            return;
        }

        removeInvalidSelection(session);
        if (session.selected == null) {
            playerRef.sendMessage(Message.raw("Select one Civ NPC before issuing a move command."));
            return;
        }

        int assigned = unitRegistry.assignMoveTargets(List.of(session.selected), targetBlock);
        playerRef.sendMessage(Message.raw(
            "Move command " + targetBlock.x + ", " + targetBlock.y + ", " + targetBlock.z
                + " assigned to " + assigned + " Civ unit."
        ));
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
        FarmBuildingRegistry.AssignmentResult result = farmRegistry.assignFarmer(farmer, farm);

        switch (result) {
            case ASSIGNED -> {
                unitRegistry.assignProfession(farmer, Profession.FARMER);
                unitRegistry.setMoveTarget(farmer, farm.entranceTarget());
                playerRef.sendMessage(Message.raw(
                    "Farmer assigned to " + farm.building().id()
                        + ". Production: 1 wheat per 5 seconds of work, target 10."
                ));
            }
            case ALREADY_ASSIGNED ->
                playerRef.sendMessage(Message.raw("That NPC is already assigned to this farm."));
            case OCCUPIED ->
                playerRef.sendMessage(Message.raw("That farm already has a Farmer."));
            case COMPLETE ->
                playerRef.sendMessage(Message.raw("That farm already contains 10 wheat."));
        }
    }

    private void assignWoodcutter(PlayerRef playerRef, Ref<EntityStore> selected) {
        if (!unitRegistry.isClaimed(selected)) {
            playerRef.sendMessage(Message.raw("The selected Civ NPC is no longer available."));
            return;
        }

        farmRegistry.unassignFarmer(selected);
        unitRegistry.clearMoveTarget(selected);
        unitRegistry.assignProfession(selected, Profession.WOODCUTTER);
        playerRef.sendMessage(Message.raw(
            "Woodcutter assigned. The NPC will search nearby for the closest tree and fell it."
        ));
    }

    private void clearPlacement(PlayerRef playerRef, Session session) {
        if (session.previewTarget != null || session.previewCandidate != null) {
            try {
                farmPrefabService.clearPreview(playerRef);
            } catch (RuntimeException ignored) {
                // Preview cleanup must not block RTS teardown or disconnect handling.
            }
        }
        session.placingFarm = false;
        session.previewTarget = null;
        session.previewCandidate = null;
    }

    private void showToolbar(PlayerRef playerRef) {
        Player player = getPlayer(playerRef);
        if (player != null) {
            player.getHudManager().addCustomHud(playerRef, new RtsToolbarHud(playerRef));
        }
    }

    private void removeToolbar(PlayerRef playerRef) {
        Player player = getPlayer(playerRef);
        if (player != null) {
            player.getHudManager().removeCustomHud(playerRef, RtsToolbarHud.KEY);
        }
    }

    private static Player getPlayer(PlayerRef playerRef) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return null;
        }
        return playerEntityRef.getStore().getComponent(
            playerEntityRef,
            Player.getComponentType()
        );
    }

    private void removeInvalidSelection(Session session) {
        if (session.selected != null && !unitRegistry.isClaimed(session.selected)) {
            session.selected = null;
        }
    }

    private static boolean isBuildToolbarClick(Vector2fc point) {
        return point != null
            && point.x() >= TOOLBAR_LEFT
            && point.x() < TOOLBAR_LEFT + TOOLBAR_WIDTH
            && point.y() >= TOOLBAR_TOP
            && point.y() < TOOLBAR_TOP + TOOLBAR_HEIGHT;
    }

    private static final class Session {
        private Ref<EntityStore> selected;
        private boolean placingFarm;
        private Vector3i previewTarget;
        private FarmPrefabService.PlacementCandidate previewCandidate;
    }
}
