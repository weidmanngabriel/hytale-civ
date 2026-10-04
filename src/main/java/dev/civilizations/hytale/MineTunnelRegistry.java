package dev.civilizations.hytale;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineSegment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime authority for persisted and reserved Civ mine tunnel segments. */
public final class MineTunnelRegistry {

    private final CivMinePersistenceService persistence;
    private final Map<UUID, Map<UUID, MineSegment>> worlds = new ConcurrentHashMap<>();

    public MineTunnelRegistry(CivMinePersistenceService persistence) {
        this.persistence = persistence;
    }

    public synchronized void loadWorld(World world) {
        UUID worldId = world.getWorldConfig().getUuid();
        Map<UUID, MineSegment> loaded = new ConcurrentHashMap<>();
        for (MineSegment segment : persistence.load(world)) {
            loaded.put(segment.id(), segment);
        }
        worlds.put(worldId, loaded);
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

    public synchronized void put(World world, MineSegment segment) {
        UUID worldId = world.getWorldConfig().getUuid();
        worlds.computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>()).put(segment.id(), segment);
        save(world);
    }

    public synchronized void removeMine(World world, UUID mineId) {
        Map<UUID, MineSegment> segments = worlds.get(world.getWorldConfig().getUuid());
        if (segments == null) return;
        segments.values().removeIf(segment -> segment.mineId().equals(mineId));
        save(world);
    }

    /**
     * Rejects overlap with every reserved/completed segment. Main tunnel and fixed junction are
     * both part of {@link MineSegment#blocks()}, so parent and child segments must be adjacent,
     * never overlapping.
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

    private void save(World world) {
        persistence.save(world, new ArrayList<>(
            worlds.getOrDefault(world.getWorldConfig().getUuid(), Map.of()).values()
        ));
    }
}
