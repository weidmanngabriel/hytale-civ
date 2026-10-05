package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.role.support.CombatSupport;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import dev.civilizations.core.Profession;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.hytale.CivActivityRegistry;
import dev.civilizations.hytale.CivUnitRegistry;
import dev.civilizations.hytale.ProfessionBootstrapInventory;
import dev.civilizations.hytale.SoldierWorkSystem;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Real-Hytale runtime probe for the Soldier vertical slice. */
final class CivSoldierFixtureProbeCommand extends CommandBase {

    private static final String WORLD_NAME = "civ-soldier-runtime";
    private static final String FLAT_GENERATOR = "Flat";
    private static final String DEFAULT_STORAGE = "default";
    private static final String CIV_ROLE = "Civ_Inhabitant";
    private static final Vector3d SOLDIER_START = new Vector3d(0.5, 1.0, 0.5);
    private static final Vector3d HOSTILE_START = new Vector3d(8.5, 1.0, 0.5);
    private static final WorldPosition MANUAL_DESTINATION = new WorldPosition(-6.0, 1.0, 0.5);
    private static final long SETTLE_MILLIS = 500L;
    private static final long ASSERT_INTERVAL_MILLIS = 25L;
    private static final long PROBE_TIMEOUT_MILLIS = 45_000L;
    private static final double MINIMUM_CHASE_DISTANCE = 1.0;
    private static final float MINIMUM_HOSTILE_HEALTH = 200.0f;

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final SoldierWorkSystem soldierWorkSystem;

    CivSoldierFixtureProbeCommand(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry,
        SoldierWorkSystem soldierWorkSystem
    ) {
        super("civsoldierprobe", "Runs the real Hytale Soldier combat runtime scenario.");
        requireNoPermission();
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
        this.soldierWorkSystem = soldierWorkSystem;
    }

    @Override
    protected void executeSync(CommandContext context) {
        Universe universe = Universe.get();
        if (universe.getWorld(WORLD_NAME) != null || universe.isWorldLoadable(WORLD_NAME)) {
            fail("test world already exists: " + WORLD_NAME, null);
            return;
        }

        System.out.println("CIV_SOLDIER_RUNTIME_STARTED world=" + WORLD_NAME);
        universe.addWorld(WORLD_NAME, FLAT_GENERATOR, DEFAULT_STORAGE)
            .whenComplete((world, throwable) -> {
                if (throwable != null || world == null) {
                    fail("flat test world could not be created", throwable);
                    return;
                }
                world.execute(() -> preloadFixtureChunks(world));
            });
    }

