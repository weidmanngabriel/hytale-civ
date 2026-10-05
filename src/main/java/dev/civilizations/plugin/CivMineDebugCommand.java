package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.MineDecisionCategory;
import dev.civilizations.hytale.CivMineDebugService;
import dev.civilizations.hytale.CivMineDecisionDiagnostics;

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;

final class CivMineDebugCommand extends AbstractPlayerCommand {

    private final CivMineDebugService service;

    CivMineDebugCommand(
        CivMineDebugService service,
        CivMineDecisionDiagnostics decisionDiagnostics
    ) {
        super("mine", "Inspect or visualize the nearest Civ mine.");
        this.service = service;
        addSubCommand(new InfoCommand(service));
        addSubCommand(new ShowCommand(service));
        addSubCommand(new HideCommand(service));
        addSubCommand(new LogsCommand(decisionDiagnostics));
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
        context.sendMessage(Message.raw("Mine debug: use /civdebug mine info|show|hide|logs"));
    }

    private static CivMineDebugService.MineDebugSnapshot snapshot(
        CivMineDebugService service,
        PlayerRef playerRef,
        World world
    ) {
        if (playerRef == null || playerRef.getTransform() == null) return null;
        return service.snapshot(
            world.getWorldConfig().getUuid(),
            playerRef.getTransform().getPosition()
        );
    }

    private static void noMine(CommandContext context) {
        context.sendMessage(Message.raw("Keine Civ-Mine im Umkreis von 128 Blöcken gefunden."));
    }

    static String mineLogCategoriesArgument(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        int onIndex = lower.lastIndexOf(" on");
        if (onIndex >= 0) {
            String trailing = trimmed.substring(onIndex + 3).trim();
            return trailing.isEmpty() ? null : trailing;
        }
        if (!trimmed.contains(" ")) return trimmed;
        return null;
    }

    private static final class InfoCommand extends AbstractPlayerCommand {
        private final CivMineDebugService service;

        private InfoCommand(CivMineDebugService service) {
            super("info", "Shows read-only state for the nearest Civ mine.");
            this.service = service;
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
            CivMineDebugService.MineDebugSnapshot snapshot = snapshot(service, playerRef, world);
            if (snapshot == null) {
                noMine(context);
                return;
            }

            context.sendMessage(Message.raw(
                "Mine " + snapshot.mine().id()
                    + " | phase=" + snapshot.mine().phase()
                    + " | distance=" + String.format(Locale.ROOT, "%.1f", snapshot.distanceBlocks())
                    + " | segments=" + snapshot.segments().size()
                    + " | active=" + snapshot.activeFrontCount()
                    + " | open=" + snapshot.openFrontCount()
            ));

            snapshot.segments().stream()
                .sorted(Comparator
                    .comparingInt(CivMineDebugService.SegmentDebugSnapshot::branchLevel)
                    .thenComparing(debug -> debug.segment().id()))
                .forEach(debug -> context.sendMessage(Message.raw(
                    "segment=" + debug.segment().id()
                        + " | topology=" + (debug.root() ? "ROOT" : "LEGACY_CHILD")
                        + " | level=" + debug.branchLevel()
                        + " | heading=" + debug.segment().direction()
                        + " | status=" + debug.segment().status()
                        + " | progress=" + debug.segment().nextBlockIndex()
                            + "/" + debug.segment().blockCount()
                )));

            context.sendMessage(Message.raw(
                "Nicht vorhanden im aktuellen Mine-Core: MAIN-vs-BRANCH-Klassifikation nach Root, "
                    + "Navigation-Anker, Räume/Knoten, Brückenmarker, Formphase und Next-Segment-Preview."
            ));
        }
    }

    private static final class ShowCommand extends AbstractPlayerCommand {
        private final CivMineDebugService service;

        private ShowCommand(CivMineDebugService service) {
            super("show", "Shows player-local mine debug volumes for the nearest mine.");
            this.service = service;
            addSubCommand(new BoundsCommand(service));
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
            show(context, playerRef, world, false);
        }

