package dev.civilizations.hytale;

import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingOrientation;
import org.joml.Vector3i;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persists confirmed Civ construction sites independently from the player session that created them. */
public final class CivConstructionPersistenceService {

    private final ResourceType<EntityStore, CivConstructionDataResource> resourceType;

    public CivConstructionPersistenceService(
        ResourceType<EntityStore, CivConstructionDataResource> resourceType
    ) {
        this.resourceType = resourceType;
    }

    public List<ConstructionSiteRegistry.PersistedSite> load(World world) {
        CivConstructionDataResource resource = resource(world);
        Map<UUID, Integer> progress = decodeProgress(resource.progress());
        List<ConstructionSiteRegistry.PersistedSite> result = new ArrayList<>();
        for (String encoded : resource.sites()) {
            try {
                PrefabPlacementService.ConstructionSite site = decodeSite(
                    world.getWorldConfig().getUuid(), encoded
                );
                result.add(new ConstructionSiteRegistry.PersistedSite(
                    site,
                    Math.max(0, progress.getOrDefault(site.id(), 0))
                ));
            } catch (RuntimeException exception) {
                System.err.println(
                    "[Civ Construction] Ignoring invalid persisted site: " + exception.getMessage()
                );
            }
        }
        return List.copyOf(result);
    }

    /**
     * Writes the comparatively large static site snapshots and explicitly flushes the world resource.
     * This is called only when sites are created, completed or cancelled, not per construction tick.
     */
    public void saveSites(World world, List<ConstructionSiteRegistry.SiteState> states) {
        CivConstructionDataResource resource = resource(world);
        List<ConstructionSiteRegistry.SiteState> safe = states == null ? List.of() : states;
        resource.setSites(safe.stream().map(state -> encodeSite(state.site())).toArray(String[]::new));
        resource.setProgress(safe.stream()
            .map(state -> state.site().id() + "=" + state.completedLayers())
            .toArray(String[]::new));
        world.getEntityStore().getStore().saveAllResources();
    }

    /**
     * Updates only the tiny progress array in memory. Normal Hytale world/resource saving persists it;
     * Civ deliberately avoids forcing a full resource flush for every materialized layer.
     */
    public void stageProgress(World world, UUID siteId, int completedLayers) {
        if (world == null || siteId == null) return;
        CivConstructionDataResource resource = resource(world);
        Map<UUID, Integer> progress = decodeProgress(resource.progress());
        progress.put(siteId, Math.max(0, completedLayers));
        resource.setProgress(progress.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .toArray(String[]::new));
    }

    private CivConstructionDataResource resource(World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        return store.getResource(resourceType);
    }