    private void preloadFixtureChunks(World world) {
        try {
            List<CompletableFuture<WorldChunk>> futures = new ArrayList<>();
            for (int chunkX = -1; chunkX <= 1; chunkX++) {
                for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                    long chunkIndex = ChunkUtil.indexChunkFromBlock(chunkX * 32, chunkZ * 32);
                    futures.add(world.getChunkAsync(chunkIndex));
                }
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, throwable) -> world.execute(() -> {
                    if (throwable != null) {
                        fail("fixture chunks could not be loaded", throwable);
                        return;
                    }
                    world.scheduleAfter(
                        () -> spawnFixture(world),
                        SETTLE_MILLIS,
                        TimeUnit.MILLISECONDS
                    );
                }));
        } catch (Throwable throwable) {
            fail("fixture chunk preload threw an exception", throwable);
        }
    }

    private void spawnFixture(World world) {
        try {
            Ref<EntityStore> soldier = spawn(world, CIV_ROLE, SOLDIER_START);
            if (soldier == null) {
                fail("Hytale could not spawn Civ_Inhabitant", null);
                return;
            }
            if (!unitRegistry.toggleClaim(soldier) || !unitRegistry.isClaimed(soldier)) {
                fail("spawned soldier could not be claimed", null);
                return;
            }
            unitRegistry.assignProfession(soldier, Profession.SOLDIER);
            if (unitRegistry.getProfession(soldier) != Profession.SOLDIER) {
                fail("spawned Civ inhabitant was not assigned SOLDIER", null);
                return;
            }

            ItemStack inHand = InventoryComponent.getItemInHand(soldier.getStore(), soldier);
            if (inHand == null
                || !ProfessionBootstrapInventory.SOLDIER_SWORD_ITEM_ID.equals(inHand.getItemId())) {
                fail("soldier did not equip the native bootstrap sword", null);
                return;
            }

            Ref<EntityStore> hostile = spawnFirstHostileCombatNpc(world);
            if (hostile == null) {
                fail("no durable spawnable native NPC hostile to players was found", null);
                return;
            }

            float soldierHealth = health(soldier);
            float hostileHealth = health(hostile);
            if (!Float.isFinite(soldierHealth) || !Float.isFinite(hostileHealth)) {
                fail("fixture entities do not expose native health", null);
                return;
            }

            TransformComponent soldierTransform = soldier.getStore()
                .getComponent(soldier, TransformComponent.getComponentType());
            if (soldierTransform == null) {
                fail("soldier has no TransformComponent", null);
                return;
            }

            ProbeState state = new ProbeState(
                new Vector3d(soldierTransform.getPosition()),
                soldierHealth,
                hostileHealth,
                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PROBE_TIMEOUT_MILLIS)
            );
            System.out.println(
                "CIV_SOLDIER_FIXTURE_READY soldier=" + soldier.getIndex()
                    + " hostile=" + hostile.getIndex()
                    + " soldierHealth=" + soldierHealth
                    + " hostileHealth=" + hostileHealth
                    + " weapon=" + inHand.getItemId()
            );

            world.scheduleAfter(
                () -> assertProgress(world, soldier, hostile, state),
                ASSERT_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            fail("soldier fixture setup threw an exception", throwable);
        }
    }

    private Ref<EntityStore> spawnFirstHostileCombatNpc(World world) {
        List<String> roles = new ArrayList<>(NPCPlugin.get().getRoleTemplateNames(true));
        roles.sort(Comparator
            .comparingInt(CivSoldierFixtureProbeCommand::hostileRolePriority)
            .thenComparing(String::compareToIgnoreCase));

        int attempted = 0;
        for (String roleName : roles) {
            if (roleName == null || roleName.isBlank() || CIV_ROLE.equals(roleName)
                || isSyntheticTestRole(roleName)) {
                continue;
            }
            attempted++;
            Ref<EntityStore> candidate = null;
            try {
                NPCPlugin.get().validateSpawnableRole(roleName);
                candidate = spawn(world, roleName, HOSTILE_START);
                if (candidate == null) {
                    continue;
                }
                WorldSupport worldSupport = WorldSupport.get(candidate, candidate.getStore());
                CombatSupport combatSupport = CombatSupport.get(candidate, candidate.getStore());
                float candidateHealth = health(candidate);
                if (worldSupport != null
                    && worldSupport.getDefaultPlayerAttitude() == Attitude.HOSTILE
                    && combatSupport != null
                    && Float.isFinite(candidateHealth)
                    && candidateHealth >= MINIMUM_HOSTILE_HEALTH) {
                    System.out.println(
                        "CIV_SOLDIER_HOSTILE_ROLE role=" + roleName
                            + " attempts=" + attempted
                            + " health=" + candidateHealth
                    );
                    return candidate;
                }
            } catch (Throwable ignored) {
                // Probe discovery intentionally tries multiple native spawnable roles.
            }
            if (candidate != null && candidate.isValid()) {
                candidate.getStore().removeEntity(candidate, RemoveReason.REMOVE);
            }
        }
        return null;
    }

    private void assertProgress(
        World world,
        Ref<EntityStore> soldier,
        Ref<EntityStore> hostile,
        ProbeState state
    ) {
        try {
            if (!soldier.isValid()) {
                fail("soldier died before reciprocal combat and manual priority were verified", null);
                return;
            }
            if (!unitRegistry.isClaimed(soldier)
                || unitRegistry.getProfession(soldier) != Profession.SOLDIER) {
                fail("soldier lost its Civ claim or profession", null);
                return;
            }

            Ref<EntityStore> currentTarget = soldierWorkSystem.targetOf(soldier);
            if (!state.targetAcquired && hostile.isValid() && hostile.equals(currentTarget)) {
                state.targetAcquired = true;
                System.out.println("CIV_SOLDIER_TARGET_ACQUIRED target=" + hostile.getIndex());
            }

            if (!state.manualIssued && state.targetAcquired) {
                if (!activityRegistry.orderManualMove(soldier, MANUAL_DESTINATION)) {
                    fail("manual movement command could not be issued to soldier", null);
                    return;
                }
                state.manualIssued = true;
                System.out.println("CIV_SOLDIER_MANUAL_MOVE_ISSUED");
            }

            if (state.manualIssued && !state.interruptionObserved) {
                if (!activityRegistry.autonomousWorkAllowed(soldier)
                    && soldierWorkSystem.targetOf(soldier) == null) {
                    state.interruptionObserved = true;
                    System.out.println("CIV_SOLDIER_COMBAT_INTERRUPTED");
                    if (!activityRegistry.cancelManualMove(soldier)) {
                        fail("manual movement could not be completed/cancelled", null);
                        return;
                    }
                }
            } else if (state.interruptionObserved && !state.resumeObserved) {
                Ref<EntityStore> resumedTarget = soldierWorkSystem.targetOf(soldier);
                if (hostile.isValid() && hostile.equals(resumedTarget)) {
                    state.resumeObserved = true;
                    System.out.println("CIV_SOLDIER_COMBAT_RESUMED");
                }
            }

            TransformComponent transform = soldier.getStore()
                .getComponent(soldier, TransformComponent.getComponentType());
            if (transform == null) {
                fail("soldier lost its TransformComponent", null);
                return;
            }
            double moved = horizontalDistance(state.startPosition, transform.getPosition());
            if (!state.chaseObserved && moved >= MINIMUM_CHASE_DISTANCE) {
                state.chaseObserved = true;
                System.out.println(
                    "CIV_SOLDIER_CHASE_OBSERVED moved="
                        + String.format(Locale.ROOT, "%.2f", moved)
                );
            }

            float currentSoldierHealth = health(soldier);
            float currentHostileHealth = hostile.isValid() ? health(hostile) : Float.NaN;
            if (!state.targetDamageObserved
                && hostile.isValid()
                && Float.isFinite(currentHostileHealth)
                && currentHostileHealth < state.initialHostileHealth) {
                state.targetDamageObserved = true;
            }
            if (!state.soldierDamageObserved
                && Float.isFinite(currentSoldierHealth)
                && currentSoldierHealth < state.initialSoldierHealth) {
                state.soldierDamageObserved = true;
            }
            if (!state.damageMarkerPrinted
                && state.targetDamageObserved
                && state.soldierDamageObserved) {
                state.damageMarkerPrinted = true;
                System.out.println(
                    "CIV_SOLDIER_RECIPROCAL_DAMAGE soldierHealth=" + currentSoldierHealth
                        + " hostileHealth=" + currentHostileHealth
                );
            }

            if (state.resumeObserved
                && state.chaseObserved
                && state.targetDamageObserved
                && state.soldierDamageObserved) {
                System.out.println(
                    "CIV_SOLDIER_RUNTIME_PASS soldierHealth=" + health(soldier)
                        + " hostileHealth=" + (hostile.isValid() ? health(hostile) : 0.0f)
                );
                HytaleServer.get().shutdownServer();
                return;
            }

            if (!hostile.isValid()) {
                fail(
                    "hostile died before the full scenario was verified"
                        + ", acquired=" + state.targetAcquired
                        + ", interrupted=" + state.interruptionObserved
                        + ", resumed=" + state.resumeObserved
                        + ", chase=" + state.chaseObserved
                        + ", targetDamage=" + state.targetDamageObserved
                        + ", soldierDamage=" + state.soldierDamageObserved,
                    null
                );
                return;
            }

            if (System.nanoTime() >= state.deadlineNanos) {
                fail(
                    "soldier scenario timed out"
                        + ", acquired=" + state.targetAcquired
                        + ", chase=" + state.chaseObserved
                        + ", targetDamage=" + state.targetDamageObserved
                        + ", soldierDamage=" + state.soldierDamageObserved
                        + ", manualIssued=" + state.manualIssued
                        + ", interrupted=" + state.interruptionObserved
                        + ", resumed=" + state.resumeObserved,
                    null
                );
                return;
            }

            world.scheduleAfter(
                () -> assertProgress(world, soldier, hostile, state),
                ASSERT_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            fail("soldier assertion threw an exception", throwable);
        }
    }

    private static Ref<EntityStore> spawn(World world, String roleName, Vector3d position) {
        var spawned = NPCPlugin.get().spawnNPC(
            world.getEntityStore().getStore(),
            roleName,
            null,
            new Vector3d(position),
            new Rotation3f()
        );
        if (spawned == null || spawned.first() == null || !spawned.first().isValid()) {
            return null;
        }
        return spawned.first();
    }

    private static float health(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return Float.NaN;
        }
        EntityStatMap stats = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        return health == null ? Float.NaN : health.get();
    }

    private static boolean isSyntheticTestRole(String roleName) {
        String value = roleName.toLowerCase(Locale.ROOT);
        return value.startsWith("test_")
            || value.startsWith("debug_")
            || value.startsWith("example_");
    }

    private static int hostileRolePriority(String roleName) {
        String value = roleName.toLowerCase(Locale.ROOT);
        if (value.contains("goblin")) return 0;
        if (value.contains("trork")) return 1;
        if (value.contains("skeleton")) return 2;
        if (value.contains("spider")) return 3;
        if (value.contains("crawler")) return 4;
        if (value.contains("outlander")) return 5;
        if (value.contains("void")) return 6;
        return 20;
    }

    private static double horizontalDistance(Vector3d first, Vector3d second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void fail(String reason, Throwable throwable) {
        System.out.println("CIV_SOLDIER_RUNTIME_FAIL " + reason);
        if (throwable != null) {
            throwable.printStackTrace(System.out);
        }
        HytaleServer.get().shutdownServer();
    }

    private static final class ProbeState {
        private final Vector3d startPosition;
        private final float initialSoldierHealth;
        private final float initialHostileHealth;
        private final long deadlineNanos;
        private boolean targetAcquired;
        private boolean chaseObserved;
        private boolean targetDamageObserved;
        private boolean soldierDamageObserved;
        private boolean damageMarkerPrinted;
        private boolean manualIssued;
        private boolean interruptionObserved;
        private boolean resumeObserved;

        private ProbeState(
            Vector3d startPosition,
            float initialSoldierHealth,
            float initialHostileHealth,
            long deadlineNanos
        ) {
            this.startPosition = startPosition;
            this.initialSoldierHealth = initialSoldierHealth;
            this.initialHostileHealth = initialHostileHealth;
            this.deadlineNanos = deadlineNanos;
        }
    }
}
