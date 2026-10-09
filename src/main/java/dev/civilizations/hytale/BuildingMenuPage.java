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
 * Mine phases are intentionally exposed here as debug entries until progression owns them.
 */
public final class BuildingMenuPage
    extends InteractiveCustomUIPage<BuildingMenuPage.ActionData> {

    private static final String ACTION_FARM = "farm";
    private static final String ACTION_MINE_1 = "mine1";
    private static final String ACTION_MINE_2 = "mine2";
    private static final String ACTION_MINE_3 = "mine3";
    private static final String ACTION_FIELD = "field";
    private static final String ACTION_DWARF = "dwarf_mine";
    private static final String ACTION_CLOSE = "close";

    private final Runnable selectFarm;
    private final Runnable selectMine1;
    private final Runnable selectMine2;
    private final Runnable selectMine3;
    private final Runnable selectField;
    private final Runnable selectDwarfMine;

    public BuildingMenuPage(
        PlayerRef playerRef,
        Runnable selectFarm,
        Runnable selectMine1,
        Runnable selectMine2,
        Runnable selectMine3,
        Runnable selectField,
        Runnable selectDwarfMine
    ) {
        super(
            playerRef,
            CustomPageLifetime.CanDismissOrCloseThroughInteraction,
            ActionData.CODEC
        );
        this.selectFarm = selectFarm;
        this.selectMine1 = selectMine1;
        this.selectMine2 = selectMine2;
        this.selectMine3 = selectMine3;
        this.selectField = selectField;
        this.selectDwarfMine = selectDwarfMine;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commands,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        commands.append("Pages/CivBuildingMenu.ui");
        bind(events, "#FarmButton", ACTION_FARM);
        bind(events, "#Mine1Button", ACTION_MINE_1);
        bind(events, "#Mine2Button", ACTION_MINE_2);
        bind(events, "#Mine3Button", ACTION_MINE_3);
        bind(events, "#FieldButton", ACTION_FIELD);
        bind(events, "#DwarfMineButton", ACTION_DWARF);
        bind(events, "#CloseButton", ACTION_CLOSE);
    }

    private static void bind(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            selector,
            EventData.of("Action", action),
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
        if (ACTION_MINE_1.equals(data.action)) {
            selectMine1.run();
            close();
            return;
        }
        if (ACTION_MINE_2.equals(data.action)) {
            selectMine2.run();
            close();
            return;
        }
        if (ACTION_MINE_3.equals(data.action)) {
            selectMine3.run();
            close();
            return;
        }
        if (ACTION_FIELD.equals(data.action)) {
            selectField.run();
            close();
            return;
        }
        if (ACTION_DWARF.equals(data.action)) {
            selectDwarfMine.run();
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
