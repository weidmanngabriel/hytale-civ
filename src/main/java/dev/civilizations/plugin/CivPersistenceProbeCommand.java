package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import dev.civilizations.core.Profession;
import dev.civilizations.hytale.CivInhabitantData;
import dev.civilizations.hytale.CivUnitRegistry;
import org.joml.Vector3d;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Two-stage runtime probe for the real Hytale entity persistence boundary.
 *
 * <p>The prepare stage creates a claimed Civ inhabitant with deterministic persistent data and
 * shuts the server down normally. The restore stage is expected to run in a second server process
 * against the same runtime directory and verifies that Hytale restored the same entity/component.
 */
final class CivPersistenceProbeCommand extends CommandBase {

    private static final String ROLE = "Civ_Inhabitant";
    private static final String STAGE_PROPERTY = "civilizations.persistenceProbeStage";
    private static final String ENTITY_UUID_PROPERTY = "civilizations.persistenceProbeEntityUuid";
    private static final String STAGE_PREPARE = "prepare";
    private static final String STAGE_RESTORE = "restore";

    private static final UUID FIXED_SPAWN_PROBE_UUID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EXPECTED_WORKPLACE_UUID =
        UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String EXPECTED_FIRST_NAME = "Persist";
    private static final String EXPECTED_MIDDLE_NAME = "Runtime";
    private static final String EXPECTED_LAST_NAME = "Probe";
    private static final String EXPECTED_FULL_NAME =
        EXPECTED_FIRST_NAME + " " + EXPECTED_MIDDLE_NAME + " " + EXPECTED_LAST_NAME;
    private static final Profession EXPECTED_PROFESSION = Profession.CONSTRUCTION_WORKER;
    private static final int EXPECTED_PROFESSION_XP = 37;
    private static final String EXPECTED_WORKPLACE_ID = EXPECTED_WORKPLACE_UUID.toString();

    private static final long PREPARE_SETTLE_MILLIS = 500L;
    private static final long RESTORE_RETRY_MILLIS = 250L;
    private static final long RESTORE_TIMEOUT_MILLIS = 5_000L;

    private final CivUnitRegistry unitRegistry;

    CivPersistenceProbeCommand(CivUnitRegistry unitRegistry) {
        super("civpersistenceprobe", "Runs the two-stage Civ persistence runtime probe.");
        requireNoPermission();
        this.unitRegistry = unitRegistry;
    }

    @Override
    protected void executeSync(CommandContext context) {
        World world = Universe.get().getDefaultWorld();
        if (world == null) {
            fail("default world is unavailable", null);
            return;
        }

        String stage = System.getProperty(STAGE_PROPERTY, "").trim().toLowerCase();
        switch (stage) {
            case STAGE_PREPARE -> world.execute(() -> prepare(world));
            case STAGE_RESTORE -> world.execute(() -> restore(world));
            default -> fail("unknown persistence probe stage: " + stage, null);
        }
    }

    private void prepare(World world) {
        try {
            var spawn = world.getWorldConfig()
                .getSpawnProvider()
                .getSpawnPoint(world, FIXED_SPAWN_PROBE_UUID);
            if (spawn == null) {
                fail("spawn provider returned no spawn point", null);
                return;
            }

            Vector3d spawnPosition = new Vector3d(spawn.getPosition()).add(1.0, 0.0, 0.0);
            long chunkIndex = ChunkUtil.indexChunkFromBlock(spawnPosition.x, spawnPosition.z);
            world.getChunkAsync(chunkIndex).whenComplete((chunk, throwable) ->
                world.execute(() -> {
                    if (throwable != null || chunk == null) {
                        fail("prepare chunk could not be loaded", throwable);
                        return;
                    }
                    spawnPersistentInhabitant(world, spawnPosition, spawn.getRotation());
                })
            );
        } catch (Throwable throwable) {
            fail("prepare stage threw an exception", throwable);
        }
    }

