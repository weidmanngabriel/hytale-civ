package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Presentation-only controller for the compact HUD of the currently inspected Civ NPC. */
public final class CivSelectedNpcHudController {

    private final CivUnitRegistry unitRegistry;
    private final NpcInfoProvider infoProvider;
    private final Map<UUID, Ref<EntityStore>> selectedByPlayer = new ConcurrentHashMap<>();
    private final Set<UUID> rtsActivePlayers = ConcurrentHashMap.newKeySet();

    public CivSelectedNpcHudController(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry
    ) {
        this.unitRegistry = unitRegistry;
        this.infoProvider = new NpcInfoProvider(unitRegistry, activityRegistry);
    }

    public void setRtsActive(PlayerRef playerRef, boolean active) {
        if (active) {
            rtsActivePlayers.add(playerRef.getUuid());
            return;
        }
        rtsActivePlayers.remove(playerRef.getUuid());
        clear(playerRef);
    }

    public void handleMouseButton(PlayerMouseButtonEvent event) {
        PlayerRef playerRef = event.getPlayerRefComponent();
        if (event.getMouseButton().state != MouseButtonState.Pressed
            || event.getMouseButton().mouseButtonType != MouseButtonType.Left
            || !rtsActivePlayers.contains(playerRef.getUuid())) {
            return;
        }

        Ref<EntityStore> target = event.getTargetEntityRef();
        if (target == null || !target.isValid() || !unitRegistry.isClaimed(target)) {
            clear(playerRef);
            return;
        }

        selectedByPlayer.put(playerRef.getUuid(), target);
        refresh(event.getPlayer());
    }

    public void refresh(Player player) {
        if (player == null) {
            return;
        }
        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null || !rtsActivePlayers.contains(playerRef.getUuid())) {
            return;
        }

        Ref<EntityStore> selected = selectedByPlayer.get(playerRef.getUuid());
        if (selected == null || !selected.isValid() || !unitRegistry.isClaimed(selected)) {
            clear(playerRef);
            return;
        }

        NpcInfoSnapshot snapshot = infoProvider.snapshot(selected);
        if (snapshot == null) {
            clear(playerRef);
            return;
        }

        var hudManager = player.getHudManager();
        var existing = hudManager.getCustomHud(CivNpcCompactHud.HUD_KEY);
        if (existing instanceof CivNpcCompactHud compactHud) {
            compactHud.refresh(snapshot);
            return;
        }

        hudManager.addCustomHud(playerRef, new CivNpcCompactHud(playerRef, snapshot));
    }

    public void handleDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        selectedByPlayer.remove(playerRef.getUuid());
        rtsActivePlayers.remove(playerRef.getUuid());
    }

    private void clear(PlayerRef playerRef) {
        selectedByPlayer.remove(playerRef.getUuid());
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return;
        }
        Player player = playerEntityRef.getStore().getComponent(playerEntityRef, Player.getComponentType());
        if (player != null) {
            player.getHudManager().removeCustomHud(playerRef, CivNpcCompactHud.HUD_KEY);
        }
    }
}
