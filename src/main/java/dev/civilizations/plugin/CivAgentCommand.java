package dev.civilizations.plugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.math.util.ChunkUtil;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.hytale.BuildingPlacementRegistry;
import dev.civilizations.hytale.CivConstructionPersistenceService;
import dev.civilizations.hytale.CivMineDebugService;
import dev.civilizations.hytale.CivPerformanceRecorder;
import dev.civilizations.hytale.ConstructionSiteRegistry;
import dev.civilizations.hytale.PrefabPlacementService;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Console-capable agent facade over existing native Hytale and Civ services.
 * The normal player UI continues to use the same underlying services.
 * Every operation is dispatched on the world executor, not the HTTP thread.
 */
@SuppressWarnings("deprecation")
final class CivAgentCommand extends AbstractCommandCollection {
    private static final ObjectMapper JSON = new ObjectMapper();

    CivAgentCommand(
        BuildingPlacementRegistry buildings,
        PrefabPlacementService placement,
        ConstructionSiteRegistry sites,
        CivConstructionPersistenceService persistence,
        CivMineDebugService mineDebug,
        CivPerformanceRecorder profiler
    ) {
        super("civagent", "Machine-readable Hytale/Civ agent functions.");
        addSubCommand(new Capabilities());
        addSubCommand(new Players());
        addSubCommand(new Buildings(buildings));
        addSubCommand(new Sites(sites));
        addSubCommand(new BlockInspect());
        addSubCommand(new BlockSet(buildings));
        addSubCommand(new CreateSite(buildings, placement, sites, persistence));
        addSubCommand(new MineRecover(mineDebug));
        addSubCommand(new PerformanceStart(profiler));
        addSubCommand(new PerformanceStop(profiler));
        addSubCommand(new PerformanceStatus(profiler));
        addSubCommand(new PerformanceReport(profiler));
        addSubCommand(new PerformanceSamples(profiler));
        addSubCommand(new PerformanceEvents(profiler));
        addSubCommand(new PerformanceMark(profiler));
    }

    private static void result(CommandContext context, String action, Object data) {
        try {
            context.sendMessage(Message.raw("CIVAGENT_RESULT " +
                JSON.writeValueAsString(Map.of("action", action, "data", data))));
        } catch (JsonProcessingException ex) {
            error(context, "Could not serialize agent result");
        }
    }

    private static void error(CommandContext context, String reason) {
        context.sendMessage(Message.raw("CIVAGENT_ERROR " + reason));
    }

    private abstract static class WorldAction extends AbstractAsyncCommand {
        WorldAction(String name, String description) { super(name, description); }

        @Override
        protected final CompletableFuture<Void> executeAsync(CommandContext context) {
            Universe universe = Universe.get();
            World world = universe == null ? null : universe.getDefaultWorld();
            if (world == null || !world.isAlive()) {
                error(context, "No running default world");
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.runAsync(() -> {
                try {
                    run(context, world);
                } catch (RuntimeException ex) {
                    error(context, ex.getMessage() == null
                        ? ex.getClass().getSimpleName() : ex.getMessage());
                }
            }, world);
        }

        protected abstract void run(CommandContext context, World world);
    }


    /** Development-only transport over the existing allowlisted command bridge. */
    private abstract static class PerformanceAction extends WorldAction {
        final CivPerformanceRecorder profiler;
        PerformanceAction(String name, String description, CivPerformanceRecorder profiler) {
            super(name, description);
            this.profiler = profiler;
        }
    }

    private static final class PerformanceStart extends PerformanceAction {
        PerformanceStart(CivPerformanceRecorder profiler) {
            super("perf-start", "Start a capped 15 minute profiling session.", profiler);
        }
        @Override protected void run(CommandContext context, World world) {
            boolean started = profiler.start();
            result(context, "perf-start", Map.of("started", started, "status", profiler.status()));
        }
    }

    private static final class PerformanceStop extends PerformanceAction {
        PerformanceStop(CivPerformanceRecorder profiler) {
            super("perf-stop", "Stop profiling and retain the last report.", profiler);
        }
        @Override protected void run(CommandContext context, World world) {
            boolean stopped = profiler.stop();
            result(context, "perf-stop", Map.of("stopped", stopped, "status", profiler.status()));
        }
    }

