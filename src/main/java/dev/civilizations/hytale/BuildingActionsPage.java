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
    private static final String DEMOLISH = "demolish";
    private static final String CONFIRM = "confirm";
    private static final String CANCEL = "cancel";
    private static final String CLOSE = "close";
    private static final String WORKER_PREFIX = "worker:";

    private final String buildingName;
    private final int phase;
    private final int workerCapacity;
    private final List<WorkerOption> workers;
    private final Runnable demolish;

    public BuildingActionsPage(
        PlayerRef playerRef,
        String buildingName,
        int phase,
        int workerCapacity,
        List<WorkerOption> workers,
        Runnable demolish
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ActionData.CODEC);
        this.buildingName = buildingName == null || buildingName.isBlank() ? "Gebäude" : buildingName;
        this.phase = Math.max(1, phase);
        this.workerCapacity = Math.max(0, workerCapacity);
        this.workers = List.copyOf(workers == null ? List.of() : workers);
        this.demolish = demolish;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append("Pages/CivBuildingActions.ui");
        commands.set("#BuildingName.Text", buildingName);
        commands.set("#BuildingMeta.Text", "Phase " + phase);
        commands.set("#WorkerSummary.Text", "Arbeiter " + workers.size() + "/" + workerCapacity);

        int visibleSlots = Math.max(workerCapacity, workers.size());
        if (visibleSlots == 0) {
            commands.appendInline("#WorkerList",
                "Label { Text: \"Keine Arbeitsplätze\"; Style: (FontSize: 16, TextColor: #a9a9a9); Anchor: (Bottom: 10); }");
        }

        for (int index = 0; index < visibleSlots; index++) {
            if (index < workers.size()) {
                WorkerOption worker = workers.get(index);
                String selector = "#Worker" + index;
                commands.appendInline("#WorkerList",
                    "$C.@TextButton " + selector + " { @Text = \"" + escapeUi(worker.label())
                        + "\"; Anchor: (Width: 400, Height: 42, Bottom: 8); }");
                events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector,
                    EventData.of("Action", WORKER_PREFIX + index),
                    false
                );
            } else {
                commands.appendInline("#WorkerList",
                    "Label { Text: \"+ Freier Arbeitsplatz\"; Style: (FontSize: 16, TextColor: #a9a9a9); Anchor: (Height: 34, Bottom: 6); }");
            }
        }

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
        if (data.action != null && data.action.startsWith(WORKER_PREFIX)) {
            selectWorker(data.action);
        } else if (DEMOLISH.equals(data.action)) {
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

    private static String escapeUi(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
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
