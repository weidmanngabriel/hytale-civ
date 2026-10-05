package dev.civilizations.hytale;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/** Compact non-interactive information HUD for a selected building or active construction site. */
public final class CivBuildingCompactHud extends CustomUIHud {

    public static final String HUD_KEY = "civ.selectedBuilding";

    private BuildingInfoSnapshot snapshot;

    public CivBuildingCompactHud(PlayerRef playerRef, BuildingInfoSnapshot snapshot) {
        super(playerRef, HUD_KEY, 10);
        this.snapshot = snapshot;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commands) {
        commands.append("Hud/CivBuildingCompact.ui");
        populate(commands, snapshot);
    }

    public void refresh(BuildingInfoSnapshot next) {
        if (next == null || next.equals(snapshot)) return;
        snapshot = next;
        UICommandBuilder commands = new UICommandBuilder();
        populate(commands, next);
        update(false, commands);
    }

    private static void populate(UICommandBuilder commands, BuildingInfoSnapshot value) {
        commands.set("#BuildingName.Text", value.name());
        commands.set("#Phase.Text", value.phase());
        commands.set("#Status.Text", value.status());
        commands.set("#Progress.Text", value.progress());
        commands.set("#Workers.Text", value.workers());
    }
}
