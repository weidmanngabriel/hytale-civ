package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.joml.Vector3i;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns per-player state for the RTS camera/input validation spike.
 */
public final class RtsInteractionController {

    private final RtsCameraController cameraController;
    private final CivUnitRegistry unitRegistry;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> claimArmed = ConcurrentHashMap.newKeySet();

    public RtsInteractionController(
        RtsCameraController cameraController,
        CivUnitRegistry unitRegistry
    ) {
        this.cameraController = cameraController;
        this.unitRegistry = unitRegistry;
    }

    public boolean toggle(PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        Session removed = sessions.remove(playerId);

        if (removed != null) {
            claimArmed.remove(playerId);
            cameraController.disable(playerRef);
            playerRef.sendMessage(Message.raw("Civ RTS test disabled."));
            return false;
        }

        sessions.put(playerId, new Session());
        cameraController.enable(playerRef);
        playerRef.sendMessage(Message.raw(
            "Civ RTS test enabled. Use /civclaim then click an NPC to toggle Civ control. "
                + "Left click selects Civ units; right click moves the selection."
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
            handleMoveTarget(event, playerRef, session);
            event.setCancelled(true);
        }
    }

    public void handleDisconnect(PlayerDisconnectEvent event) {
        UUID playerId = event.getPlayerRef().getUuid();
        sessions.remove(playerId);
        claimArmed.remove(playerId);
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

        boolean claimed = unitRegistry.toggleClaim(target);
        if (!claimed) {
            sessions.values().forEach(session -> session.selected.remove(target));
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

        if (!session.selected.add(target)) {
            session.selected.remove(target);
        }

        session.selected.removeIf(ref -> !unitRegistry.isClaimed(ref));
        playerRef.sendMessage(Message.raw("Selected Civ units: " + session.selected.size()));
    }

    private void handleMoveTarget(PlayerMouseButtonEvent event, PlayerRef playerRef, Session session) {
        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) {
            playerRef.sendMessage(Message.raw("No ground target under cursor."));
            return;
        }

        session.selected.removeIf(ref -> !unitRegistry.isClaimed(ref));
        int assigned = unitRegistry.assignMoveTargets(session.selected, targetBlock);

        playerRef.sendMessage(Message.raw(
            "Move command " + targetBlock.x + ", " + targetBlock.y + ", " + targetBlock.z
                + " assigned to " + assigned + " Civ units."
        ));
    }

    private static final class Session {
        private final Set<Ref<EntityStore>> selected = new LinkedHashSet<>();
    }
}
