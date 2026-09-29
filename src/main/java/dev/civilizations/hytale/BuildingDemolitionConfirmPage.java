package dev.civilizations.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public final class BuildingDemolitionConfirmPage extends InteractiveCustomUIPage<BuildingDemolitionConfirmPage.ActionData> {
    private static final String CONFIRM = "confirm";
    private static final String CANCEL = "cancel";
    private final Runnable confirm;

    public BuildingDemolitionConfirmPage(PlayerRef playerRef, Runnable confirm) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ActionData.CODEC);
        this.confirm = confirm;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append("Pages/CivBuildingDemolitionConfirm.ui");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmButton",
            EventData.of("Action", CONFIRM), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton",
            EventData.of("Action", CANCEL), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ActionData data) {
        if (CONFIRM.equals(data.action)) {
            close();
            confirm.run();
        } else if (CANCEL.equals(data.action)) {
            close();
        }
    }

    public static final class ActionData {
        public static final BuilderCodec<ActionData> CODEC = BuilderCodec.builder(ActionData.class, ActionData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action)
            .add().build();
        private String action;
    }
}
