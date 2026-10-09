package dev.civilizations.hytale;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/** Passive, purple on-screen badge. CustomUIHud has no interactive event binding. */
public final class CivPerformanceHud extends CustomUIHud {
    public static final String KEY = "civ.performanceTracking";
    private int lastRemainingSeconds;
    public CivPerformanceHud(PlayerRef playerRef, int seconds) {
        super(playerRef, KEY, 20);
        this.lastRemainingSeconds = seconds;
    }
    @Override protected void build(@Nonnull UICommandBuilder commands) {
        commands.append("Hud/CivPerformanceTracking.ui");
        commands.set("#TrackingRemaining.Text", remainingText(lastRemainingSeconds));
    }
    public void refresh(int remainingSeconds) {
        if (lastRemainingSeconds == remainingSeconds) return;
        lastRemainingSeconds = remainingSeconds;
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#TrackingRemaining.Text", remainingText(remainingSeconds));
        update(false, commands);
    }
    private static String remainingText(int seconds) {
        return String.format("%02d:%02d verbleibend · /civ",
            Math.max(0, seconds) / 60, Math.max(0, seconds) % 60);
    }
}
