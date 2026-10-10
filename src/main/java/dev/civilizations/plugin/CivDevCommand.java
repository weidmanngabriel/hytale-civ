package dev.civilizations.plugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import dev.civilizations.core.Profession;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.hytale.CivActivityRegistry;
import dev.civilizations.hytale.CivInhabitantData;
import dev.civilizations.hytale.CivMineDebugService;
import dev.civilizations.hytale.MinerWorkSystem;
import dev.civilizations.hytale.CivMinerAssignmentService;
import dev.civilizations.hytale.CivUnitRegistry;
import dev.civilizations.hytale.BuildingPlacementRegistry;
import dev.civilizations.hytale.SoldierWorkSystem;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Small console-capable development command surface used by the local MCP command bridge.
 *
 * <p>All world/entity work is dispatched onto Hytale's world executor. The commands compose
 * existing Civ services and native Hytale APIs; they do not own a second gameplay state.</p>
 */
final class CivDevCommand extends AbstractCommandCollection {
    private static final int NPC_LIST_LIMIT = 100;

    CivDevCommand(
        CivUnitRegistry units,
        CivActivityRegistry activities,
        CivMinerAssignmentService minerAssignments,
        BuildingPlacementRegistry buildings,
        CivMineDebugService mineDebug,
        MinerWorkSystem minerWorkSystem,
        CivDevScenarioService scenarios,
        CivDevEventHistory history
    ) {
        super("civdev", "Console-capable Civilizations development commands.");
        addSubCommand(new NpcsCommand(units, activities));
        addSubCommand(new NpcCommand(units, activities));
        addSubCommand(new SpawnCommand(units, scenarios));
        addSubCommand(new MoveCommand(units, activities));
        addSubCommand(new ProfessionCommand(units));
        addSubCommand(new MinesCommand(buildings, units, activities));
        addSubCommand(new MineInfoCommand(mineDebug));
        addSubCommand(new MineRetryStairCommand(minerWorkSystem));
        addSubCommand(new AssignMineCommand(buildings, minerAssignments));
        addSubCommand(new CivDevScenarioCommand(scenarios));
        addSubCommand(new CivDevResetCommand(scenarios));
        addSubCommand(new CivDevEventsCommand(history));
        addSubCommand(new CivWorldExportCommand());
    }

    private abstract static class WorldCommand extends AbstractAsyncCommand {
        WorldCommand(String name, String description) {
            super(name, description);
        }

        @Override
        protected final CompletableFuture<Void> executeAsync(CommandContext context) {
            Universe universe = Universe.get();
            World world = universe == null ? null : universe.getDefaultWorld();
            if (world == null || !world.isAlive()) {
                context.sendMessage(Message.raw("CIVDEV_ERROR no active default world"));
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.runAsync(
                () -> executeWorld(context, world, world.getEntityStore().getStore()),
                world
            );
        }

        protected abstract void executeWorld(
            CommandContext context,
            World world,
            Store<EntityStore> store
        );

        protected static Ref<EntityStore> requireEntity(
            CommandContext context,
            World world,
            UUID uuid
        ) {
            Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(uuid);
            if (ref == null || !ref.isValid()) {
                context.sendMessage(Message.raw("CIVDEV_ERROR loaded entity not found: " + uuid));
                return null;
            }
            return ref;
        }
    }

    private static final class NpcsCommand extends WorldCommand {
        private final CivUnitRegistry units;
        private final CivActivityRegistry activities;

        NpcsCommand(CivUnitRegistry units, CivActivityRegistry activities) {
            super("npcs", "Lists loaded native NPCs in the default world.");
            this.units = units;
            this.activities = activities;
        }

