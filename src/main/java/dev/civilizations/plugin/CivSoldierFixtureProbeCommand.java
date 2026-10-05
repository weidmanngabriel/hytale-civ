package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
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
import com.hypixel.hytale.server.npc.util.InventoryHelper;
import dev.civilizations.core.Profession;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.hytale.CivActivityRegistry;
import dev.civilizations.hytale.CivUnitRegistry;
import dev.civilizations.hytale.ProfessionBootstrapInventory;
import dev.civilizations.hytale.SoldierWorkSystem;
import org.joml.Vector3d;

import java.util.ArrayList;
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
    private static final String HOSTILE_FIXTURE_ROLE = "Bear_Grizzly";
    private static final List<Vector3d> SOLDIER_STARTS = List.of(
        new Vector3d(0.5, 1.0, 0.5),
        new Vector3d(0.5, 1.0, 3.5),
        new Vector3d(0.5, 1.0, -2.5)
    );
    private static final Vector3d HOSTILE_START = new Vector3d(8.5, 1.0, 0.5);
    private static final WorldPosition MANUAL_DESTINATION = new WorldPosition(-6.0, 1.0, 0.5);
    private static final long SETTLE_MILLIS = 500L;
    private static final long ASSERT_INTERVAL_MILLIS = 25L;
    private static final long PROBE_TIMEOUT_MILLIS = 45_000L;
    private static final double MINIMUM_CHASE_DISTANCE = 1.0;

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
                world.execute(() -> {
                    world.getWorldConfig().setCanUnloadChunks(false);
                    System.out.println("CIV_SOLDIER_CHUNK_UNLOAD_DISABLED");
                    preloadFixtureChunks(world);
                });
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
            List<Ref<EntityStore>> soldiers = new ArrayList<>();
            List<Vector3d> soldierStartPositions = new ArrayList<>();

            for (int i = 0; i < SOLDIER_STARTS.size(); i++) {
                Ref<EntityStore> soldier = spawn(world, CIV_ROLE, SOLDIER_STARTS.get(i));
                if (soldier == null) {
                    fail("Hytale could not spawn Civ_Inhabitant #" + (i + 1), null);
                    return;
                }
                if (!unitRegistry.toggleClaim(soldier) || !unitRegistry.isClaimed(soldier)) {
                    fail("spawned soldier #" + (i + 1) + " could not be claimed", null);
                    return;
                }
                unitRegistry.assignProfession(soldier, Profession.SOLDIER);
                if (unitRegistry.getProfession(soldier) != Profession.SOLDIER) {
                    fail("spawned Civ inhabitant #" + (i + 1) + " was not assigned SOLDIER", null);
                    return;
                }

                ItemStack inHand = InventoryComponent.getItemInHand(soldier.getStore(), soldier);
                if (inHand == null
                    || !ProfessionBootstrapInventory.SOLDIER_SWORD_ITEM_ID.equals(inHand.getItemId())) {
                    fail("soldier #" + (i + 1) + " did not equip the native bootstrap item", null);
                    return;
                }
                byte swordSlot = InventoryHelper.findHotbarSlotWithItem(
                    soldier,
                    soldier.getStore(),
                    ProfessionBootstrapInventory.SOLDIER_SWORD_ITEM_ID
                );
                if (swordSlot != 0) {
                    fail(
                        "soldier #" + (i + 1)
                            + " sword is not in CAE WeaponSlot 0: slot=" + swordSlot,
                        null
                    );
                    return;
                }

                TransformComponent transform = soldier.getStore()
                    .getComponent(soldier, TransformComponent.getComponentType());
                float health = health(soldier);
                if (transform == null || !Float.isFinite(health)) {
                    fail("soldier #" + (i + 1) + " lacks native transform or health", null);
                    return;
                }

                soldiers.add(soldier);
                soldierStartPositions.add(new Vector3d(transform.getPosition()));
                System.out.println(
                    "CIV_SOLDIER_MEMBER_READY ordinal=" + (i + 1)
                        + " entity=" + soldier.getIndex()
                        + " health=" + health
                        + " weapon=" + inHand.getItemId()
                        + " weaponSlot=" + swordSlot
                );
            }

            Ref<EntityStore> hostile = spawnStableHostileFixture(world);
            if (hostile == null) {
                fail("native hostile fixture could not be spawned", null);
                return;
            }

            float hostileHealth = health(hostile);
            if (!Float.isFinite(hostileHealth)) {
                fail("hostile fixture does not expose native health", null);
                return;
            }

            ProbeState state = new ProbeState(
                soldierStartPositions,
                hostileHealth,
                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PROBE_TIMEOUT_MILLIS)
            );
            System.out.println(
                "CIV_SOLDIER_FIXTURE_READY soldiers=" + soldiers.size()
                    + " hostile=" + hostile.getIndex()
                    + " role=" + HOSTILE_FIXTURE_ROLE
                    + " hostileHealth=" + hostileHealth
            );

            world.scheduleAfter(
                () -> assertProgress(world, soldiers, hostile, state),
                ASSERT_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            fail("soldier fixture setup threw an exception", throwable);
        }
    }

    private Ref<EntityStore> spawnStableHostileFixture(World world) {
        String resolvedRole = NPCPlugin.get().getRoleTemplateNames(true).stream()
            .filter(roleName -> roleName != null && (
                HOSTILE_FIXTURE_ROLE.equals(roleName)
                    || roleName.endsWith("/" + HOSTILE_FIXTURE_ROLE)
            ))
            .findFirst()
            .orElse(null);
        if (resolvedRole == null) {
            return null;
        }

        try {
            NPCPlugin.get().validateSpawnableRole(resolvedRole);
            Ref<EntityStore> candidate = spawn(world, resolvedRole, HOSTILE_START);
            if (candidate == null || !Float.isFinite(health(candidate))) {
                return null;
            }
            System.out.println(
                "CIV_SOLDIER_HOSTILE_ROLE role=" + resolvedRole
                    + " health=" + health(candidate)
            );
            return candidate;
        } catch (Throwable throwable) {
            System.out.println(
                "CIV_SOLDIER_FIXTURE_ROLE_FAIL role=" + resolvedRole
                    + " reason=" + throwable.getClass().getSimpleName()
            );
            return null;
        }
    }

    private void assertProgress(
        World world,
        List<Ref<EntityStore>> soldiers,
        Ref<EntityStore> hostile,
        ProbeState state
    ) {
        try {
            for (int i = 0; i < soldiers.size(); i++) {
                Ref<EntityStore> soldier = soldiers.get(i);
                if (!soldier.isValid()) {
                    fail("soldier #" + (i + 1) + " became invalid before group combat was verified", null);
                    return;
                }
                if (!unitRegistry.isClaimed(soldier)
                    || unitRegistry.getProfession(soldier) != Profession.SOLDIER) {
                    fail("soldier #" + (i + 1) + " lost its Civ claim or profession", null);
                    return;
                }

                Ref<EntityStore> currentTarget = soldierWorkSystem.targetOf(soldier);
                if (!state.targetAcquired[i] && hostile.isValid() && hostile.equals(currentTarget)) {
                    state.targetAcquired[i] = true;
                    System.out.println(
                        "CIV_SOLDIER_TARGET_ACQUIRED ordinal=" + (i + 1)
                            + " target=" + hostile.getIndex()
                    );
                }

                TransformComponent transform = soldier.getStore()
                    .getComponent(soldier, TransformComponent.getComponentType());
                if (transform == null) {
                    fail("soldier #" + (i + 1) + " lost its TransformComponent", null);
                    return;
                }
                double moved = horizontalDistance(state.startPositions.get(i), transform.getPosition());
                if (!state.chaseObserved[i] && moved >= MINIMUM_CHASE_DISTANCE) {
                    state.chaseObserved[i] = true;
                    System.out.println(
                        "CIV_SOLDIER_CHASE_OBSERVED ordinal=" + (i + 1)
                            + " moved=" + String.format(Locale.ROOT, "%.2f", moved)
                    );
                }
            }

            Ref<EntityStore> primary = soldiers.get(0);
            if (!state.manualIssued && allTrue(state.targetAcquired)) {
                System.out.println("CIV_SOLDIER_GROUP_TARGET_ACQUIRED count=" + soldiers.size());
                if (!activityRegistry.orderManualMove(primary, MANUAL_DESTINATION)) {
                    fail("manual movement command could not be issued to primary soldier", null);
                    return;
                }
                state.manualIssued = true;
                System.out.println("CIV_SOLDIER_MANUAL_MOVE_ISSUED");
            }

            if (state.manualIssued && !state.interruptionObserved) {
                if (!activityRegistry.autonomousWorkAllowed(primary)
                    && soldierWorkSystem.targetOf(primary) == null) {
                    state.interruptionObserved = true;
                    System.out.println("CIV_SOLDIER_COMBAT_INTERRUPTED");
                    if (!activityRegistry.cancelManualMove(primary)) {
                        fail("manual movement could not be completed/cancelled", null);
                        return;
                    }
                }
            } else if (state.interruptionObserved && !state.resumeObserved) {
                Ref<EntityStore> resumedTarget = soldierWorkSystem.targetOf(primary);
                if (hostile.isValid() && hostile.equals(resumedTarget)) {
                    state.resumeObserved = true;
                    System.out.println("CIV_SOLDIER_COMBAT_RESUMED");
                }
            }

            float currentHostileHealth = hostile.isValid() ? health(hostile) : Float.NaN;
            if (!state.targetDamageObserved
                && hostile.isValid()
                && Float.isFinite(currentHostileHealth)
                && currentHostileHealth < state.initialHostileHealth) {
                state.targetDamageObserved = true;
                System.out.println(
                    "CIV_SOLDIER_TARGET_DAMAGE_OBSERVED hostileHealth=" + currentHostileHealth
                );
            }

            if (state.resumeObserved
                && allTrue(state.targetAcquired)
                && allTrue(state.chaseObserved)
                && state.targetDamageObserved) {
                System.out.println(
                    "CIV_SOLDIER_RUNTIME_PASS soldiers=" + soldiers.size()
                        + " hostileRole=" + HOSTILE_FIXTURE_ROLE
                        + " hostileHealth=" + (hostile.isValid() ? health(hostile) : 0.0f)
                );
                HytaleServer.get().shutdownServer();
                return;
            }

            if (!hostile.isValid()) {
                if (state.targetDamageObserved
                    && state.resumeObserved
                    && allTrue(state.targetAcquired)
                    && allTrue(state.chaseObserved)) {
                    System.out.println(
                        "CIV_SOLDIER_RUNTIME_PASS soldiers=" + soldiers.size()
                            + " hostileRole=" + HOSTILE_FIXTURE_ROLE
                            + " hostileDefeated=true"
                    );
                    HytaleServer.get().shutdownServer();
                    return;
                }
                fail(
                    "hostile fixture became invalid before the full group scenario was verified"
                        + ", acquired=" + countTrue(state.targetAcquired) + "/" + soldiers.size()
                        + ", chase=" + countTrue(state.chaseObserved) + "/" + soldiers.size()
                        + ", targetDamage=" + state.targetDamageObserved
                        + ", interrupted=" + state.interruptionObserved
                        + ", resumed=" + state.resumeObserved,
                    null
                );
                return;
            }

            if (System.nanoTime() >= state.deadlineNanos) {
                fail(
                    "soldier group scenario timed out"
                        + ", acquired=" + countTrue(state.targetAcquired) + "/" + soldiers.size()
                        + ", chase=" + countTrue(state.chaseObserved) + "/" + soldiers.size()
                        + ", targetDamage=" + state.targetDamageObserved
                        + ", manualIssued=" + state.manualIssued
                        + ", interrupted=" + state.interruptionObserved
                        + ", resumed=" + state.resumeObserved,
                    null
                );
                return;
            }

            world.scheduleAfter(
                () -> assertProgress(world, soldiers, hostile, state),
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

    private static double horizontalDistance(Vector3d first, Vector3d second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean allTrue(boolean[] values) {
        for (boolean value : values) {
            if (!value) {
                return false;
            }
        }
        return true;
    }

    private static int countTrue(boolean[] values) {
        int count = 0;
        for (boolean value : values) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    private static void fail(String reason, Throwable throwable) {
        System.out.println("CIV_SOLDIER_RUNTIME_FAIL " + reason);
        if (throwable != null) {
            throwable.printStackTrace(System.out);
        }
        HytaleServer.get().shutdownServer();
    }

    private static final class ProbeState {
        private final List<Vector3d> startPositions;
        private final float initialHostileHealth;
        private final long deadlineNanos;
        private final boolean[] targetAcquired;
        private final boolean[] chaseObserved;
        private boolean targetDamageObserved;
        private boolean manualIssued;
        private boolean interruptionObserved;
        private boolean resumeObserved;

        private ProbeState(
            List<Vector3d> startPositions,
            float initialHostileHealth,
            long deadlineNanos
        ) {
            this.startPositions = List.copyOf(startPositions);
            this.initialHostileHealth = initialHostileHealth;
            this.deadlineNanos = deadlineNanos;
            this.targetAcquired = new boolean[startPositions.size()];
            this.chaseObserved = new boolean[startPositions.size()];
        }
    }
}
