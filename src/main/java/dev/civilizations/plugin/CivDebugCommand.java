package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.InhabitantActivity;
import dev.civilizations.hytale.BuildingPlacementRegistry;
import dev.civilizations.hytale.CivActivityRegistry;
import dev.civilizations.hytale.CivBuildingPersistenceService;
import dev.civilizations.hytale.CivMineDebugService;
import dev.civilizations.hytale.CivMineDecisionDiagnostics;
import dev.civilizations.hytale.CivNameplateStatusSystem;
import dev.civilizations.hytale.CivPathDebugService;
import dev.civilizations.hytale.CivPerformanceRecorder;
import dev.civilizations.hytale.CivPlayerRigDebugService;
import dev.civilizations.hytale.WoodcutterScanDiagnostics;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;

final class CivDebugCommand extends AbstractPlayerCommand {
    private final BuildingPlacementRegistry buildingRegistry;
    private final CivBuildingPersistenceService buildingPersistence;

    CivDebugCommand(
        BuildingPlacementRegistry buildingRegistry,
        CivBuildingPersistenceService buildingPersistence,
        CivPathDebugService pathDebugService,
        WoodcutterScanDiagnostics woodcutterScanDiagnostics,
        CivActivityRegistry activityRegistry,
        CivNameplateStatusSystem nameplateStatusSystem,
        CivPlayerRigDebugService playerRigDebugService,
        CivMineDebugService mineDebugService,
        CivMineDecisionDiagnostics mineDecisionDiagnostics,
        CivPerformanceRecorder performanceRecorder
    ) {
        super("civdebug", "Shows read-only Civilizations development diagnostics.");
        this.buildingRegistry = buildingRegistry;
        this.buildingPersistence = buildingPersistence;
        addSubCommand(new PathCommand(pathDebugService));
        addSubCommand(new PerformanceCommand(performanceRecorder));
        addSubCommand(new WoodScanCommand(woodcutterScanDiagnostics));
        addSubCommand(new ActivityCommand(activityRegistry));
        addSubCommand(new StatusCommand(nameplateStatusSystem));
        addSubCommand(new PlayerRigCommand(playerRigDebugService));
        addSubCommand(new CivMineDebugCommand(mineDebugService, mineDecisionDiagnostics));
        requireNoPermission();
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        List<BuildingPlacementRegistry.BuildingInstance> runtime =
            buildingRegistry.buildings(world.getWorldConfig().getUuid());
        List<BuildingPlacementRegistry.BuildingInstance> persisted = buildingPersistence.load(world);
        context.sendMessage(Message.raw("Civ debug buildings: runtime=" + runtime.size() + ", persisted=" + persisted.size()));
        if (persisted.isEmpty()) {
            context.sendMessage(Message.raw("Keine persistenten Civ-Gebäude gespeichert."));
            return;
        }
        for (BuildingPlacementRegistry.BuildingInstance building : persisted) {
            int snapshotBlocks = building.placement() == null ? 0 : building.placement().replacedFloorBlocks().size();
            String volumes = building.semanticVolumes().stream()
                .map(marker -> marker.tags().getOrDefault("civ.type", "?"))
                .distinct().sorted().reduce((left, right) -> left + "," + right).orElse("-");
            context.sendMessage(Message.raw(
                building.buildingType() + " | id=" + building.id()
                    + " | snapshot=" + snapshotBlocks + " blocks"
                    + " | volumes=" + building.semanticVolumes().size() + " [" + volumes + "]"
            ));
        }
    }

    private static final class PerformanceCommand extends AbstractPlayerCommand {
        private final CivPerformanceRecorder recorder;

        PerformanceCommand(CivPerformanceRecorder recorder) {
            super("perf", "Civ performance recording: start, stop, status, report.");
            this.recorder = recorder;
            addSubCommand(new Action("start", recorder));
            addSubCommand(new Action("stop", recorder));
            addSubCommand(new Action("status", recorder));
            addSubCommand(new Action("report", recorder));
            requireNoPermission();
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store,
                               Ref<EntityStore> ref, PlayerRef playerRef, World world) {
            context.sendMessage(Message.raw("Civ-Profiler: /civdebug perf start | stop | status | report"));
            showStatus(context, recorder);
        }

