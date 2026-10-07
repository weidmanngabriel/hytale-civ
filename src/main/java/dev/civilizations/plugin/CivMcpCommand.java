package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.singleplayer.SingleplayerModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.CompletableFuture;

/** No launcher flags required. Only Hytale's native singleplayer owner may enable live access. */
final class CivMcpCommand extends AbstractPlayerCommand {
    CivMcpCommand(CivLiveBridgeService service, HytaleLogger logger) {
        super("civmcp", "Enable or disable local MCP access to this singleplayer world.");
        requireNoPermission(); // Native ownership is checked in each subcommand, including on LAN.
        addSubCommand(new Toggle("on", service, logger, true));
        addSubCommand(new Toggle("off", service, logger, false));
    }

    @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                     PlayerRef player, World world) {
        context.sendMessage(Message.raw("/civmcp on aktiviert lokalen Zugriff; /civmcp off beendet ihn."));
    }

    private static final class Toggle extends AbstractPlayerCommand {
        private final CivLiveBridgeService service;
        private final HytaleLogger logger;
        private final boolean enabled;
        Toggle(String name, CivLiveBridgeService service, HytaleLogger logger, boolean enabled) {
            super(name, "Toggle the local development bridge.");
            this.service = service; this.logger = logger; this.enabled = enabled; requireNoPermission();
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef player, World world) {
            if (!SingleplayerModule.isOwner(player)) {
                context.sendMessage(Message.raw("Nur der Besitzer der lokalen Einzelspieler-Session darf MCP aktivieren."));
                return;
            }
            var owner = player.getUuid();
            CompletableFuture.supplyAsync(() -> {
                try { return enabled ? service.enable(world, owner) : service.disable(); }
                catch (Exception exception) {
                    logger.atWarning().withCause(exception).log("Failed to toggle the local MCP bridge");
                    return "MCP konnte nicht umgeschaltet werden. Serverlog pruefen: " + exception.getClass().getSimpleName();
                }
            }).thenAcceptAsync(message -> context.sendMessage(Message.raw(message)), world);
        }
    }
}