        @Override
        protected void executeWorld(CommandContext context, World world, Store<EntityStore> store) {
            List<Map<String, Object>> rows = loadedNpcSnapshots(world, store, units, activities);
            context.sendMessage(Message.raw(
                "CIVDEV_NPCS world=" + world.getName() + " loaded=" + rows.size()
                    + (rows.size() >= NPC_LIST_LIMIT ? " limit=" + NPC_LIST_LIMIT : "")
            ));
            for (Map<String, Object> row : rows) {
                @SuppressWarnings("unchecked")
                Map<String, Object> civ = (Map<String, Object>) row.get("civ");
                @SuppressWarnings("unchecked")
                Map<String, Object> pos = (Map<String, Object>) row.get("position");
                String suffix = Boolean.TRUE.equals(civ.get("claimed"))
                    ? " | " + civ.get("name") + " | " + civ.get("profession")
                    : "";
                context.sendMessage(Message.raw(
                    row.get("uuid") + " | " + row.get("role") + suffix
                        + " | pos=" + pos.get("x") + "," + pos.get("y") + "," + pos.get("z")
                ));
            }
        }
    }

    private static final class NpcCommand extends WorldCommand {
        private final RequiredArg<UUID> uuid;
        private final FlagArg json;
        private final CivUnitRegistry units;
        private final CivActivityRegistry activities;
        private final ObjectMapper mapper = new ObjectMapper();

        NpcCommand(CivUnitRegistry units, CivActivityRegistry activities) {
            super("npc", "Shows one loaded native/Civ NPC by UUID.");
            this.units = units;
            this.activities = activities;
            uuid = withRequiredArg("uuid", "Loaded entity UUID.", ArgTypes.UUID);
            json = withFlagArg("json", "Return one machine-readable JSON object.");
        }

        @Override
        protected void executeWorld(CommandContext context, World world, Store<EntityStore> store) {
            Ref<EntityStore> ref = requireEntity(context, world, context.get(uuid));
            if (ref == null) return;
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc == null) {
                context.sendMessage(Message.raw("CIVDEV_ERROR entity is not an NPC"));
                return;
            }
            Map<String, Object> snapshot = npcSnapshot(world, ref, units, activities);
            if (Boolean.TRUE.equals(context.get(json))) {
                try {
                    context.sendMessage(Message.raw(mapper.writeValueAsString(snapshot)));
                } catch (JsonProcessingException exception) {
                    context.sendMessage(Message.raw("CIVDEV_ERROR could not serialize NPC snapshot"));
                }
                return;
            }
            context.sendMessage(Message.raw("CIVDEV_NPC " + snapshot.get("uuid") + " role=" + snapshot.get("role")));
            context.sendMessage(Message.raw("world=" + snapshot.get("world") + " position=" + snapshot.get("position")));
            context.sendMessage(Message.raw("health=" + snapshot.get("health")));
            context.sendMessage(Message.raw("civ=" + snapshot.get("civ")));
            context.sendMessage(Message.raw("activity=" + snapshot.get("activity")));
            context.sendMessage(Message.raw("combat=" + snapshot.get("combat")));
            context.sendMessage(Message.raw("itemInHand=" + snapshot.get("itemInHand")));
        }
    }

    private static final class SpawnCommand extends WorldCommand {
        private final RequiredArg<String> role;
        private final RequiredArg<Double> x;
        private final RequiredArg<Double> y;
        private final RequiredArg<Double> z;
        private final CivUnitRegistry units;
        private final CivDevScenarioService scenarios;

        SpawnCommand(CivUnitRegistry units, CivDevScenarioService scenarios) {
            super("spawn", "Spawns a native Hytale NPC in the default world.");
            this.units = units;
            this.scenarios = scenarios;
            role = withRequiredArg("role", "Native Hytale NPC role.", ArgTypes.STRING);
            x = withRequiredArg("x", "World X coordinate.", ArgTypes.DOUBLE);
            y = withRequiredArg("y", "World Y coordinate.", ArgTypes.DOUBLE);
            z = withRequiredArg("z", "World Z coordinate.", ArgTypes.DOUBLE);
        }