        private void show(
            CommandContext context,
            PlayerRef playerRef,
            World world,
            boolean includeBounds
        ) {
            CivMineDebugService.MineDebugSnapshot snapshot = snapshot(service, playerRef, world);
            if (snapshot == null) {
                noMine(context);
                return;
            }
            CivMineDebugService.ShowResult result = service.show(playerRef, snapshot, includeBounds);
            context.sendMessage(Message.raw(
                "Mine debug angezeigt | mine=" + snapshot.mine().id()
                    + " | entries=" + result.displayedEntryCount()
                    + " | 500x500=" + (result.boundsIncluded() ? "on" : "off")
            ));
            context.sendMessage(Message.raw(
                "Farben: gelb=MINING, orange=RESERVED, cyan=Root, blau=abgeschlossen/sonstige Segmente."
            ));
        }

        private static final class BoundsCommand extends AbstractPlayerCommand {
            private final CivMineDebugService service;

            private BoundsCommand(CivMineDebugService service) {
                super("bounds", "Shows mine debug volumes including the 500x500 design area.");
                this.service = service;
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
                new ShowCommand(service).show(context, playerRef, world, true);
            }
        }
    }

    private static final class HideCommand extends AbstractPlayerCommand {
        private final CivMineDebugService service;

        private HideCommand(CivMineDebugService service) {
            super("hide", "Removes Civ mine debug volumes for this player.");
            this.service = service;
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
            int removed = service.hide(playerRef);
            context.sendMessage(Message.raw("Mine debug ausgeblendet | removed=" + removed));
        }
    }

    private static final class LogsCommand extends AbstractPlayerCommand {
        private final CivMineDecisionDiagnostics diagnostics;

        private LogsCommand(CivMineDecisionDiagnostics diagnostics) {
            super("logs", "Controls structured mine decision logging.");
            this.diagnostics = diagnostics;
            addSubCommand(new LogsOnCommand(diagnostics));
            addSubCommand(new LogsOffCommand(diagnostics));
            addSubCommand(new LogsStatusCommand(diagnostics));
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
            context.sendMessage(Message.raw(
                "Mine decision logs: " + diagnostics.statusSummary()
                    + " | use /civdebug mine logs on [categories]|off|status"
            ));
        }
    }

    private static final class LogsOnCommand extends AbstractPlayerCommand {
        private final CivMineDecisionDiagnostics diagnostics;

        private LogsOnCommand(CivMineDecisionDiagnostics diagnostics) {
            super("on", "Enables mine decision logs, optionally for comma-separated categories.");
            this.diagnostics = diagnostics;
            setAllowsExtraArguments(true);
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
            String raw = mineLogCategoriesArgument(context.getInputString());
            try {
                Set<MineDecisionCategory> enabled = diagnostics.enable(
                    CivMineDecisionDiagnostics.parseCategories(raw)
                );
                context.sendMessage(Message.raw(
                    "Mine decision logs enabled | categories=" + enabled.stream()
                        .map(Enum::name)
                        .sorted()
                        .reduce((left, right) -> left + "," + right)
                        .orElse("-")
                ));
            } catch (IllegalArgumentException exception) {
                context.sendMessage(Message.raw(exception.getMessage()));
            }
        }
    }

    private static final class LogsOffCommand extends AbstractPlayerCommand {
        private final CivMineDecisionDiagnostics diagnostics;

        private LogsOffCommand(CivMineDecisionDiagnostics diagnostics) {
            super("off", "Disables mine decision logs.");
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
            diagnostics.disable();
            context.sendMessage(Message.raw("Mine decision logs disabled."));
        }
    }

    private static final class LogsStatusCommand extends AbstractPlayerCommand {
        private final CivMineDecisionDiagnostics diagnostics;

        private LogsStatusCommand(CivMineDecisionDiagnostics diagnostics) {
            super("status", "Shows the active mine decision log filters.");
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
            context.sendMessage(Message.raw(
                "Mine decision logs " + diagnostics.statusSummary()
                    + " | available=" + CivMineDecisionDiagnostics.availableCategories()
            ));
        }
    }
}