    private void spawnPersistentInhabitant(
        World world,
        Vector3d spawnPosition,
        com.hypixel.hytale.math.vector.Rotation3f spawnRotation
    ) {
        try {
            var spawned = NPCPlugin.get().spawnNPC(
                world.getEntityStore().getStore(),
                ROLE,
                null,
                spawnPosition,
                spawnRotation
            );
            if (spawned == null || spawned.first() == null) {
                fail("Hytale could not spawn persistence-probe Civ_Inhabitant", null);
                return;
            }

            Ref<EntityStore> ref = spawned.first();
            if (!ref.isValid()) {
                fail("spawned persistence-probe ref is invalid", null);
                return;
            }
            if (!unitRegistry.toggleClaim(ref) || !unitRegistry.isClaimed(ref)) {
                fail("spawned persistence-probe NPC could not be claimed", null);
                return;
            }

            CivInhabitantData data = unitRegistry.getInhabitantData(ref);
            if (data == null || data.gender() == null || !data.hasAppearance()) {
                fail("claimed persistence-probe inhabitant was not fully initialized", null);
                return;
            }

            data.setIdentity(
                data.gender(),
                EXPECTED_FIRST_NAME,
                EXPECTED_MIDDLE_NAME,
                EXPECTED_LAST_NAME
            );
            data.setProfessionXp(EXPECTED_PROFESSION, EXPECTED_PROFESSION_XP);
            unitRegistry.assignProfession(ref, EXPECTED_PROFESSION);
            unitRegistry.assignWorkplace(ref, EXPECTED_WORKPLACE_UUID);

            UUIDComponent uuidComponent = ref.getStore()
                .getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComponent == null || uuidComponent.getUuid() == null) {
                fail("persistence-probe inhabitant has no UUIDComponent", null);
                return;
            }
            UUID entityUuid = uuidComponent.getUuid();

            world.scheduleAfter(
                () -> finishPrepare(ref, entityUuid),
                PREPARE_SETTLE_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            fail("prepare spawn/setup threw an exception", throwable);
        }
    }

    private void finishPrepare(Ref<EntityStore> ref, UUID entityUuid) {
        try {
            if (!ref.isValid() || !unitRegistry.isClaimed(ref)) {
                fail("persistence-probe inhabitant became invalid before shutdown", null);
                return;
            }

            CivInhabitantData data = unitRegistry.getInhabitantData(ref);
            if (!matchesExpectedPersistentData(data)) {
                fail("persistence-probe data changed before shutdown", null);
                return;
            }
            if (!unitRegistry.workersAt(EXPECTED_WORKPLACE_UUID).contains(ref)) {
                fail("persistence-probe workplace was not projected before shutdown", null);
                return;
            }

            System.out.println(
                "CIV_PERSISTENCE_PREPARED uuid=" + entityUuid
                    + " name=Persist_Runtime_Probe"
                    + " profession=" + data.profession()
                    + " xp=" + data.professionXp(EXPECTED_PROFESSION)
                    + " workplace=" + data.workplaceId()
            );
            System.out.println("CIV_PERSISTENCE_WORKPLACE_INDEXED_PREPARE");
            System.out.println("CIV_PERSISTENCE_PREPARE_PASS");
            HytaleServer.get().shutdownServer();
        } catch (Throwable throwable) {
            fail("prepare assertion threw an exception", throwable);
        }
    }

    private void restore(World world) {
        String uuidValue = System.getProperty(ENTITY_UUID_PROPERTY, "").trim();
        final UUID expectedUuid;
        try {
            expectedUuid = UUID.fromString(uuidValue);
        } catch (IllegalArgumentException exception) {
            fail("restore stage received an invalid entity UUID: " + uuidValue, exception);
            return;
        }

        try {
            var spawn = world.getWorldConfig()
                .getSpawnProvider()
                .getSpawnPoint(world, FIXED_SPAWN_PROBE_UUID);
            if (spawn == null) {
                fail("spawn provider returned no spawn point during restore", null);
                return;
            }

            Vector3d spawnPosition = new Vector3d(spawn.getPosition()).add(1.0, 0.0, 0.0);
            long chunkIndex = ChunkUtil.indexChunkFromBlock(spawnPosition.x, spawnPosition.z);
            long deadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(RESTORE_TIMEOUT_MILLIS);

            world.getChunkAsync(chunkIndex).whenComplete((chunk, throwable) ->
                world.execute(() -> {
                    if (throwable != null || chunk == null) {
                        fail("restore chunk could not be loaded", throwable);
                        return;
                    }
                    assertRestored(world, expectedUuid, deadlineNanos);
                })
            );
        } catch (Throwable throwable) {
            fail("restore stage threw an exception", throwable);
        }
    }

