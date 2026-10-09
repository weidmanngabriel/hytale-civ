package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import dev.civilizations.core.Profession;
import dev.civilizations.hytale.CivInhabitantData;
import dev.civilizations.hytale.CivUnitRegistry;
import org.joml.Vector3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Isolated, runtime-probe-only NPC identity/claim check across a plugin classloader reload. */
final class CivReloadNpcProbeCommand extends CommandBase {
    private static final UUID SPAWN_UUID =
        UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID WORKPLACE_UUID =
        UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final Path UUID_FILE = Path.of("reload-npc.uuid");
    private static final int EXPECTED_XP = 37;
    private final CivUnitRegistry units;
    private final boolean verify;

    CivReloadNpcProbeCommand(CivUnitRegistry units, boolean verify) {
        super(verify ? "civreloadnpcverify" : "civreloadnpcprepare",
            "Verifies persistent Civ inhabitant data across an in-process plugin reload.");
        requireNoPermission();
        this.units = units;
        this.verify = verify;
    }

    @Override
    protected void executeSync(CommandContext context) {
        World world = Universe.get().getDefaultWorld();
        if (world == null) {
            fail("default world unavailable", null);
            return;
        }
        if (verify) {
            world.execute(() -> verify(world));
        } else {
            world.execute(() -> preload(world));
        }
    }

    private void preload(World world) {
        try {
            var spawn = world.getWorldConfig().getSpawnProvider().getSpawnPoint(world, SPAWN_UUID);
            if (spawn == null) {
                fail("no spawn position", null);
                return;
            }
            var position = new Vector3d(spawn.getPosition()).add(2, 0, 0);
            var chunkIndex = ChunkUtil.indexChunkFromBlock(position.x, position.z);
            world.getChunkAsync(chunkIndex).whenComplete((chunk, error) ->
                world.execute(() -> {
                    if (chunk == null || error != null) {
                        fail("spawn chunk unavailable", error);
                    } else {
                        prepare(world, position, spawn.getRotation());
                    }
                })
            );
        } catch (Throwable error) {
            fail("preload failed", error);
        }
    }

    private void prepare(
        World world,
        Vector3d position,
        com.hypixel.hytale.math.vector.Rotation3f rotation
    ) {
        try {
            var spawned = NPCPlugin.get().spawnNPC(
                world.getEntityStore().getStore(),
                "Civ_Inhabitant",
                null,
                position,
                rotation
            );
            if (spawned == null || spawned.first() == null || !spawned.first().isValid()) {
                fail("NPC spawn failed", null);
                return;
            }
            Ref<EntityStore> ref = spawned.first();
            if (!units.toggleClaim(ref) || !units.isClaimed(ref)) {
                fail("NPC claim failed", null);
                return;
            }
            CivInhabitantData data = units.getInhabitantData(ref);
            if (data == null || data.gender() == null) {
                fail("inhabitant data missing after claim", null);
                return;
            }
            data.setIdentity(data.gender(), "Reload", "Runtime", "Probe");
            data.setProfessionXp(Profession.MINER, EXPECTED_XP);
            units.assignProfession(ref, Profession.MINER);
            units.assignWorkplace(ref, WORKPLACE_UUID);

            UUIDComponent component = ref.getStore()
                .getComponent(ref, UUIDComponent.getComponentType());
            if (component == null || component.getUuid() == null) {
                fail("NPC UUID missing", null);
                return;
            }
            UUID uuid = component.getUuid();
            world.scheduleAfter(() -> {
                try {
                    if (!ref.isValid() || !matches(ref)) {
                        fail("NPC data changed before unload", null);
                        return;
                    }
                    Files.writeString(UUID_FILE, uuid.toString());
                    System.out.println("CIV_RELOAD_NPC_PREPARED uuid=" + uuid
                        + " profession=MINER xp=" + EXPECTED_XP);
                } catch (Throwable error) {
                    fail("prepare assertion failed", error);
                }
            }, 500, TimeUnit.MILLISECONDS);
        } catch (Throwable error) {
            fail("NPC prepare failed", error);
        }
    }

    private void verify(World world) {
        try {
            UUID uuid = UUID.fromString(Files.readString(UUID_FILE).trim());
            Ref<EntityStore> ref = world.getEntityRef(uuid);
            if (ref == null || !ref.isValid()) {
                fail("entity missing after plugin reload uuid=" + uuid, null);
                return;
            }
            if (!matches(ref)) {
                fail("claimed NPC lost identity, profession, XP or workplace after reload", null);
                return;
            }
            if (!units.workersAt(WORKPLACE_UUID).contains(ref)) {
                fail("workplace registry did not rehydrate after reload", null);
                return;
            }
            System.out.println("CIV_RELOAD_NPC_RESTORED uuid=" + uuid
                + " profession=MINER xp=" + EXPECTED_XP);
        } catch (Throwable error) {
            fail("NPC verification failed", error);
        }
    }

    private boolean matches(Ref<EntityStore> ref) {
        if (!units.isClaimed(ref)) {
            return false;
        }
        CivInhabitantData data = units.getInhabitantData(ref);
        return data != null
            && "Reload Runtime Probe".equals(data.fullName())
            && data.profession() == Profession.MINER
            && data.professionXp(Profession.MINER) == EXPECTED_XP
            && WORKPLACE_UUID.toString().equals(data.workplaceId());
    }

    private static void fail(String message, Throwable error) {
        System.out.println("CIV_RELOAD_NPC_FAIL " + message);
        if (error != null) {
            error.printStackTrace(System.out);
        }
    }
}
