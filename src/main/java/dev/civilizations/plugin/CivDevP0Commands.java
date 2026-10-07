package dev.civilizations.plugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class CivDevEventsCommand extends AbstractAsyncCommand {
    private final RequiredArg<UUID> uuid;
    private final FlagArg json;
    private final CivDevEventHistory history;
    private final ObjectMapper mapper = new ObjectMapper();

    CivDevEventsCommand(CivDevEventHistory history) {
        super("events", "Shows bounded recent state transitions for one Civ NPC.");
        this.history = history;
        uuid = withRequiredArg("uuid", "Entity UUID.", ArgTypes.UUID);
        json = withFlagArg("json", "Return machine-readable JSON.");
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        UUID entityId = context.get(uuid);
        var events = history.snapshot(entityId);
        if (Boolean.TRUE.equals(context.get(json))) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("uuid", entityId.toString());
                payload.put("events", events);
                context.sendMessage(Message.raw(mapper.writeValueAsString(payload)));
            } catch (JsonProcessingException exception) {
                context.sendMessage(Message.raw("CIVDEV_ERROR could not serialize event history"));
            }
            return CompletableFuture.completedFuture(null);
        }

        context.sendMessage(Message.raw(
            "CIVDEV_EVENTS uuid=" + entityId + " count=" + events.size()
        ));
        for (CivDevEventHistory.Event event : events) {
            context.sendMessage(Message.raw(
                event.sequence() + " | " + event.timestampMillis() + " | "
                    + event.type() + " | " + event.details()
            ));
        }
        return CompletableFuture.completedFuture(null);
    }
}

final class CivDevScenarioCommand extends AbstractAsyncCommand {
    private final RequiredArg<String> scenario;
    private final RequiredArg<Double> x;
    private final RequiredArg<Double> y;
    private final RequiredArg<Double> z;
    private final CivDevScenarioService scenarios;

    CivDevScenarioCommand(CivDevScenarioService scenarios) {
        super("scenario", "Creates a reproducible civdev scenario at an explicit world position.");
        this.scenarios = scenarios;
        scenario = withRequiredArg("scenario", "Scenario name; currently soldier.", ArgTypes.STRING);
        x = withRequiredArg("x", "Scenario center X.", ArgTypes.DOUBLE);
        y = withRequiredArg("y", "Scenario center Y.", ArgTypes.DOUBLE);
        z = withRequiredArg("z", "Scenario center Z.", ArgTypes.DOUBLE);
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        World world = defaultWorld(context);
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                var result = scenarios.setup(
                    world,
                    context.get(scenario),
                    context.get(x),
                    context.get(y),
                    context.get(z)
                );
                context.sendMessage(Message.raw(
                    "CIVDEV_SCENARIO scenario=" + result.scenario()
                        + " entities=" + result.entities()
                ));
            } catch (RuntimeException exception) {
                context.sendMessage(Message.raw(
                    "CIVDEV_ERROR scenario failed: "
                        + (exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage())
                ));
            }
        }, world);
    }

    private static World defaultWorld(CommandContext context) {
        Universe universe = Universe.get();
        World world = universe == null ? null : universe.getDefaultWorld();
        if (world == null || !world.isAlive()) {
            context.sendMessage(Message.raw("CIVDEV_ERROR no active default world"));
            return null;
        }
        return world;
    }
}

final class CivDevResetCommand extends AbstractAsyncCommand {
    private final CivDevScenarioService scenarios;

    CivDevResetCommand(CivDevScenarioService scenarios) {
        super("reset", "Removes only entities spawned through civdev in the current session.");
        this.scenarios = scenarios;
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        Universe universe = Universe.get();
        World world = universe == null ? null : universe.getDefaultWorld();
        if (world == null || !world.isAlive()) {
            context.sendMessage(Message.raw("CIVDEV_ERROR no active default world"));
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            int removed = scenarios.reset(world);
            context.sendMessage(Message.raw("CIVDEV_RESET removed=" + removed));
        }, world);
    }
}
