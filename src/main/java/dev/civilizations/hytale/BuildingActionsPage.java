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

public final class BuildingActionsPage extends InteractiveCustomUIPage<BuildingActionsPage.ActionData> {
    private static final String DEMOLISH = "demolish";
    private static final String CONFIRM = "confirm";
    private static final String CANCEL = "cancel";
    private static final String CLOSE = "close";
    private final Runnable demolish;

    public BuildingActionsPage(PlayerRef playerRef, Runnable demolish) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ActionData.CODEC);
        this.demolish = demolish;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append("Pages/CivBuildingActions.ui");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DemolishButton",
            EventData.of("Action", DEMOLISH), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmButton",
            EventData.of("Action", CONFIRM), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton",
            EventData.of("Action", CANCEL), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of("Action", CLOSE), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ActionData data) {
        if (DEMOLISH.equals(data.action)) {
            showConfirmation();
        } else if (CONFIRM.equals(data.action)) {
            demolish.run();
            close();
        } else if (CANCEL.equals(data.action)) {
            hideConfirmation();
        } else if (CLOSE.equals(data.action)) {
            close();
        }
    }

    private void showConfirmation() {
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#ActionsPanel.Visible", false);
        commands.set("#ConfirmationPanel.Visible", true);
        sendUpdate(commands, false);
    }

    private void hideConfirmation() {
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#ConfirmationPanel.Visible", false);
        commands.set("#ActionsPanel.Visible", true);
        sendUpdate(commands, false);
    }

    public static final class ActionData {
        public static final BuilderCodec<ActionData> CODEC = BuilderCodec.builder(ActionData.class, ActionData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action)
            .add().build();
        private String action;
    }
}