        @Override
        protected void executeWorld(CommandContext context, World world, Store<EntityStore> store) {
            String requested = context.get(role);
            String resolved = resolveRole(requested);
            if (resolved == null) {
                context.sendMessage(Message.raw("CIVDEV_ERROR unknown NPC role: " + requested));
                return;
            }
            try {
                NPCPlugin.get().validateSpawnableRole(resolved);
                var spawned = NPCPlugin.get().spawnNPC(
                    store,
                    resolved,
                    null,
                    new Vector3d(context.get(x), context.get(y), context.get(z)),
                    new Rotation3f()
                );
                Ref<EntityStore> ref = spawned == null ? null : spawned.first();
                if (ref == null || !ref.isValid()) {
                    context.sendMessage(Message.raw("CIVDEV_ERROR Hytale did not create the NPC"));
                    return;
                }
                boolean claimed = units.isClaimed(ref);
                if (!claimed && ("Civ_Inhabitant".equals(resolved) || resolved.endsWith("/Civ_Inhabitant"))) {
                    claimed = units.toggleClaim(ref);
                }
                UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
                UUID trackedUuid = scenarios.registerSpawned(ref, resolved);
                context.sendMessage(Message.raw(
                    "CIVDEV_SPAWNED uuid=" + (uuid == null ? "unavailable" : uuid.getUuid())
                        + " role=" + resolved + " claimed=" + claimed
                        + " resetTracked=" + (trackedUuid != null)
                ));
            } catch (RuntimeException exception) {
                context.sendMessage(Message.raw(
                    "CIVDEV_ERROR spawn failed: "
                        + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())
                ));
            }
        }
    }

    private static final class MoveCommand extends WorldCommand {
        private final RequiredArg<UUID> uuid;
        private final RequiredArg<Double> x;
        private final RequiredArg<Double> y;
        private final RequiredArg<Double> z;
        private final CivUnitRegistry units;
        private final CivActivityRegistry activities;

        MoveCommand(CivUnitRegistry units, CivActivityRegistry activities) {
            super("move", "Orders a loaded Civ inhabitant to move through the normal Civ manual-move path.");
            this.units = units;
            this.activities = activities;
            uuid = withRequiredArg("uuid", "Loaded Civ entity UUID.", ArgTypes.UUID);
            x = withRequiredArg("x", "World X coordinate.", ArgTypes.DOUBLE);
            y = withRequiredArg("y", "World Y coordinate.", ArgTypes.DOUBLE);
            z = withRequiredArg("z", "World Z coordinate.", ArgTypes.DOUBLE);
        }

        @Override
        protected void executeWorld(CommandContext context, World world, Store<EntityStore> store) {
            Ref<EntityStore> ref = requireEntity(context, world, context.get(uuid));
            if (ref == null) return;
            if (!units.isClaimed(ref)) {
                context.sendMessage(Message.raw("CIVDEV_ERROR move requires a Civ inhabitant"));
                return;
            }
            WorldPosition destination = new WorldPosition(context.get(x), context.get(y), context.get(z));
            if (!activities.orderManualMove(ref, destination)) {
                context.sendMessage(Message.raw("CIVDEV_ERROR manual move was rejected"));
                return;
            }
            context.sendMessage(Message.raw(
                "CIVDEV_MOVED uuid=" + context.get(uuid)
                    + " destination=" + destination.x() + "," + destination.y() + "," + destination.z()
            ));
        }
    }

    private static final class ProfessionCommand extends WorldCommand {
        private final RequiredArg<UUID> uuid;
        private final RequiredArg<String> profession;
        private final CivUnitRegistry units;

        ProfessionCommand(CivUnitRegistry units) {
            super("profession", "Assigns a Civ profession through CivUnitRegistry.");
            this.units = units;
            uuid = withRequiredArg("uuid", "Loaded Civ entity UUID.", ArgTypes.UUID);
            profession = withRequiredArg("profession", "Civ profession enum value.", ArgTypes.STRING);
        }

        @Override
        protected void executeWorld(CommandContext context, World world, Store<EntityStore> store) {
            Ref<EntityStore> ref = requireEntity(context, world, context.get(uuid));
            if (ref == null) return;
            if (!units.isClaimed(ref)) {
                context.sendMessage(Message.raw("CIVDEV_ERROR profession requires a Civ inhabitant"));
                return;
            }
            Profession next;
            try {
                next = Profession.valueOf(context.get(profession).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                context.sendMessage(Message.raw(
                    "CIVDEV_ERROR unknown profession; use " + java.util.Arrays.toString(Profession.values())
                ));
                return;
            }
            units.assignProfession(ref, next);
            context.sendMessage(Message.raw(
                "CIVDEV_PROFESSION uuid=" + context.get(uuid) + " profession=" + units.getProfession(ref)
            ));
        }
    }

    private static final class MinesCommand extends WorldCommand {
        private final BuildingPlacementRegistry buildings;
        private final CivUnitRegistry units;
        private final CivActivityRegistry activities;

        MinesCommand(
            BuildingPlacementRegistry buildings,
            CivUnitRegistry units,
            CivActivityRegistry activities
        ) {
            super("mines", "Lists completed mines and their loaded Civ workers.");
            this.buildings = buildings;
            this.units = units;
            this.activities = activities;
        }

        @Override
        protected void executeWorld(CommandContext context, World world, Store<EntityStore> store) {
            List<Map<String, Object>> workers = loadedNpcSnapshots(world, store, units, activities);
            List<BuildingPlacementRegistry.BuildingInstance> mines = buildings.buildings(world.getWorldConfig().getUuid())
                .stream().filter(building -> "mine".equals(building.buildingType()))
                .sorted(Comparator.comparing(building -> building.id().toString())).toList();
            context.sendMessage(Message.raw("CIVDEV_MINES world=" + world.getName() + " count=" + mines.size()));
            for (BuildingPlacementRegistry.BuildingInstance mine : mines) {
                long assigned = workers.stream().filter(row -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> civ = (Map<String, Object>) row.get("civ");
                    return "MINER".equals(civ.get("profession"))
                        && mine.id().toString().equals(civ.get("workplaceId"));
                }).count();
                boolean connector = mine.semanticVolumes().stream()
                    .anyMatch(volume -> volume.hasTag("civ.type", "mine_tunnel_connector")
                        && volume.hasTag("civ.building", "mine"));
                var bounds = mine.bounds();
                context.sendMessage(Message.raw(
                    "id=" + mine.id() + " phase=" + mine.phase() + " workers=" + assigned
                        + " upgrading=" + buildings.isUpgrading(mine.worldId(), mine.id())
                        + " connector=" + connector + " bounds=" + bounds.minX() + "," + bounds.minY() + ","
                        + bounds.minZ() + ".." + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ()
                ));
            }
        }
    }

    private static final class AssignMineCommand extends WorldCommand {
        private final RequiredArg<UUID> npcUuid;
        private final RequiredArg<UUID> mineUuid;
        private final BuildingPlacementRegistry buildings;
        private final CivMinerAssignmentService minerAssignments;

        AssignMineCommand(
            BuildingPlacementRegistry buildings,
            CivMinerAssignmentService minerAssignments
        ) {
            super("assign-mine", "Assigns a loaded Civ inhabitant to a completed mine.");
            this.buildings = buildings;
            this.minerAssignments = minerAssignments;
            npcUuid = withRequiredArg("npc", "Loaded Civ inhabitant UUID.", ArgTypes.UUID);
            mineUuid = withRequiredArg("mine", "Mine building UUID from civdev mines.", ArgTypes.UUID);
        }

        @Override
        protected void executeWorld(CommandContext context, World world, Store<EntityStore> store) {
            UUID npcId = context.get(npcUuid);
            UUID mineId = context.get(mineUuid);
            Ref<EntityStore> miner = requireEntity(context, world, npcId);
            if (miner == null) return;
            BuildingPlacementRegistry.BuildingInstance mine = buildings.findIncludingUpgrading(
                world.getWorldConfig().getUuid(), mineId
            );
            if (mine == null) {
                context.sendMessage(Message.raw("CIVDEV_ERROR mine not found in the default world: " + mineId));
                return;
            }
            CivMinerAssignmentService.Result result = minerAssignments.assign(miner, mine);
            switch (result) {
                case ASSIGNED -> context.sendMessage(Message.raw(
                    "CIVDEV_ASSIGNED_MINE npc=" + npcId + " mine=" + mineId + " profession=MINER"
                ));
                case NOT_CIV_INHABITANT -> context.sendMessage(Message.raw(
                    "CIVDEV_ERROR assignment requires a loaded Civ inhabitant"
                ));
                case NOT_A_MINE -> context.sendMessage(Message.raw("CIVDEV_ERROR building is not a mine"));
                case UPGRADING -> context.sendMessage(Message.raw("CIVDEV_ERROR mine is upgrading"));
                case MISSING_CONNECTOR -> context.sendMessage(Message.raw("CIVDEV_ERROR mine has no valid tunnel connector"));
            }
        }
    }

    private static final class MineRetryStairCommand extends WorldCommand {
        private final RequiredArg<UUID> mineUuid;
        private final MinerWorkSystem minerWorkSystem;

        MineRetryStairCommand(MinerWorkSystem minerWorkSystem) {
            super("mine-retry-stair", "Reopen an old abandoned MAIN front only if a pending lower-slice stair matches.");
            this.minerWorkSystem = minerWorkSystem;
            mineUuid = withRequiredArg("mine", "Mine UUID from civdev mines.", ArgTypes.UUID);
        }

        @Override
        protected void executeWorld(CommandContext context, World world, Store<EntityStore> store) {
            UUID mineId = context.get(mineUuid);
            MinerWorkSystem.StairRetryResult result =
                minerWorkSystem.retryAbandonedMainStair(world, mineId);
            context.sendMessage(Message.raw("CIVDEV_MINE_RETRY_STAIR mine=" + mineId + " result=" + result));
        }
    }

    private static final class MineInfoCommand extends WorldCommand {
        private final RequiredArg<UUID> mineUuid;
        private final CivMineDebugService mineDebug;

        MineInfoCommand(CivMineDebugService mineDebug) {
            super("mine-info", "Shows tunnel and work-front state for a registered mine.");
            this.mineDebug = mineDebug;
            mineUuid = withRequiredArg("mine", "Mine UUID from civdev mines.", ArgTypes.UUID);
        }

        @Override
        protected void executeWorld(CommandContext context, World world, Store<EntityStore> store) {
            UUID worldId = world.getWorldConfig().getUuid();
            UUID id = context.get(mineUuid);
            CivMineDebugService.MineDebugSnapshot snapshot = mineDebug.snapshot(worldId, id);
            if (snapshot == null) {
                context.sendMessage(Message.raw("CIVDEV_ERROR mine not found in the default world: " + id));
                return;
            }

            BuildingPlacementRegistry.BuildingInstance mine = snapshot.mine();
            context.sendMessage(Message.raw(
                "CIVDEV_MINE_INFO id=" + id + " phase=" + mine.phase()
                    + " tunnels=" + snapshot.tunnels().size()
                    + " active=" + snapshot.activeFrontCount()
                    + " open=" + snapshot.openFrontCount()
            ));
            for (CivMineDebugService.TunnelDebugSnapshot tunnel : snapshot.tunnels()) {
                String front = tunnel.front() == null ? "none"
                    : tunnel.front().state() + "@" + tunnel.front().position();
                String slices = tunnel.geometry() == null ? "not-generated"
                    : Integer.toString(tunnel.geometry().slices().size());
                context.sendMessage(Message.raw(
                    "tunnel=" + tunnel.tunnel().id()
                        + " kind=" + tunnel.tunnel().kind()
                        + " depth=" + tunnel.tunnel().branchDepth()
                        + " origin=" + tunnel.tunnel().origin()
                        + " front=" + front
                        + " slices=" + slices
                ));
            }
        }
    }

    private static List<Map<String, Object>> loadedNpcSnapshots(
        World world,
        Store<EntityStore> store,
        CivUnitRegistry units,
        CivActivityRegistry activities
    ) {
        Query<EntityStore> query = Archetype.of(
            NPCEntity.getComponentType(),
            UUIDComponent.getComponentType(),
            TransformComponent.getComponentType()
        );
        List<Map<String, Object>> rows = new ArrayList<>();
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size() && rows.size() < NPC_LIST_LIMIT; i++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref != null && ref.isValid()) {
                    rows.add(npcSnapshot(world, ref, units, activities));
                }
            }
        });
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("uuid"))));
        return List.copyOf(rows);
    }

    private static Map<String, Object> npcSnapshot(
        World world,
        Ref<EntityStore> ref,
        CivUnitRegistry units,
        CivActivityRegistry activities
    ) {
        Store<EntityStore> store = ref.getStore();
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        CivInhabitantData data = units.getInhabitantData(ref);
        boolean claimed = data != null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", uuid == null ? null : uuid.getUuid().toString());
        result.put("world", world.getName());
        result.put("role", npc == null ? null : npc.getRoleName());
        result.put("displayName", displayName(ref));
        result.put("position", position(transform == null ? null : transform.getPosition()));
        result.put("health", health(ref));
        result.put("itemInHand", itemInHand(ref));

        Map<String, Object> civ = new LinkedHashMap<>();
        civ.put("claimed", claimed);
        civ.put("name", claimed && data.hasIdentity() ? data.fullName() : null);
        civ.put("profession", claimed ? data.profession().name() : null);
        civ.put("workplaceId", claimed ? data.workplaceId() : null);
        result.put("civ", civ);

        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("manualMove", claimed && activities.manualMovementIntent(ref) != null);
        activity.put("autonomousWorkAllowed", !claimed || activities.autonomousWorkAllowed(ref));
        activity.put("moveTarget", position(units.getMoveTarget(ref)));
        result.put("activity", activity);

        Ref<EntityStore> combatTarget = combatTarget(ref);
        Map<String, Object> combat = new LinkedHashMap<>();
        combat.put("targetUuid", entityUuid(combatTarget));
        combat.put("targetName", displayName(combatTarget));
        result.put("combat", combat);
        return result;
    }

    private static String resolveRole(String requested) {
        if (requested == null || requested.isBlank()) return null;
        return NPCPlugin.get().getRoleTemplateNames(true).stream()
            .filter(name -> requested.equals(name) || name.endsWith("/" + requested))
            .sorted()
            .findFirst()
            .orElse(null);
    }

    private static Map<String, Object> position(org.joml.Vector3dc value) {
        if (value == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("x", round(value.x()));
        result.put("y", round(value.y()));
        result.put("z", round(value.z()));
        return result;
    }

    private static Map<String, Object> health(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return null;
        EntityStatMap stats = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue value = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        if (value == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("current", round(value.get()));
        result.put("max", round(value.getMax()));
        return result;
    }

    private static String itemInHand(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return null;
        ItemStack stack = InventoryComponent.getItemInHand(ref.getStore(), ref);
        return stack == null ? null : stack.getItemId();
    }

    private static Ref<EntityStore> combatTarget(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return null;
        MarkedEntitySupport marked = MarkedEntitySupport.get(ref, ref.getStore());
        Ref<EntityStore> target = marked == null
            ? null
            : marked.getMarkedEntityRef(SoldierWorkSystem.COMBAT_TARGET_SLOT);
        return target != null && target.isValid() ? target : null;
    }

    private static String entityUuid(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return null;
        UUIDComponent component = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
        return component == null ? null : component.getUuid().toString();
    }

    private static String displayName(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return null;
        DisplayNameComponent display = ref.getStore().getComponent(ref, DisplayNameComponent.getComponentType());
        if (display != null && display.getDisplayName() != null) {
            String raw = display.getDisplayName().getRawText();
            if (raw != null && !raw.isBlank()) return raw;
            String id = display.getDisplayName().getMessageId();
            if (id != null && !id.isBlank()) return id;
        }
        NPCEntity npc = ref.getStore().getComponent(ref, NPCEntity.getComponentType());
        return npc == null ? null : npc.getRoleName();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
