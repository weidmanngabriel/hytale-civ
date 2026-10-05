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

/** Large read-only detail page for a construction site selected through the RTS interaction flow. */
public final class ConstructionDetailsPage
    extends InteractiveCustomUIPage<ConstructionDetailsPage.ActionData> {

    private static final String CLOSE = "close";
    private final BuildingInfoSnapshot snapshot;

    public ConstructionDetailsPage(PlayerRef playerRef, BuildingInfoSnapshot snapshot) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ActionData.CODEC);
        this.snapshot = snapshot;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commands,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        commands.append("Pages/CivConstructionDetails.ui");
        commands.set("#BuildingName.Text", snapshot.name());
        commands.set("#Phase.Text", snapshot.phase());
        commands.set("#Status.Text", snapshot.status());
        commands.set("#Progress.Text", snapshot.progress());
        commands.set("#Workers.Text", snapshot.workers());
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CloseButton",
            EventData.of("Action", CLOSE),
            false
        );
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull ActionData data
    ) {
        if (CLOSE.equals(data.action)) close();
    }

    public static final class ActionData {
        public static final BuilderCodec<ActionData> CODEC = BuilderCodec.builder(
            ActionData.class,
            ActionData::new
        ).append(
            new KeyedCodec<>("Action", Codec.STRING),
            (data, value) -> data.action = value,
            data -> data.action
        ).add().build();
        private String action;
    }
}
