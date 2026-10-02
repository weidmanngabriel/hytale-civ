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

/** Small first-person action page opened by Hytale's standard Use action (F). */
public final class PersonActionsPage extends InteractiveCustomUIPage<PersonActionsPage.ActionData> {

    private static final String ACTION_WOODCUTTER = "woodcutter";
    private static final String ACTION_MINER = "miner";
    private static final String ACTION_CONSTRUCTION_WORKER = "construction_worker";
    private static final String ACTION_FARMER = "farmer";
    private static final String ACTION_INVENTORY = "inventory";

    private final Runnable assignWoodcutter;
    private final Runnable assignMiner;
    private final Runnable assignConstructionWorker;
    private final Runnable assignFarmer;
    private final Runnable openInventory;

    public PersonActionsPage(
        PlayerRef playerRef,
        Runnable assignWoodcutter,
        Runnable assignMiner,
        Runnable assignConstructionWorker,
        Runnable assignFarmer,
        Runnable openInventory
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ActionData.CODEC);
        this.assignWoodcutter = assignWoodcutter;
        this.assignMiner = assignMiner;
        this.assignConstructionWorker = assignConstructionWorker;
        this.assignFarmer = assignFarmer;
        this.openInventory = openInventory;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commands,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        commands.append("Pages/CivPersonActions.ui");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WoodcutterButton", EventData.of("Action", ACTION_WOODCUTTER), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MinerButton", EventData.of("Action", ACTION_MINER), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FarmerButton", EventData.of("Action", ACTION_FARMER), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#InventoryButton", EventData.of("Action", ACTION_INVENTORY), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConstructionWorkerButton", EventData.of("Action", ACTION_CONSTRUCTION_WORKER), false);
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull ActionData data
    ) {
        if (ACTION_INVENTORY.equals(data.action)) {
            close();
            openInventory.run();
        } else if (ACTION_WOODCUTTER.equals(data.action)) {
            assignWoodcutter.run();
            close();
        } else if (ACTION_MINER.equals(data.action)) {
            assignMiner.run();
            close();
        } else if (ACTION_CONSTRUCTION_WORKER.equals(data.action)) {
            assignConstructionWorker.run();
            close();
        } else if (ACTION_FARMER.equals(data.action)) {
            assignFarmer.run();
            close();
        }
    }

    public static final class ActionData {
        public static final BuilderCodec<ActionData> CODEC =
            BuilderCodec.builder(ActionData.class, ActionData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING),
                    (data, value) -> data.action = value, data -> data.action)
                .add()
                .build();
        private String action;
    }
}
