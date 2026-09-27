package dev.civilizations.hytale;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * Persistent, non-interactive RTS chrome. Mouse clicks are routed by
 * RtsInteractionController using the same screen-space bounds as this HUD.
 */
public final class RtsToolbarHud extends CustomUIHud {

    public static final String KEY = "Civilizations:RtsToolbar";

    public RtsToolbarHud(PlayerRef playerRef) {
        super(playerRef, KEY, 50);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commands) {
        commands.append("Hud/CivRtsToolbar.ui");
    }
}
