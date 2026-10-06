package dev.civilizations.hytale;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.civilizations.core.MineNetwork;
import dev.civilizations.core.MineTunnelGeometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime authority for persistent mine networks and regenerated tunnel geometry. */
public final class MineTunnelRegistry {

    private final CivMinePersistenceService persistence;
    private final Map<UUID, Map<UUID, MineNetwork>> networks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Map<UUID, MineTunnelGeometry>>> runtimeGeometries =
        new ConcurrentHashMap<>();

    public MineTunnelRegistry(CivMinePersistenceService persistence) {
        this.persistence = persistence;
    }

    public synchronized void loadWorld(World world) {
        UUID worldId = world.getWorldConfig().getUuid();
        Map<UUID, MineNetwork> loadedNetworks = new ConcurrentHashMap<>();
        for (MineNetwork network : persistence.loadNetworks(world)) loadedNetworks.put(network.mineId(), network);
        networks.put(worldId, loadedNetworks);
        runtimeGeometries.put(worldId, new ConcurrentHashMap<>());
    }

    public MineNetwork networkForMine(UUID worldId, UUID mineId) {
        return networks.getOrDefault(worldId, Map.of()).get(mineId);
    }

    public List<MineNetwork> networks(UUID worldId) {
        return List.copyOf(networks.getOrDefault(worldId, Map.of()).values());
    }

    /**
     * Stores deterministic Layer-3 geometry regenerated for the current runtime.
     *
     * <p>The Hytale world remains authoritative for blocks actually excavated. Geometry is not
     * persisted because the current mine plan deterministically rebuilds it after restart.</p>
     */
    public synchronized void putRuntimeGeometries(
        UUID worldId,
        UUID mineId,
        Map<UUID, MineTunnelGeometry> geometries
    ) {
        runtimeGeometries
            .computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>())
            .put(mineId, Map.copyOf(geometries));
    }

    public MineTunnelGeometry geometryForTunnel(UUID worldId, UUID mineId, UUID tunnelId) {
        return runtimeGeometries
            .getOrDefault(worldId, Map.of())
            .getOrDefault(mineId, Map.of())
            .get(tunnelId);
    }

    public Map<UUID, MineTunnelGeometry> geometriesForMine(UUID worldId, UUID mineId) {
        return runtimeGeometries
            .getOrDefault(worldId, Map.of())
            .getOrDefault(mineId, Map.of());
    }

    /** Replaces persistent semantic mine-network state. */
    public synchronized void putNetwork(World world, MineNetwork network) {
        UUID worldId = world.getWorldConfig().getUuid();
        networks.computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>()).put(network.mineId(), network);
        save(world);
    }

    public synchronized void removeMine(World world, UUID mineId) {
        UUID worldId = world.getWorldConfig().getUuid();
        Map<UUID, MineNetwork> worldNetworks = networks.get(worldId);
        if (worldNetworks != null) worldNetworks.remove(mineId);
        Map<UUID, Map<UUID, MineTunnelGeometry>> worldGeometries = runtimeGeometries.get(worldId);
        if (worldGeometries != null) worldGeometries.remove(mineId);
        save(world);
    }

    /** Temporary compile bridge removed with the MinerWorkSystem cleanup in this same branch. */
    @Deprecated
    public List<?> segmentsForMine(UUID worldId, UUID mineId) {
        return List.of();
    }

    private void save(World world) {
        UUID worldId = world.getWorldConfig().getUuid();
        persistence.save(world, new ArrayList<>(networks.getOrDefault(worldId, Map.of()).values()));
    }
}