    private static final class PerformanceStatus extends PerformanceAction {
        PerformanceStatus(CivPerformanceRecorder profiler) {
            super("perf-status", "Check a profiling session.", profiler);
        }
        @Override protected void run(CommandContext context, World world) {
            result(context, "perf-status", profiler.status());
        }
    }

    private static final class PerformanceReport extends PerformanceAction {
        PerformanceReport(CivPerformanceRecorder profiler) {
            super("perf-report", "Read aggregate profiling costs.", profiler);
        }
        @Override protected void run(CommandContext context, World world) {
            result(context, "perf-report", profiler.report());
        }
    }

    private static final class PerformanceSamples extends PerformanceAction {
        private final RequiredArg<Integer> offset;
        PerformanceSamples(CivPerformanceRecorder profiler) {
            super("perf-samples", "Read ten timestamped profiling samples starting at an offset.", profiler);
            offset = withRequiredArg("offset", "Zero-based sample offset", ArgTypes.INTEGER);
        }
        @Override protected void run(CommandContext context, World world) {
            int at = context.get(offset);
            if (at < 0 || at > CivPerformanceRecorder.MAX_SECONDS)
                throw new IllegalArgumentException("Sample offset out of range");
            result(context, "perf-samples", profiler.samples(at, 10));
        }
    }

    private static final class PerformanceEvents extends PerformanceAction {
        private final RequiredArg<Integer> offset;
        PerformanceEvents(CivPerformanceRecorder profiler) {
            super("perf-events", "Read recorded timestamped Civ performance markers.", profiler);
            offset = withRequiredArg("offset", "Zero-based event offset", ArgTypes.INTEGER);
        }
        @Override protected void run(CommandContext context, World world) {
            int at = context.get(offset);
            if (at < 0 || at > 2048) throw new IllegalArgumentException("Event offset out of range");
            result(context, "perf-events", profiler.events(at, 100));
        }
    }

    private static final class PerformanceMark extends PerformanceAction {
        private final RequiredArg<String> label;
        PerformanceMark(CivPerformanceRecorder profiler) {
            super("perf-mark", "Add a manual marker to the active recording.", profiler);
            label = withRequiredArg("label", "Marker name (one word)", ArgTypes.STRING);
        }
        @Override protected void run(CommandContext context, World world) {
            if (!profiler.isActive()) throw new IllegalArgumentException("Profiler is inactive");
            profiler.event("manual", context.get(label));
            result(context, "perf-mark", Map.of("recorded", true));
        }
    }

    private static final class Capabilities extends WorldAction {
        Capabilities() { super("capabilities", "List currently implemented agent actions."); }

        @Override protected void run(CommandContext context, World world) {
            result(context, "capabilities", Map.of(
                "version", 1,
                "actions", List.of(
                    "capabilities", "players", "buildings", "sites", "block",
                    "set-block", "create-site", "mine-recover",
                    "perf-start", "perf-stop", "perf-status", "perf-report", "perf-samples",
                    "perf-events", "perf-mark",
                    "civdev npcs", "civdev npc", "civdev spawn",
                    "civdev profession", "civdev assign-mine", "civdev mine-info"
                ),
                "limitations", List.of(
                    "Only the listed actions are implemented",
                    "World actions require loaded chunks",
                    "Building placement creates a normal construction site, not an instant building",
                    "Client-only camera/UI interaction is not available from the console"
                )
            ));
        }
    }

    private static final class Players extends WorldAction {
        Players() { super("players", "Inspect connected Hytale players."); }

        @Override protected void run(CommandContext context, World world) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (PlayerRef player : Universe.get().getPlayers()) {
                if (player == null || !world.getWorldConfig().getUuid().equals(player.getWorldUuid())
                    || player.getTransform() == null) continue;
                var pos = player.getTransform().getPosition();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("uuid", player.getUuid().toString());
                row.put("username", player.getUsername());
                row.put("position", List.of(pos.x, pos.y, pos.z));
                rows.add(row);
            }
            result(context, "players", rows);
        }
    }

