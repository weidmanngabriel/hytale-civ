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
import dev.civilizations.hytale.CivPlayerRigDebugService;
import dev.civilizations.hytale.WoodcutterScanDiagnostics;

import java.util.List;
import java.util.Locale;

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
        CivMineDecisionDiagnostics mineDecisionDiagnostics
    ) {
        super("civdebug", "Shows read-only Civilizations development diagnostics.");
        this.buildingRegistry = buildingRegistry;
        this.buildingPersistence = buildingPersistence;
        addSubCommand(new PathCommand(pathDebugService));
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