    private void assertRestored(World world, UUID expectedUuid, long deadlineNanos) {
        try {
            Ref<EntityStore> ref = world.getEntityRef(expectedUuid);
            if (ref == null || !ref.isValid()) {
                if (System.nanoTime() < deadlineNanos) {
                    world.scheduleAfter(
                        () -> assertRestored(world, expectedUuid, deadlineNanos),
                        RESTORE_RETRY_MILLIS,
                        TimeUnit.MILLISECONDS
                    );
                    return;
                }
                fail("persisted entity was not restored after loading its chunk: " + expectedUuid, null);
                return;
            }

            UUIDComponent uuidComponent = ref.getStore()
                .getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComponent == null || !expectedUuid.equals(uuidComponent.getUuid())) {
                fail("restored entity UUID does not match the prepared entity", null);
                return;
            }
            if (!unitRegistry.isClaimed(ref)) {
                fail("restored entity lost CivInhabitantData / claimed state", null);
                return;
            }

            CivInhabitantData data = unitRegistry.getInhabitantData(ref);
            if (!matchesExpectedPersistentData(data)) {
                fail("restored CivInhabitantData does not match prepared persistent data", null);
                return;
            }
            if (!data.hasAppearance()) {
                fail("restored Civ inhabitant lost its persisted appearance", null);
                return;
            }
            if (ref.getStore().getComponent(ref, PersistentDisplayName.getComponentType()) == null
                || ref.getStore().getComponent(ref, DisplayNameComponent.getComponentType()) == null) {
                fail("restored Civ inhabitant presentation was not rehydrated", null);
                return;
            }
            if (!unitRegistry.workersAt(EXPECTED_WORKPLACE_UUID).contains(ref)) {
                if (System.nanoTime() < deadlineNanos) {
                    world.scheduleAfter(
                        () -> assertRestored(world, expectedUuid, deadlineNanos),
                        RESTORE_RETRY_MILLIS,
                        TimeUnit.MILLISECONDS
                    );
                    return;
                }
                fail("restored workplace was not rehydrated into CivUnitRegistry", null);
                return;
            }

            System.out.println(
                "CIV_PERSISTENCE_RESTORED uuid=" + expectedUuid
                    + " name=Persist_Runtime_Probe"
                    + " profession=" + data.profession()
                    + " xp=" + data.professionXp(EXPECTED_PROFESSION)
                    + " workplace=" + data.workplaceId()
            );
            System.out.println("CIV_PERSISTENCE_WORKPLACE_INDEXED_RESTORE");
            System.out.println("CIV_PERSISTENCE_RESTORE_PASS");
            HytaleServer.get().shutdownServer();
        } catch (Throwable throwable) {
            fail("restore assertion threw an exception", throwable);
        }
    }

    private static boolean matchesExpectedPersistentData(CivInhabitantData data) {
        return data != null
            && data.hasIdentity()
            && EXPECTED_FULL_NAME.equals(data.fullName())
            && data.profession() == EXPECTED_PROFESSION
            && data.professionXp(EXPECTED_PROFESSION) == EXPECTED_PROFESSION_XP
            && EXPECTED_WORKPLACE_ID.equals(data.workplaceId());
    }

    private static void fail(String reason, Throwable throwable) {
        System.out.println("CIV_PERSISTENCE_PROBE_FAIL " + reason);
        if (throwable != null) {
            throwable.printStackTrace(System.out);
        }
        HytaleServer.get().shutdownServer();
    }
}
