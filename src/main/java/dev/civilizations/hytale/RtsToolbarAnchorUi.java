package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.UpdateAnchorUI;
import com.hypixel.hytale.server.core.modules.anchoraction.AnchorActionModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Interactive RTS toolbar injected into Hytale's always-present reticle anchor.
 */
public final class RtsToolbarAnchorUi {

    private static final String ANCHOR_ID = "ReticleServerEvent";
    private static final String ACTION_OPEN_BUILD_MENU = "civilizationsOpenBuildMenu";

    private RtsToolbarAnchorUi() {
    }

    public static void register(RtsInteractionController controller) {
        AnchorActionModule.get().register(
            ACTION_OPEN_BUILD_MENU,
            (playerRef, ref, store) ->
                controller.openBuildingMenu(playerRef, ref, store)
        );
    }

    public static void send(@Nonnull PlayerRef playerRef) {
        UICommandBuilder commands = new UICommandBuilder();
        commands.append("Hud/CivRtsToolbar.ui");

        UIEventBuilder events = new UIEventBuilder();
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#BuildButton",
            EventData.of("action", ACTION_OPEN_BUILD_MENU),
            false
        );

        playerRef.getPacketHandler().writeNoCache(
            new UpdateAnchorUI(
                ANCHOR_ID,
                true,
                commands.getCommands(),
                events.getEvents()
            )
        );
    }

    public static void clear(@Nonnull PlayerRef playerRef) {
        playerRef.getPacketHandler().writeNoCache(
            new UpdateAnchorUI(ANCHOR_ID, true, null, null)
        );
    }
}
