package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.civilizations.core.Profession;
import org.joml.Vector3i;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns per-player state for the RTS validation spike and the farm vertical slice.
 */
public final class RtsInteractionController {

    private final RtsCameraController cameraController;
    private final CivUnitRegistry unitRegistry;
    private final FarmBuildingRegistry farmRegistry;
    private final FarmPrefabService farmPrefabService;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> claimArmed = ConcurrentHashMap.newKeySet();
    private final Set<UUID> farmPlacementArmed = ConcurrentHashMap.newKeySet();

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
            farmPlacementArmed.remove(playerId);
            cameraController.disable(playerRef);
            playerRef.sendMessage(Message.raw("Civ RTS test disabled."));
            return false;
        }

        sessions.put(playerId, new Session());
        cameraController.enable(playerRef);
        playerRef.sendMessage(Message.raw(
            "Civ RTS test enabled. /civclaim claims NPCs; /civfarm arms farm placement. "
                + "Left click selects Civ units; right click moves or assigns a selected unit to a farm entrance."
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

    public void armFarmPlacement(PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        if (!sessions.containsKey(playerId)) {
            playerRef.sendMessage(Message.raw("Enable /civrtstest before placing a farm."));
            return;
        }

        farmPlacementArmed.add(playerId);
        playerRef.sendMessage(Message.raw(
            "Farm placement armed. Right click reasonably flat ground; the entrance faces south."
        ));
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
            handleRightClick(event, playerRef, session);
            event.setCancelled(true);
        }
    }

    public void handleDisconnect(PlayerDisconnectEvent event) {
        UUID playerId = event.getPlayerRef().getUuid();
        sessions.remove(playerId);
        claimArmed.remove(playerId);
        farmPlacementArmed.remove(playerId);
    }

    private void handleLeftClick(
        PlayerMouseButtonEvent event,
        PlayerRef playerRef,
        Session session
    ) {
        if (claimArmed.remove(playerRef.getUuid())) {
            handleClaim(event, playerRef);
            return;
        }

        handleSelection(event, playerRef, session);
    }

    private void handleClaim(PlayerMouseButtonEvent event, PlayerRef playerRef) {
        Ref<EntityStore> target = event.getTargetEntityRef();
        if (target == null || !target.isValid()) {
            playerRef.sendMessage(Message.raw("No NPC under cursor."));
            return;
        }

        NPCEntity npc = target.getStore().getComponentConcurrent(target, NPCEntity.getComponentType());
        if (npc == null) {
            playerRef.sendMessage(Message.raw("Target is not an NPCEntity and cannot be claimed."));
            return;
        }

        CivUnitRegistry.UnitKey key = unitRegistry.keyOf(target);
        boolean claimed = unitRegistry.toggleClaim(target);
        if (!claimed) {
            farmRegistry.unassignFarmer(target);
            sessions.values().forEach(otherSession -> otherSession.selected.remove(key));
        }

        playerRef.sendMessage(Message.raw(
            claimed ? "NPC claimed as Civ test unit." : "NPC released from Civ control."
        ));
    }

    private void handleSelection(PlayerMouseButtonEvent event, PlayerRef playerRef, Session session) {
        Ref<EntityStore> target = event.getTargetEntityRef();
        if (target == null) {
            session.selected.clear();
            playerRef.sendMessage(Message.raw("Selection cleared."));
            return;
        }

        if (!unitRegistry.isClaimed(target)) {
            playerRef.sendMessage(Message.raw("That entity is not a Civ unit. Use /civclaim first."));
            return;
        }

        CivUnitRegistry.UnitKey key = unitRegistry.keyOf(target);
        if (session.selected.remove(key) == null) {
            session.selected.put(key, target);
        }

        removeInvalidSelections(session);
        playerRef.sendMessage(Message.raw("Selected Civ units: " + session.selected.size()));
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

        if (farmPlacementArmed.remove(playerRef.getUuid())) {
            placeFarm(playerRef, targetBlock);
            return;
        }

        UUID worldId = playerRef.getWorldUuid();
        FarmBuildingRegistry.FarmSite farm = farmRegistry.findByEntranceHit(worldId, targetBlock);
        if (farm != null) {
            assignSelectedFarmer(playerRef, session, farm);
            return;
        }

        removeInvalidSelections(session);
        int assigned = unitRegistry.assignMoveTargets(session.selected.values(), targetBlock);

        playerRef.sendMessage(Message.raw(
            "Move command " + targetBlock.x + ", " + targetBlock.y + ", " + targetBlock.z
                + " assigned to " + assigned + " Civ units."
        ));
    }

    private void placeFarm(PlayerRef playerRef, Vector3i targetBlock) {
        UUID worldId = playerRef.getWorldUuid();
        World world = worldId == null ? null : Universe.get().getWorld(worldId);

        if (world == null) {
            playerRef.sendMessage(Message.raw("Could not resolve your current world."));
            return;
        }

        try {
            farmPrefabService.placeFarm(playerRef, world, targetBlock);
            FarmBuildingRegistry.FarmSite site = farmRegistry.registerFarm(worldId, targetBlock);
            playerRef.sendMessage(Message.raw(
                "Farm " + site.building().id() + " placed. "
                    + "Select exactly one claimed NPC and right click the doorway to assign a Farmer."
            ));
        } catch (RuntimeException exception) {
            playerRef.sendMessage(Message.raw("Farm placement failed: " + exception.getMessage()));
        }
    }

    private void assignSelectedFarmer(
        PlayerRef playerRef,
        Session session,
        FarmBuildingRegistry.FarmSite farm
    ) {
        removeInvalidSelections(session);

        if (session.selected.size() != 1) {
            playerRef.sendMessage(Message.raw(
                "Select exactly one claimed Civ NPC before right clicking the farm entrance."
            ));
            return;
        }

        Ref<EntityStore> farmer = session.selected.values().iterator().next();
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

    private void removeInvalidSelections(Session session) {
        session.selected.entrySet().removeIf(entry -> !unitRegistry.isClaimed(entry.getValue()));
    }

    private static final class Session {
        private final Map<CivUnitRegistry.UnitKey, Ref<EntityStore>> selected = new LinkedHashMap<>();
    }
}
