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

/**
 * Modal RTS building catalog. Entries are kept alphabetically by display name.
 */
public final class BuildingMenuPage
    extends InteractiveCustomUIPage<BuildingMenuPage.ActionData> {

    private static final String ACTION_FARM = "farm";
    private static final String ACTION_FIELD = "field";
    private static final String ACTION_CLOSE = "close";

    private final Runnable selectFarm;
    private final Runnable selectField;

    public BuildingMenuPage(
        PlayerRef playerRef,
        Runnable selectFarm,
        Runnable selectField
    ) {
        super(
            playerRef,
            CustomPageLifetime.CanDismissOrCloseThroughInteraction,
            ActionData.CODEC
        );
        this.selectFarm = selectFarm;
        this.selectField = selectField;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commands,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        commands.append("Pages/CivBuildingMenu.ui");
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#FarmButton",
            EventData.of("Action", ACTION_FARM),
            false
        );
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CloseButton",
            EventData.of("Action", ACTION_CLOSE),
            false
        );
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull ActionData data
    ) {
        if (ACTION_FARM.equals(data.action)) {
            selectFarm.run();
            close();
            return;
        }
        if (ACTION_FIELD.equals(data.action)) {
            selectField.run();
            close();
            return;
        }
        if (ACTION_CLOSE.equals(data.action)) {
            close();
        }
    }

    public static final class ActionData {
        public static final BuilderCodec<ActionData> CODEC =
            BuilderCodec.builder(ActionData.class, ActionData::new)
                .append(
                    new KeyedCodec<>("Action", Codec.STRING),
                    (data, value) -> data.action = value,
                    data -> data.action
                )
                .add()
                .build();

        private String action;
    }
}
