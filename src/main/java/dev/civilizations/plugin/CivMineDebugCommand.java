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
import dev.civilizations.hytale.MinerWorkSystem;

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
        addSubCommand(new RecoverCommand(service));
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
        context.sendMessage(Message.raw("Mine debug: use /civdebug mine info|show|hide|logs|recover"));
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
                    + " | tunnels=" + snapshot.tunnels().size()
                    + " | active=" + snapshot.activeFrontCount()
                    + " | open=" + snapshot.openFrontCount()
            ));

            snapshot.tunnels().stream()
                .sorted(Comparator
                    .comparingInt((CivMineDebugService.TunnelDebugSnapshot debug) -> debug.tunnel().branchDepth())
                    .thenComparing(debug -> debug.tunnel().id()))
                .forEach(debug -> context.sendMessage(Message.raw(
                    "tunnel=" + debug.tunnel().id()
                        + " | kind=" + debug.tunnel().kind()
                        + " | depth=" + debug.tunnel().branchDepth()
                        + " | front=" + (debug.front() == null ? "none" : debug.front().state())
                        + " | geometry=" + (debug.geometry() == null ? "not-generated" : debug.geometry().slices().size() + " slices")
                )));
        }
    }

    private static final class ShowCommand extends AbstractPlayerCommand {
        private final CivMineDebugService service;

        private ShowCommand(CivMineDebugService service) {
            super("show", "Shows player-local mine work-front volumes for the nearest mine.");
            this.service = service;
            addSubCommand(new BoundsCommand(service));
            addSubCommand(new AnchorsCommand(service));
            addSubCommand(new AllCommand(service));
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
            show(context, playerRef, world, false, false);
        }

        private void show(
            CommandContext context,
            PlayerRef playerRef,
            World world,
            boolean includeBounds,
            boolean includeAnchors
        ) {
            CivMineDebugService.MineDebugSnapshot snapshot = snapshot(service, playerRef, world);
            if (snapshot == null) {
                noMine(context);
                return;
            }
            CivMineDebugService.ShowResult result =
                service.show(playerRef, snapshot, includeBounds, includeAnchors);
            context.sendMessage(Message.raw(
                "Mine debug angezeigt | mine=" + snapshot.mine().id()
                    + " | entries=" + result.displayedEntryCount()
                    + " | anchors=" + (result.anchorsIncluded() ? "on" : "off")
                    + " | 500x500=" + (result.boundsIncluded() ? "on" : "off")
            ));
            context.sendMessage(Message.raw(
                "Farben: gelb=Front, orange=OPEN, cyan=MAIN abgeschlossen, blau=BRANCH abgeschlossen, "
                    + "gruen=Navigation, violett=Raum, weiss=Infrastruktur."
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
                new ShowCommand(service).show(context, playerRef, world, true, false);
            }
        }

        private static final class AnchorsCommand extends AbstractPlayerCommand {
            private final CivMineDebugService service;

            private AnchorsCommand(CivMineDebugService service) {
                super("anchors", "Shows mine work-front and navigation/task anchors.");
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
                new ShowCommand(service).show(context, playerRef, world, false, true);
            }
        }

        private static final class AllCommand extends AbstractPlayerCommand {
            private final CivMineDebugService service;

            private AllCommand(CivMineDebugService service) {
                super("all", "Shows mine fronts, runtime anchors and the 500x500 design area.");
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
                new ShowCommand(service).show(context, playerRef, world, true, true);
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

    private static final class RecoverCommand extends AbstractPlayerCommand {
        private final CivMineDebugService service;

        private RecoverCommand(CivMineDebugService service) {
            super("recover", "Recover miner tasks or blocked mine fronts without changing NPC identity.");
            this.service = service;
            addSubCommand(new RecoveryAction(service, "status", false, false));
            addSubCommand(new RecoveryAction(service, "workers", true, false));
            addSubCommand(new RecoveryAction(service, "fronts", false, true));
            addSubCommand(new RecoveryAction(service, "all", true, true));
            requireNoPermission();
        }

        @Override
        protected void execute(
            CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
            PlayerRef playerRef, World world
        ) {
            context.sendMessage(Message.raw(
                "Use /civdebug mine recover status|workers|fronts|all"
            ));
        }
    }

    private static final class RecoveryAction extends AbstractPlayerCommand {
        private final CivMineDebugService service;
        private final boolean workers;
        private final boolean fronts;

        private RecoveryAction(
            CivMineDebugService service, String name, boolean workers, boolean fronts
        ) {
            super(name, "Inspect or recover the nearest mine.");
            this.service = service;
            this.workers = workers;
            this.fronts = fronts;
            requireNoPermission();
        }

        @Override
        protected void execute(
            CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
            PlayerRef playerRef, World world
        ) {
            CivMineDebugService.MineDebugSnapshot mine = snapshot(service, playerRef, world);
            if (mine == null) {
                noMine(context);
                return;
            }
            MinerWorkSystem.RecoveryResult result = workers || fronts
                ? service.recover(world, mine.mine().id(), workers, fronts)
                : service.recoveryStatus(world.getWorldConfig().getUuid(), mine.mine().id());
            context.sendMessage(Message.raw(
                "Mine recovery | mine=" + mine.mine().id()
                    + " | mode=" + (workers && fronts ? "all" : workers ? "workers"
                        : fronts ? "fronts" : "status")
                    + " | workers=" + result.workers()
                    + (workers ? " (queued for next NPC tick)" : "")
                    + " | recoverable/reopened fronts=" + result.fronts()
                    + " | completed fronts preserved=" + result.completedFrontsPreserved()
            ));
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
            String raw = MineLogCommandInput.categoriesArgument(context.getInputString());
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