        private static void showStatus(CommandContext context, CivPerformanceRecorder recorder) {
            Map<String, Object> status = recorder.status();
            boolean active = Boolean.TRUE.equals(status.get("active"));
            context.sendMessage(Message.raw("Civ-Profiler: " + (active ? "Aufzeichnung aktiv" : "nicht aktiv")
                + " | Aufnahmelimit: 15 Minuten"
                + " | letzte Aufnahme vorhanden: " + status.get("hasPreviousReport")));
            if (active) {
                context.sendMessage(Message.raw("Laufzeit: " + status.get("elapsedSeconds")
                    + " s | verbleibend: " + status.get("remainingSeconds") + " s"
                    + " | Samples: " + status.get("sampleCount")));
            }
            context.sendMessage(Message.raw("Gemessen werden Serverkosten, keine Spieler-FPS."));
        }

        private static final class Action extends AbstractPlayerCommand {
            private final String action;
            private final CivPerformanceRecorder recorder;

            Action(String action, CivPerformanceRecorder recorder) {
                super(action, "Civ-Profiler " + action);
                this.action = action;
                this.recorder = recorder;
                requireNoPermission();
            }

            @Override
            protected void execute(CommandContext context, Store<EntityStore> store,
                                   Ref<EntityStore> ref, PlayerRef playerRef, World world) {
                switch (action) {
                    case "start" -> {
                        boolean started = recorder.start();
                        context.sendMessage(Message.raw(started
                            ? "Civ-Performance-Aufzeichnung gestartet (maximal 15 Minuten)."
                            : "Es läuft bereits eine Aufzeichnung."));
                        showStatus(context, recorder);
                    }
                    case "stop" -> {
                        boolean stopped = recorder.stop();
                        context.sendMessage(Message.raw(stopped
                            ? "Civ-Performance-Aufzeichnung beendet. Ergebnis: /civdebug perf report"
                            : "Keine aktive Aufzeichnung vorhanden."));
                    }
                    case "status" -> showStatus(context, recorder);
                    case "report" -> showReport(context, recorder);
                    default -> throw new IllegalStateException("Unbekannte Profiling-Aktion");
                }
            }
        }

        private static void showReport(CommandContext context, CivPerformanceRecorder recorder) {
            Map<String, Object> report = recorder.report();
            if (!Boolean.TRUE.equals(report.get("available"))) {
                context.sendMessage(Message.raw("Noch keine Civ-Performance-Aufnahme vorhanden."));
                return;
            }
            context.sendMessage(Message.raw(String.format(Locale.ROOT,
                "Civ-Performance | %.1f s | %s | %s Samples | %s Ereignisse",
                ((Number) report.get("durationSeconds")).doubleValue(),
                report.get("endReason"), report.get("sampleCount"), report.get("eventCount"))));
            Object systems = report.get("systems");
            if (!(systems instanceof List<?> rows) || rows.isEmpty()) {
                context.sendMessage(Message.raw("Keine instrumentierten Civ-Aufrufe gemessen."));
                return;
            }
            int shown = 0;
            for (Object value : rows) {
                if (!(value instanceof Map<?, ?> metric)) continue;
                if (shown++ >= 5) break;
                context.sendMessage(Message.raw(String.format(Locale.ROOT,
                    "%s: %.2f ms/s | %.1f Aufrufe/s | max %.2f ms",
                    metric.get("system"),
                    ((Number) metric.get("msPerSecond")).doubleValue(),
                    ((Number) metric.get("callsPerSecond")).doubleValue(),
                    ((Number) metric.get("maxMs")).doubleValue())));
            }
            context.sendMessage(Message.raw("Die Kategorien können verschachtelt sein und dürfen nicht addiert werden."));
            context.sendMessage(Message.raw("JSON-Export weiterhin über tools/performance-export.py."));
        }
    }

    private static final class PathCommand extends AbstractPlayerCommand {
        private final CivPathDebugService pathDebugService;

        private PathCommand(CivPathDebugService pathDebugService) {
            super("path", "Toggles Hytale's native path visualization for loaded Civ inhabitants.");
            this.pathDebugService = pathDebugService;
            requireNoPermission();
        }

        @Override
        protected void execute(
            CommandContext context,
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            PlayerRef playerRef,
            World world
        ) {
            CivPathDebugService.ToggleResult result = pathDebugService.toggle(store);
            context.sendMessage(Message.raw(
                "Civ path debug " + (result.enabled() ? "enabled" : "disabled")
                    + " | loaded inhabitants=" + result.matchedNpcCount()
                    + " | flags changed=" + result.changedNpcCount()
            ));
        }
    }

    private static final class WoodScanCommand extends AbstractPlayerCommand {
        private final WoodcutterScanDiagnostics diagnostics;

        private WoodScanCommand(WoodcutterScanDiagnostics diagnostics) {
            super("woodscan", "Toggles real Hytale woodcutter tree-search performance diagnostics.");
            this.diagnostics = diagnostics;
            requireNoPermission();
        }

