package dev.civilizations.hytale;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/** Compact, non-interactive inhabitant information HUD shown for the inspected Civ NPC. */
public final class CivNpcCompactHud extends CustomUIHud {

    public static final String HUD_KEY = "civ.selectedNpc";

    private NpcInfoSnapshot snapshot;

    public CivNpcCompactHud(PlayerRef playerRef, NpcInfoSnapshot snapshot) {
        super(playerRef, HUD_KEY, 10);
        this.snapshot = snapshot;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commands) {
        commands.append("Hud/CivNpcCompact.ui");
        populate(commands, snapshot);
    }

    public void refresh(NpcInfoSnapshot next) {
        if (next == null || next.equals(snapshot)) {
            return;
        }
        snapshot = next;
        UICommandBuilder commands = new UICommandBuilder();
        populate(commands, next);
        update(false, commands);
    }

    private static void populate(UICommandBuilder commands, NpcInfoSnapshot value) {
        commands.set("#NpcName.Text", value.identity().name());
        commands.set("#Profession.Text", value.work().profession());
        commands.set("#Activity.Text", value.work().activity());
        commands.set("#Experience.Text", value.work().professionXp() + " XP");
        commands.set("#Hunger.Text", value.needs().hunger());
        commands.set("#Home.Text", value.family().home());
    }
}
