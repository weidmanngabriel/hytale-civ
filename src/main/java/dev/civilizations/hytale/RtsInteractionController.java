package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns per-player state for the deliberately small RTS camera/input validation spike.
 *
 * <p>This does not move NPCs yet. A right click records and reports the world-space
 * movement target so the camera -> cursor -> world-target interaction loop can be
 * validated before choosing an NPC role and navigation API.</p>
 */
public final class RtsInteractionController {

    private final RtsCameraController cameraController;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public RtsInteractionController(RtsCameraController cameraController) {
        this.cameraController = cameraController;
    }

    public boolean toggle(PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        Session removed = sessions.remove(playerId);

        if (removed != null) {
            cameraController.disable(playerRef);
            playerRef.sendMessage(Message.raw("Civ RTS test disabled."));
            return false;
        }

        sessions.put(playerId, new Session());
        cameraController.enable(playerRef);
        playerRef.sendMessage(Message.raw(
            "Civ RTS test enabled. Left click toggles entity selection; right click sets a movement target."
        ));
        return true;
    }

    public boolean isActive(PlayerRef playerRef) {
        return sessions.containsKey(playerRef.getUuid());
    }

    public void handleMouseButton(PlayerMouseButtonEvent event) {
        PlayerRef playerRef = event.getPlayerRefComponent();
        Session session = sessions.get(playerRef.getUuid());

        if (session == null || event.getMouseButton().state != MouseButtonState.Pressed) {
            return;
        }

        MouseButtonType button = event.getMouseButton().mouseButtonType;
        if (button == MouseButtonType.Left) {
            handleSelection(event, playerRef, session);
            event.setCancelled(true);
            return;
        }

        if (button == MouseButtonType.Right) {
            handleMoveTarget(event, playerRef, session);
            event.setCancelled(true);
        }
    }

    private void handleSelection(PlayerMouseButtonEvent event, PlayerRef playerRef, Session session) {
        Ref<EntityStore> target = event.getTargetEntityRef();
        if (target == null) {
            session.selected.clear();
            playerRef.sendMessage(Message.raw("Selection cleared."));
            return;
        }

        if (!session.selected.add(target)) {
            session.selected.remove(target);
        }

        playerRef.sendMessage(Message.raw("Selected entities: " + session.selected.size()));
    }

    private void handleMoveTarget(PlayerMouseButtonEvent event, PlayerRef playerRef, Session session) {
        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) {
            playerRef.sendMessage(Message.raw("No ground target under cursor."));
            return;
        }

        session.lastMoveTarget = new Vector3i(targetBlock);
        playerRef.sendMessage(Message.raw(
            "Move target " + targetBlock.x + ", " + targetBlock.y + ", " + targetBlock.z
                + " for " + session.selected.size() + " selected entities."
        ));
    }

    private static final class Session {
        private final Set<Ref<EntityStore>> selected = new LinkedHashSet<>();
        private Vector3i lastMoveTarget;
    }
}
