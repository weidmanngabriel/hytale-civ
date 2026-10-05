package dev.civilizations.hytale;

import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime authority for active construction sites and already materialized layer progress.
 *
 * <p>The worker executing a site is deliberately not the owner of construction progress. A worker
 * may disappear, change profession or be replaced without resetting the site.</p>
 */
public final class ConstructionSiteRegistry {

    private final Map<UUID, SiteState> sites = new ConcurrentHashMap<>();

    public SiteState register(PrefabPlacementService.ConstructionSite site) {
        return register(site, 0);
    }

    public SiteState register(PrefabPlacementService.ConstructionSite site, int completedLayers) {
        if (site == null) {
            throw new IllegalArgumentException("Construction site cannot be null.");
        }
        SiteState state = new SiteState(site, Math.max(0, completedLayers));
        sites.put(site.id(), state);
        return state;
    }

    public void restoreWorld(UUID worldId, Collection<PersistedSite> restored) {
        if (worldId == null) return;
        sites.entrySet().removeIf(entry -> worldId.equals(entry.getValue().site().worldId()));
        if (restored == null) return;
        for (PersistedSite persisted : restored) {
            if (persisted == null || persisted.site() == null) continue;
            if (!worldId.equals(persisted.site().worldId())) continue;
            register(persisted.site(), persisted.completedLayers());
        }
    }

    public SiteState get(UUID siteId) {
        return siteId == null ? null : sites.get(siteId);
    }

    public SiteState remove(UUID siteId) {
        return siteId == null ? null : sites.remove(siteId);
    }

    public List<SiteState> states() {
        return List.copyOf(sites.values());
    }

    public List<SiteState> states(UUID worldId) {
        if (worldId == null) return List.of();
        return sites.values().stream()
            .filter(state -> worldId.equals(state.site().worldId()))
            .toList();
    }

    public List<PrefabPlacementService.ConstructionSite> sites() {
        return sites.values().stream().map(SiteState::site).toList();
    }

    public SiteState findAt(UUID worldId, Vector3i block) {
        if (worldId == null || block == null) return null;
        for (SiteState state : sites.values()) {
            PrefabPlacementService.ConstructionSite site = state.site();
            if (!worldId.equals(site.worldId())) continue;
            PrefabPlacementService.PlacementFootprint footprint = site.candidate().footprint();
            if (footprint == null) continue;
            if (block.x >= footprint.minX() && block.x <= footprint.maxX()
                && block.z >= footprint.minZ() && block.z <= footprint.maxZ()) {
                return state;
            }
        }
        return null;
    }

    public List<SiteState> overlapping(
        UUID worldId,
        PrefabPlacementService.PlacementFootprint footprint,
        UUID exceptSiteId
    ) {
        if (worldId == null || footprint == null) return List.of();
        List<SiteState> result = new ArrayList<>();
        for (SiteState state : sites.values()) {
            PrefabPlacementService.ConstructionSite site = state.site();
            if (!worldId.equals(site.worldId())) continue;
            if (exceptSiteId != null && exceptSiteId.equals(site.id())) continue;
            if (site.candidate().footprint().overlaps(footprint)) result.add(state);
        }
        return List.copyOf(result);
    }

    public static final class SiteState {
        private final PrefabPlacementService.ConstructionSite site;
        private volatile int completedLayers;

        private SiteState(PrefabPlacementService.ConstructionSite site, int completedLayers) {
            this.site = site;
            this.completedLayers = completedLayers;
        }

        public PrefabPlacementService.ConstructionSite site() {
            return site;
        }

        public int completedLayers() {
            return completedLayers;
        }

        public synchronized int advanceCompletedLayers(int newlyCompleted, int totalLayers) {
            if (newlyCompleted <= 0) return completedLayers;
            completedLayers = Math.min(Math.max(0, totalLayers), completedLayers + newlyCompleted);
            return completedLayers;
        }

        public synchronized void setCompletedLayers(int completedLayers, int totalLayers) {
            this.completedLayers = Math.min(Math.max(0, totalLayers), Math.max(0, completedLayers));
        }
    }

    public record PersistedSite(
        PrefabPlacementService.ConstructionSite site,
        int completedLayers
    ) {
    }
}
