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
import java.util.List;

public final class BuildingActionsPage extends InteractiveCustomUIPage<BuildingActionsPage.ActionData> {
    private static final String UPGRADE = "upgrade";
    private static final String DEMOLISH = "demolish";
    private static final String CONFIRM = "confirm";
    private static final String CANCEL = "cancel";
    private static final String CLOSE = "close";
    private static final String WORKER_PREFIX = "worker:";
    private static final int STATIC_WORKER_SLOTS = 3;

    private final String buildingName;
    private final int phase;
    private final int workerCapacity;
    private final List<WorkerOption> workers;
    private final int nextPhase;
    private final boolean upgrading;
    private final Runnable upgrade;
    private final Runnable demolish;

    public BuildingActionsPage(
        PlayerRef playerRef,
        String buildingName,
        int phase,
        int workerCapacity,
        List<WorkerOption> workers,
        int nextPhase,
        boolean upgrading,
        Runnable upgrade,
        Runnable demolish
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ActionData.CODEC);
        this.buildingName = buildingName == null || buildingName.isBlank() ? "Gebäude" : buildingName;
        this.phase = Math.max(1, phase);
        this.workerCapacity = Math.max(0, workerCapacity);
        this.workers = List.copyOf(workers == null ? List.of() : workers);
        this.nextPhase = Math.max(0, nextPhase);
        this.upgrading = upgrading;
        this.upgrade = upgrade;
        this.demolish = demolish;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append("Pages/CivBuildingActions.ui");
        commands.set("#BuildingName.Text", buildingName);
        commands.set("#BuildingMeta.Text", "Phase " + phase);
        commands.set("#WorkerSummary.Text", "Arbeiter " + workers.size() + "/" + workerCapacity);

        int visibleSlots = Math.min(STATIC_WORKER_SLOTS, Math.max(workerCapacity, workers.size()));
        commands.set("#NoWorkerSlots.Visible", visibleSlots == 0);

        for (int index = 0; index < STATIC_WORKER_SLOTS; index++) {
            boolean slotVisible = index < visibleSlots;
            boolean hasWorker = index < workers.size();

            commands.set("#WorkerSlot" + index + ".Visible", slotVisible);
            if (!slotVisible) {
                continue;
            }

            String labelSelector = "#WorkerLabel" + index;
            String buttonSelector = "#WorkerButton" + index;
            if (hasWorker) {
                WorkerOption worker = workers.get(index);
                commands.set(labelSelector + ".Text", worker.label());
                commands.set(buttonSelector + ".Visible", true);
                events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    buttonSelector,
                    EventData.of("Action", WORKER_PREFIX + index),
                    false
                );
            } else {
                commands.set(labelSelector + ".Text", "+ Freier Arbeitsplatz");
                commands.set(buttonSelector + ".Visible", false);
            }
        }

        commands.set("#UpgradeRequirements.Visible", upgrading || nextPhase > 0);
        commands.set("#UpgradeStatus.Visible", upgrading);
        commands.set("#UpgradeButton.Visible", !upgrading && nextPhase > 0);
        if (nextPhase > 0) {
            commands.set("#UpgradeButton.Text", "Auf Phase " + nextPhase + " erweitern");
        }
        if (!upgrading && nextPhase > 0 && upgrade != null) {
            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#UpgradeButton",
                EventData.of("Action", UPGRADE),
                false
            );
        }

        commands.set("#DemolishButton.Visible", !upgrading);
        if (!upgrading) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#DemolishButton",
                EventData.of("Action", DEMOLISH), false);
        }
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
        if (data.action != null && data.action.startsWith(WORKER_PREFIX)) {
            selectWorker(data.action);
        } else if (UPGRADE.equals(data.action) && !upgrading && nextPhase > 0 && upgrade != null) {
            upgrade.run();
            close();
        } else if (DEMOLISH.equals(data.action) && !upgrading) {
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

    private void selectWorker(String action) {
        try {
            int index = Integer.parseInt(action.substring(WORKER_PREFIX.length()));
            if (index >= 0 && index < workers.size()) {
                workers.get(index).select().run();
                close();
            }
        } catch (NumberFormatException ignored) {
            // Ignore malformed UI events instead of changing selection.
        }
    }

    private void showConfirmation() {
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#ActionsPanel.Visible", false);
        commands.set("#ConfirmationText.Text", buildingName + " wirklich abreißen?");
        commands.set("#ConfirmationPanel.Visible", true);
        sendUpdate(commands, false);
    }

    private void hideConfirmation() {
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#ConfirmationPanel.Visible", false);
        commands.set("#ActionsPanel.Visible", true);
        sendUpdate(commands, false);
    }

    public record WorkerOption(String label, Runnable select) {
        public WorkerOption {
            label = label == null || label.isBlank() ? "Bewohner" : label;
            if (select == null) {
                throw new IllegalArgumentException("Worker selection callback is required.");
            }
        }
    }

    public static final class ActionData {
        public static final BuilderCodec<ActionData> CODEC = BuilderCodec.builder(ActionData.class, ActionData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action)
            .add().build();
        private String action;
    }
}
