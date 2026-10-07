package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import dev.civilizations.core.Profession;
import dev.civilizations.hytale.CivUnitRegistry;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns only ephemeral entities created through civdev commands. */
final class CivDevScenarioService {
    private static final String CIV_ROLE = "Civ_Inhabitant";
    private static final String SOLDIER_HOSTILE_ROLE = "Bear_Grizzly";

    private final CivUnitRegistry units;
    private final CivDevRuntimeState state;
    private final CivDevEventHistory history;

    CivDevScenarioService(
        CivUnitRegistry units,
        CivDevRuntimeState state,
        CivDevEventHistory history
    ) {
        this.units = units;
        this.state = state;
        this.history = history;
    }

    ScenarioResult setup(World world, String scenario, double x, double y, double z) {
        if (!"soldier".equalsIgnoreCase(scenario)) {
            throw new IllegalArgumentException("unknown scenario; supported: soldier");
        }
        reset(world);

        List<UUID> created = new ArrayList<>();
        try {
            created.add(spawnTracked(world, CIV_ROLE, new Vector3d(x, y, z), Profession.SOLDIER));
            created.add(spawnTracked(world, CIV_ROLE, new Vector3d(x, y, z + 3.0), Profession.SOLDIER));
            created.add(spawnTracked(world, CIV_ROLE, new Vector3d(x, y, z - 3.0), Profession.SOLDIER));
            created.add(spawnTracked(world, SOLDIER_HOSTILE_ROLE, new Vector3d(x + 8.0, y, z), null));
        } catch (RuntimeException exception) {
            reset(world);
            throw exception;
        }

        history.record(created.get(0), "scenario_started", Map.of("scenario", "soldier"));
        return new ScenarioResult("soldier", List.copyOf(created));
    }

    UUID registerSpawned(Ref<EntityStore> ref, String role) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        UUIDComponent component =
            ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
        if (component == null) {
            return null;
        }
        UUID uuid = component.getUuid();
        state.trackSpawn(uuid);
        history.record(uuid, "spawned", Map.of("role", role == null ? "unknown" : role));
        return uuid;
    }

    int reset(World world) {
        int removed = 0;
        var store = world.getEntityStore().getStore();
        for (UUID uuid : state.spawnedSnapshot()) {
            Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(uuid);
            if (ref != null && ref.isValid()) {
                history.record(uuid, "reset_removed", Map.of());
                store.removeEntity(ref, RemoveReason.REMOVE);
                removed++;
            }
            state.forget(uuid);
        }
        return removed;
    }

    private UUID spawnTracked(
        World world,
        String requestedRole,
        Vector3d position,
        Profession profession
    ) {
        String role = resolveRole(requestedRole);
        if (role == null) {
            throw new IllegalArgumentException("unknown NPC role: " + requestedRole);
        }
        NPCPlugin.get().validateSpawnableRole(role);
        var spawned = NPCPlugin.get().spawnNPC(
            world.getEntityStore().getStore(),
            role,
            null,
            position,
            new Rotation3f()
        );
        Ref<EntityStore> ref = spawned == null ? null : spawned.first();
        if (ref == null || !ref.isValid()) {
            throw new IllegalStateException("Hytale did not create NPC role " + role);
        }
        UUID uuid = registerSpawned(ref, role);
        if (uuid == null) {
            throw new IllegalStateException("spawned NPC has no UUID");
        }

        if (profession != null) {
            if (!units.isClaimed(ref) && !units.toggleClaim(ref)) {
                throw new IllegalStateException("could not claim spawned Civ inhabitant");
            }
            units.assignProfession(ref, profession);
            history.record(uuid, "profession_assigned", Map.of("profession", profession.name()));
        }
        return uuid;
    }

    static String resolveRole(String requested) {
        if (requested == null || requested.isBlank()) {
            return null;
        }
        return NPCPlugin.get().getRoleTemplateNames(true).stream()
            .filter(name -> requested.equals(name) || name.endsWith("/" + requested))
            .sorted()
            .findFirst()
            .orElse(null);
    }

    record ScenarioResult(String scenario, List<UUID> entities) {
    }
}