    private static Map<UUID, Integer> decodeProgress(String[] entries) {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        if (entries == null) return result;
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;
            int separator = entry.indexOf('=');
            if (separator <= 0) continue;
            try {
                result.put(
                    UUID.fromString(entry.substring(0, separator)),
                    Integer.parseInt(entry.substring(separator + 1))
                );
            } catch (RuntimeException ignored) {
                // A broken progress hint must not make the static construction-site snapshot unloadable.
            }
        }
        return result;
    }

    private static String encodeSite(PrefabPlacementService.ConstructionSite site) {
        PrefabPlacementService.PlacementCandidate placement = site.candidate();
        StringBuilder terrain = new StringBuilder();
        placement.terrainSnapshot().forEach((position, snapshot) -> {
            if (!terrain.isEmpty()) terrain.append(';');
            BlockType blockType = BlockType.getAssetMap().getAsset(snapshot.blockId());
            if (blockType == null) {
                throw new IllegalStateException("Unknown terrain block id " + snapshot.blockId());
            }
            terrain.append(position.x()).append(',').append(position.y()).append(',')
                .append(position.z()).append(',').append(b64(blockType.getId())).append(',')
                .append(snapshot.rotation()).append(',')
                .append(snapshot.filler()).append(',')
                .append(snapshot.supportValue()).append(',')
                .append(snapshot.fluidId()).append(',')
                .append(snapshot.fluidLevel());
        });
        PrefabPlacementService.PlacementFootprint fp = placement.footprint();
        UUID upgradeId = site.upgradeBuildingId();
        return String.join("|",
            site.id().toString(),
            site.ownerId().toString(),
            definitionToken(site.definition()),
            placement.anchor().x + "," + placement.anchor().y + "," + placement.anchor().z,
            fp.minX() + "," + fp.minZ() + "," + fp.maxX() + "," + fp.maxZ() + "," + fp.floorY(),
            placement.orientation().name(),
            upgradeId == null ? "" : upgradeId.toString(),
            Integer.toString(site.targetPhase()),
            b64(terrain.toString())
        );
    }

    private static PrefabPlacementService.ConstructionSite decodeSite(UUID worldId, String encoded) {
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 9) {
            throw new IllegalArgumentException("unexpected construction-site field count");
        }
        UUID siteId = UUID.fromString(parts[0]);
        UUID ownerId = UUID.fromString(parts[1]);
        PrefabPlacementService.PlacementDefinition definition = definition(parts[2]);
        int[] anchor = ints(parts[3], 3);
        int[] fp = ints(parts[4], 5);
        BuildingOrientation orientation = BuildingOrientation.valueOf(parts[5]);
        UUID upgradeId = parts[6].isBlank() ? null : UUID.fromString(parts[6]);
        int targetPhase = Integer.parseInt(parts[7]);

        Map<BlockPosition, PrefabPlacementService.TerrainBlockSnapshot> terrain =
            new LinkedHashMap<>();
        String terrainText = unb64(parts[8]);
        if (!terrainText.isEmpty()) {
            for (String entry : terrainText.split(";")) {
                String[] values = entry.split(",", -1);
                if (values.length != 9) {
                    throw new IllegalArgumentException("invalid construction terrain entry");
                }
                BlockPosition position = new BlockPosition(
                    Integer.parseInt(values[0]),
                    Integer.parseInt(values[1]),
                    Integer.parseInt(values[2])
                );
                String blockKey = unb64(values[3]);
                int blockId = BlockType.getAssetMap().getIndex(blockKey);
                if (blockId < 0) {
                    throw new IllegalArgumentException("unknown construction terrain block " + blockKey);
                }
                terrain.put(
                    position,
                    new PrefabPlacementService.TerrainBlockSnapshot(
                        blockId,
                        Integer.parseInt(values[4]),
                        Integer.parseInt(values[5]),
                        Integer.parseInt(values[6]),
                        Integer.parseInt(values[7]),
                        Byte.parseByte(values[8])
                    )
                );
            }
        }
        Map<BlockPosition, Integer> replaced = new LinkedHashMap<>();
        terrain.forEach((position, snapshot) -> replaced.put(position, snapshot.blockId()));
        PrefabPlacementService.PlacementCandidate candidate =
            PrefabPlacementService.PlacementCandidate.valid(
                definition,
                new Vector3i(anchor[0], anchor[1], anchor[2]),
                new PrefabPlacementService.PlacementFootprint(
                    fp[0], fp[1], fp[2], fp[3], fp[4]
                ),
                replaced,
                terrain,
                orientation
            );
        return new PrefabPlacementService.ConstructionSite(
            siteId,
            ownerId,
            worldId,
            candidate,
            null,
            upgradeId,
            targetPhase
        );
    }

    private static String definitionToken(PrefabPlacementService.PlacementDefinition definition) {
        if (PrefabPlacementService.FARM.prefabKey().equals(definition.prefabKey())) return "farm";
        if (PrefabPlacementService.MINE.prefabKey().equals(definition.prefabKey())) return "mine1";
        if (PrefabPlacementService.MINE_02.prefabKey().equals(definition.prefabKey())) return "mine2";
        if (PrefabPlacementService.MINE_03.prefabKey().equals(definition.prefabKey())) return "mine3";
        if (PrefabPlacementService.WHEAT_FIELD.prefabKey().equals(definition.prefabKey())) return "wheat";
        throw new IllegalArgumentException("Unknown construction prefab " + definition.prefabKey());
    }

    private static PrefabPlacementService.PlacementDefinition definition(String token) {
        return switch (token) {
            case "farm" -> PrefabPlacementService.FARM;
            case "mine1" -> PrefabPlacementService.MINE;
            case "mine2" -> PrefabPlacementService.MINE_02;
            case "mine3" -> PrefabPlacementService.MINE_03;
            case "wheat" -> PrefabPlacementService.WHEAT_FIELD;
            default -> throw new IllegalArgumentException("unknown construction prefab token " + token);
        };
    }

    private static int[] ints(String value, int count) {
        String[] parts = value.split(",", -1);
        if (parts.length != count) throw new IllegalArgumentException("invalid integer tuple");
        int[] result = new int[count];
        for (int i = 0; i < count; i++) result[i] = Integer.parseInt(parts[i]);
        return result;
    }

    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String value) {
        if (value == null || value.isEmpty()) return "";
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