    private static final class Buildings extends WorldAction {
        private final BuildingPlacementRegistry registry;
        Buildings(BuildingPlacementRegistry registry) {
            super("buildings", "List existing Civ buildings.");
            this.registry = registry;
        }
        @Override protected void run(CommandContext context, World world) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (var building : registry.buildings(world.getWorldConfig().getUuid())) {
                rows.add(Map.of("uuid", building.id().toString(),
                    "type", building.buildingType(), "phase", building.phase(),
                    "upgrading", registry.isUpgrading(building.worldId(), building.id())));
            }
            result(context, "buildings", rows);
        }
    }

    private static final class Sites extends WorldAction {
        private final ConstructionSiteRegistry sites;
        Sites(ConstructionSiteRegistry sites) {
            super("sites", "List current Civ construction sites and layer progress.");
            this.sites = sites;
        }
        @Override protected void run(CommandContext context, World world) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (var state : sites.states(world.getWorldConfig().getUuid())) {
                var site = state.site();
                rows.add(Map.of("uuid", site.id().toString(),
                    "type", site.candidate().definition().id(),
                    "completedLayers", state.completedLayers(),
                    "anchor", List.of(site.candidate().anchor().x,
                        site.candidate().anchor().y, site.candidate().anchor().z)));
            }
            result(context, "sites", rows);
        }
    }

    private abstract static class AtBlock extends WorldAction {
        final RequiredArg<Integer> x;
        final RequiredArg<Integer> y;
        final RequiredArg<Integer> z;
        AtBlock(String name, String description) {
            super(name, description);
            x = withRequiredArg("x", "World X coordinate", ArgTypes.INTEGER);
            y = withRequiredArg("y", "World Y coordinate", ArgTypes.INTEGER);
            z = withRequiredArg("z", "World Z coordinate", ArgTypes.INTEGER);
        }
        int x(CommandContext c) { return c.get(x); }
        int y(CommandContext c) { return c.get(y); }
        int z(CommandContext c) { return c.get(z); }

        WorldChunk loaded(World world, CommandContext c) {
            if (Math.abs((long)x(c)) > 30000 || Math.abs((long)z(c)) > 30000
                || Math.abs((long)y(c)) > 4096) throw new IllegalArgumentException("Coordinates out of bounds");
            WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x(c),z(c)));
            if (chunk == null) throw new IllegalArgumentException("Target chunk not loaded");
            return chunk;
        }
    }

    private static final class BlockInspect extends AtBlock {
        BlockInspect() { super("block", "Inspect a block in an already-loaded chunk."); }
        @Override protected void run(CommandContext context, World world) {
            WorldChunk chunk = loaded(world, context);
            BlockType block = chunk.getBlockType(x(context),y(context),z(context));
            result(context, "block", Map.of("position", List.of(x(context), y(context), z(context)),
                "block", block == null ? "unknown" : block.getId(),
                "index", chunk.getBlock(x(context),y(context),z(context))));
        }
    }

    private static final class BlockSet extends AtBlock {
        private final RequiredArg<String> type;
        private final BuildingPlacementRegistry buildings;
        BlockSet(BuildingPlacementRegistry buildings) {
            super("set-block", "Change a single block outside protected Civ buildings.");
            this.buildings = buildings;
            type = withRequiredArg("block", "Existing Hytale block asset ID", ArgTypes.STRING);
        }
        @Override protected void run(CommandContext context, World world) {
            WorldChunk chunk = loaded(world, context);
            var pos = new BlockPosition(x(context),y(context),z(context));
            if (buildings.isProtected(world.getWorldConfig().getUuid(), pos))
                throw new IllegalArgumentException("Protected Civ building or construction site");
            String assetId = context.get(type);
            BlockType next = BlockType.getAssetMap().getAsset(assetId);
            int index = BlockType.getAssetMap().getIndex(assetId);
            if (next == null || index < 0) throw new IllegalArgumentException("Unknown block asset ID");
            String before = chunk.getBlockType(x(context),y(context),z(context)) == null
                ? "unknown" : chunk.getBlockType(x(context),y(context),z(context)).getId();
            ChunkStore store = world.getChunkStore();
            Ref<ChunkStore> section = store.getChunkSectionReferenceAtBlock(
                x(context),y(context),z(context));
            if (section == null || !section.isValid())
                throw new IllegalArgumentException("Block section not loaded");
            boolean changed = BlockOperations.setBlock(store, section,
                x(context),y(context),z(context),index,next,0,0,256);
            if (!changed) throw new IllegalArgumentException("Native block write rejected");
            BlockType after = chunk.getBlockType(x(context),y(context),z(context));
            if (after == null || !assetId.equals(after.getId()))
                throw new IllegalArgumentException("Block change not observed after native write");
            result(context, "set-block", Map.of("position",List.of(x(context),y(context),z(context)),
                "before",before,"after",after.getId(),"confirmed",true));
        }
    }

    private static final class CreateSite extends WorldAction {
        private final RequiredArg<UUID> player;
        private final RequiredArg<String> buildingType;
        private final RequiredArg<Integer> x;
        private final RequiredArg<Integer> y;
        private final RequiredArg<Integer> z;
        private final BuildingPlacementRegistry buildings;
        private final PrefabPlacementService placement;
        private final ConstructionSiteRegistry sites;
        private final CivConstructionPersistenceService persistence;

        CreateSite(BuildingPlacementRegistry buildings, PrefabPlacementService placement,
                   ConstructionSiteRegistry sites, CivConstructionPersistenceService persistence) {
            super("create-site", "Create a normal Civ construction site at the selected ground block.");
            this.buildings = buildings;
            this.placement = placement;
            this.sites = sites;
            this.persistence = persistence;
            player = withRequiredArg("player", "Connected player UUID", ArgTypes.UUID);
            buildingType = withRequiredArg("type", "mine, farm or wheat_field", ArgTypes.STRING);
            x = withRequiredArg("x", "Pointed block X", ArgTypes.INTEGER);
            y = withRequiredArg("y", "Pointed block Y", ArgTypes.INTEGER);
            z = withRequiredArg("z", "Pointed block Z", ArgTypes.INTEGER);
        }

        @Override protected void run(CommandContext context, World world) {
            PlayerRef ref = Universe.get().getPlayer(context.get(player));
            if (ref == null || ref.getReference() == null || !ref.getReference().isValid()
                || !world.getWorldConfig().getUuid().equals(ref.getWorldUuid()))
                throw new IllegalArgumentException("Player is not connected in the default world");
            PrefabPlacementService.PlacementDefinition definition = switch (context.get(buildingType)) {
                case "mine" -> PrefabPlacementService.MINE;
                case "farm" -> PrefabPlacementService.FARM;
                case "wheat_field" -> PrefabPlacementService.WHEAT_FIELD;
                default -> throw new IllegalArgumentException("Unknown authored Civ building type");
            };
            var point = new Vector3i(context.get(x),context.get(y),context.get(z));
            var candidate = placement.validatePlacement(world,point,definition);
            if (!candidate.valid()) throw new IllegalArgumentException(candidate.invalidReason());
            if (buildings.overlaps(world.getWorldConfig().getUuid(),candidate.footprint()))
                throw new IllegalArgumentException("Overlapping Civ building or construction site");
            var site = placement.createConstructionSiteAtClick(ref,candidate);
            buildings.reserve(world.getWorldConfig().getUuid(),site.id(),candidate.footprint());
            sites.register(site);
            persistence.saveSites(world,sites.states());
            result(context,"create-site",Map.of("siteUuid",site.id().toString(),
                "type",definition.id(),"status","construction_site_created",
                "anchor",List.of(candidate.anchor().x,candidate.anchor().y,candidate.anchor().z)));
        }
    }

    private static final class MineRecover extends WorldAction {
        private final RequiredArg<UUID> mine;
        private final RequiredArg<String> mode;
        private final CivMineDebugService debug;
        MineRecover(CivMineDebugService debug) {
            super("mine-recover", "Recover workers or reopen blocked mine fronts by mine UUID.");
            this.debug = debug;
            mine = withRequiredArg("mine", "Mine UUID", ArgTypes.UUID);
            mode = withRequiredArg("mode", "status, workers, fronts or all", ArgTypes.STRING);
        }
        @Override protected void run(CommandContext context, World world) {
            String requested = context.get(mode);
            boolean workers = requested.equals("workers") || requested.equals("all");
            boolean fronts = requested.equals("fronts") || requested.equals("all");
            if (!requested.equals("status") && !workers && !fronts)
                throw new IllegalArgumentException("Unknown recovery mode");
            UUID id = context.get(mine);
            var recovery = workers || fronts ? debug.recover(world,id,workers,fronts)
                : debug.recoveryStatus(world.getWorldConfig().getUuid(),id);
            if (!recovery.mineFound()) throw new IllegalArgumentException("Mine not found");
            result(context,"mine-recover",Map.of("mineUuid",id.toString(),"mode",requested,
                "workers",recovery.workers(),"fronts",recovery.fronts(),
                "completedFrontsPreserved",recovery.completedFrontsPreserved(),
                "note","Recovery schedules worker reset; verify gameplay after subsequent ticks"));
        }
    }
}