        @Override
        protected void execute(
            CommandContext context,
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            PlayerRef playerRef,
            World world
        ) {
            WoodcutterScanDiagnostics.ToggleResult result = diagnostics.toggle();
            context.sendMessage(Message.raw(
                "Civ wood scan debug " + (result.enabled() ? "enabled" : "disabled")
                    + " | " + result.snapshot().summary()
            ));
        }
    }

    private static final class ActivityCommand extends AbstractPlayerCommand {
        private final CivActivityRegistry activityRegistry;

        private ActivityCommand(CivActivityRegistry activityRegistry) {
            super("activity", "Shows manual-move and autonomous-work state for Civ inhabitants.");
            this.activityRegistry = activityRegistry;
            requireNoPermission();
        }

        @Override
        protected void execute(
            CommandContext context,
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            PlayerRef playerRef,
            World world
        ) {
            List<CivActivityRegistry.ActivityDebugEntry> entries =
                activityRegistry.debugSnapshots(store);
            context.sendMessage(Message.raw(
                "Civ activity debug | tracked inhabitants=" + entries.size()
            ));
            if (entries.isEmpty()) {
                context.sendMessage(Message.raw(
                    "Noch kein manueller Civ-Bewegungsauftrag in dieser Welt verfolgt."
                ));
                return;
            }

            for (CivActivityRegistry.ActivityDebugEntry entry : entries) {
                InhabitantActivity.ActivitySnapshot snapshot = entry.snapshot();
                String destination = snapshot.manualMovement() == null
                    ? "-"
                    : snapshot.manualMovement().destination().toString();
                context.sendMessage(Message.raw(
                    "entity=" + entry.entityIndex()
                        + " | state=" + snapshot.mode()
                        + " | autonomous=" + snapshot.autonomousWorkAllowed()
                        + " | resume=" + String.format(
                            Locale.ROOT,
                            "%.2fs",
                            snapshot.resumeDelayRemainingSeconds()
                        )
                        + " | destination=" + destination
                ));
            }
        }
    }

    private static final class StatusCommand extends AbstractPlayerCommand {
        private final CivNameplateStatusSystem statusSystem;

        private StatusCommand(CivNameplateStatusSystem statusSystem) {
            super("status", "Shows the current player-facing Civ inhabitant nameplate status.");
            this.statusSystem = statusSystem;
            requireNoPermission();
        }

        @Override
        protected void execute(
            CommandContext context,
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            PlayerRef playerRef,
            World world
        ) {
            List<CivNameplateStatusSystem.StatusDebugEntry> entries =
                statusSystem.debugSnapshots(store);
            context.sendMessage(Message.raw(
                "Civ status debug | loaded inhabitants=" + entries.size()
            ));
            if (entries.isEmpty()) {
                context.sendMessage(Message.raw("Keine geladenen Civ-Bewohner mit Status gefunden."));
                return;
            }

            for (CivNameplateStatusSystem.StatusDebugEntry entry : entries) {
                context.sendMessage(Message.raw(
                    "entity=" + entry.entityIndex()
                        + " | profession=" + entry.profession()
                        + " | status=" + entry.status()
                        + " | nameplate=" + entry.nameplateText()
                ));
            }
        }
    }

    private static final class PlayerRigCommand extends AbstractPlayerCommand {
        private final CivPlayerRigDebugService playerRigDebugService;

        private PlayerRigCommand(CivPlayerRigDebugService playerRigDebugService) {
            super("playerrig", "Toggles the native Player rig on loaded Civ inhabitants for animation testing.");
            this.playerRigDebugService = playerRigDebugService;
            requireNoPermission();
        }

        @Override
        protected void execute(
            CommandContext context,
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            PlayerRef playerRef,
            World world
        ) {
            CivPlayerRigDebugService.ToggleResult result = playerRigDebugService.toggle(store);
            if (result.error() != null) {
                context.sendMessage(Message.raw("Civ Player-rig spike failed: " + result.error()));
                return;
            }

            context.sendMessage(Message.raw(
                "Civ Player-rig spike " + (result.enabled() ? "enabled" : "disabled")
                    + " | loaded inhabitants=" + result.matchedNpcCount()
                    + " | visuals changed=" + result.changedNpcCount()
            ));
            if (result.enabled()) {
                context.sendMessage(Message.raw(
                    "Player animation sets=" + result.playerAnimationSetCount()
                        + " | first sets=" + String.join(", ", result.animationSetPreview())
                ));
                context.sendMessage(Message.raw(
                    "Probe: Player model + random PlayerSkin + Action animation 'Alerted'. "
                        + "Move a Civ inhabitant to verify native locomotion; run the command again to restore the original visuals."
                ));
            }
        }
    }
}
