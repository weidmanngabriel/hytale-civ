package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
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
        if (playerRef == null) return;
        if (active) {
            rtsActivePlayers.add(playerRef.getUuid());
            return;
        }
        rtsActivePlayers.remove(playerRef.getUuid());
        clear(playerRef);
    }

    public void select(PlayerRef playerRef, Ref<EntityStore> target) {
        if (playerRef == null || target == null || !target.isValid()
            || !unitRegistry.isClaimed(target)) {
            clear(playerRef);
            return;
        }
        rtsActivePlayers.add(playerRef.getUuid());
        selectedByPlayer.put(playerRef.getUuid(), target);
        refresh(playerRef);
    }

    public boolean isSelected(PlayerRef playerRef, Ref<EntityStore> target) {
        if (playerRef == null || target == null || !target.isValid()) return false;
        Ref<EntityStore> selected = selectedByPlayer.get(playerRef.getUuid());
        return selected != null && selected.isValid()
            && unitRegistry.keyOf(selected).equals(unitRegistry.keyOf(target));
    }

    public void refresh(Player player) {
        if (player != null) refresh(player.getPlayerRef());
    }

    public void refresh(PlayerRef playerRef) {
        if (playerRef == null || !rtsActivePlayers.contains(playerRef.getUuid())) return;
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
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) return;
        Player player = playerEntityRef.getStore().getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) return;
        var hudManager = player.getHudManager();
        var existing = hudManager.getCustomHud(CivNpcCompactHud.HUD_KEY);
        if (existing instanceof CivNpcCompactHud compactHud) {
            compactHud.refresh(snapshot);
        } else {
            hudManager.addCustomHud(playerRef, new CivNpcCompactHud(playerRef, snapshot));
        }
    }

    public void handleDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        selectedByPlayer.remove(playerRef.getUuid());
        rtsActivePlayers.remove(playerRef.getUuid());
    }

    public void clear(PlayerRef playerRef) {
        if (playerRef == null) return;
        selectedByPlayer.remove(playerRef.getUuid());
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) return;
        Player player = playerEntityRef.getStore().getComponent(playerEntityRef, Player.getComponentType());
        if (player != null) {
            player.getHudManager().removeCustomHud(playerRef, CivNpcCompactHud.HUD_KEY);
        }
    }
}
