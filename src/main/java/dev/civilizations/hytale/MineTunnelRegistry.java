package dev.civilizations.hytale;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineNetwork;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MineTunnel;
import dev.civilizations.core.MineTunnelGeometry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime authority for Civ mine network metadata and concrete excavation segments. */
public final class MineTunnelRegistry {

    private final CivMinePersistenceService persistence;
    private final Map<UUID, Map<UUID, MineSegment>> worlds = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, MineNetwork>> networks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Map<UUID, MineTunnelGeometry>>> runtimeGeometries =
        new ConcurrentHashMap<>();

    public MineTunnelRegistry(CivMinePersistenceService persistence) {
        this.persistence = persistence;
    }

    public synchronized void loadWorld(World world) {
        UUID worldId = world.getWorldConfig().getUuid();
        Map<UUID, MineSegment> loadedSegments = new ConcurrentHashMap<>();
        for (MineSegment segment : persistence.load(world)) loadedSegments.put(segment.id(), segment);
        worlds.put(worldId, loadedSegments);

        Map<UUID, MineNetwork> loadedNetworks = new ConcurrentHashMap<>();
        for (MineNetwork network : persistence.loadNetworks(world)) loadedNetworks.put(network.mineId(), network);
        networks.put(worldId, loadedNetworks);
        runtimeGeometries.put(worldId, new ConcurrentHashMap<>());

        // Existing development worlds may contain the pre-network segment format. Convert that
        // one current shape into a main-tunnel network instead of keeping a second runtime truth.
        boolean changed = false;
        for (MineSegment segment : loadedSegments.values()) {
            MineNetwork network = loadedNetworks.get(segment.mineId());
            if (network == null) {
                network = bootstrapNetwork(segment.mineId(), rootOrigin(loadedSegments.values(), segment));
                loadedNetworks.put(segment.mineId(), network);
                changed = true;
            }
            if (!containsSegment(network, segment.id())) {
                MineTunnel main = network.mainTunnel().withSegment(segment.id());
                loadedNetworks.put(segment.mineId(), network.withTunnel(main));
                changed = true;
            }
        }
        if (changed) save(world);
    }

    public List<MineSegment> segments(UUID worldId) {
        return List.copyOf(worlds.getOrDefault(worldId, Map.of()).values());
    }

    public List<MineSegment> segmentsForMine(UUID worldId, UUID mineId) {
        return segments(worldId).stream().filter(segment -> segment.mineId().equals(mineId)).toList();
    }

    public MineSegment get(UUID worldId, UUID segmentId) {
        return worlds.getOrDefault(worldId, Map.of()).get(segmentId);
    }

    public MineNetwork networkForMine(UUID worldId, UUID mineId) {
        return networks.getOrDefault(worldId, Map.of()).get(mineId);
    }

    public List<MineNetwork> networks(UUID worldId) {
        return List.copyOf(networks.getOrDefault(worldId, Map.of()).values());
    }

    /**
     * Stores the deterministic Layer-3 geometry regenerated for the current runtime.
     *
     * <p>The world stays authoritative for blocks actually excavated. Geometry is intentionally not
     * persisted here; it is deterministically rebuilt from the mine plan after restart.</p>
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

    /**
     * Transitional Layer-1 entry point: newly created existing-miner segments default to the main
     * tunnel. Updates to an already assigned segment preserve its logical tunnel membership.
     */
    public synchronized void put(World world, MineSegment segment) {
        UUID worldId = world.getWorldConfig().getUuid();
        MineNetwork network = ensureNetwork(worldId, segment.mineId(), segment.start());
        MineTunnel assignedTunnel = tunnelForSegment(network, segment.id());
        if (assignedTunnel == null) assignedTunnel = network.mainTunnel();
        putInternal(worldId, assignedTunnel.id(), segment);
        save(world);
    }

    /** Stores a concrete excavation segment under an explicitly selected logical tunnel. */
    public synchronized void put(World world, UUID tunnelId, MineSegment segment) {
        UUID worldId = world.getWorldConfig().getUuid();
        ensureNetwork(worldId, segment.mineId(), segment.start());
        putInternal(worldId, tunnelId, segment);
        save(world);
    }

    /** Replaces semantic network metadata without changing world geometry or segment progress. */
    public synchronized void putNetwork(World world, MineNetwork network) {
        UUID worldId = world.getWorldConfig().getUuid();
        networks.computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>()).put(network.mineId(), network);
        save(world);
    }

    public synchronized void removeMine(World world, UUID mineId) {
        UUID worldId = world.getWorldConfig().getUuid();
        Map<UUID, MineSegment> segments = worlds.get(worldId);
        if (segments != null) segments.values().removeIf(segment -> segment.mineId().equals(mineId));
        Map<UUID, MineNetwork> worldNetworks = networks.get(worldId);
        if (worldNetworks != null) worldNetworks.remove(mineId);
        Map<UUID, Map<UUID, MineTunnelGeometry>> worldGeometries = runtimeGeometries.get(worldId);
        if (worldGeometries != null) worldGeometries.remove(mineId);
        save(world);
    }

    /**
     * Rejects overlap with every reserved/completed segment. Concrete excavation geometry stays
     * separate from the logical tunnel hierarchy introduced by the mine network.
     */
    public boolean conflicts(UUID worldId, MineSegment candidate) {
        Set<BlockPosition> candidateBlocks = new HashSet<>(candidate.blocks());
        for (MineSegment existing : segments(worldId)) {
            if (existing.id().equals(candidate.id())) continue;
            for (BlockPosition block : existing.blocks()) {
                if (candidateBlocks.contains(block)) return true;
            }
        }
        return false;
    }

    public MineSegment unfinishedForMine(UUID worldId, UUID mineId) {
        return segmentsForMine(worldId, mineId).stream()
            .filter(segment -> segment.status() == MineSegment.Status.RESERVED
                || segment.status() == MineSegment.Status.MINING)
            .findFirst()
            .orElse(null);
    }

    private void putInternal(UUID worldId, UUID tunnelId, MineSegment segment) {
        Map<UUID, MineNetwork> worldNetworks = networks.computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>());
        MineNetwork network = worldNetworks.get(segment.mineId());
        MineTunnel tunnel = network == null ? null : network.tunnel(tunnelId);
        if (tunnel == null) throw new IllegalArgumentException("Unknown logical mine tunnel: " + tunnelId);

        worlds.computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>()).put(segment.id(), segment);
        worldNetworks.put(segment.mineId(), network.withTunnel(tunnel.withSegment(segment.id())));
    }

    private MineNetwork ensureNetwork(UUID worldId, UUID mineId, BlockPosition origin) {
        Map<UUID, MineNetwork> worldNetworks = networks.computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>());
        return worldNetworks.computeIfAbsent(mineId, ignored -> bootstrapNetwork(mineId, origin));
    }

    private static MineNetwork bootstrapNetwork(UUID mineId, BlockPosition origin) {
        UUID mainTunnelId = UUID.nameUUIDFromBytes(("civ-mine-main:" + mineId).getBytes(StandardCharsets.UTF_8));
        return MineNetwork.create(mineId, mainTunnelId, origin);
    }

    private static MineTunnel tunnelForSegment(MineNetwork network, UUID segmentId) {
        return network.tunnels().stream()
            .filter(tunnel -> tunnel.segmentIds().contains(segmentId))
            .findFirst()
            .orElse(null);
    }

    private static boolean containsSegment(MineNetwork network, UUID segmentId) {
        return tunnelForSegment(network, segmentId) != null;
    }

    private static BlockPosition rootOrigin(Iterable<MineSegment> allSegments, MineSegment fallback) {
        for (MineSegment candidate : allSegments) {
            if (candidate.mineId().equals(fallback.mineId()) && candidate.parentId() == null) return candidate.start();
        }
        return fallback.start();
    }

    private void save(World world) {
        UUID worldId = world.getWorldConfig().getUuid();
        persistence.save(
            world,
            new ArrayList<>(worlds.getOrDefault(worldId, Map.of()).values()),
            new ArrayList<>(networks.getOrDefault(worldId, Map.of()).values())
        );
    }
}
