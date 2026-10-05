package dev.civilizations.hytale;

import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
import dev.civilizations.core.BuildingOrientation;
import org.joml.Vector3i;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists the Civ-owned metadata that native prefab/trigger-volume persistence does not contain:
 * stable building id, type/phase, placement transform, owned prefab entities and the terrain
 * snapshot required for demolition.
 */
public final class CivBuildingPersistenceService {

    private final ResourceType<EntityStore, CivBuildingDataResource> resourceType;

    public CivBuildingPersistenceService(
        ResourceType<EntityStore, CivBuildingDataResource> resourceType
    ) {
        this.resourceType = resourceType;
    }

    public List<BuildingPlacementRegistry.BuildingInstance> load(World world) {
        CivBuildingDataResource resource = resource(world);
        List<BuildingPlacementRegistry.BuildingInstance> result = new ArrayList<>();
        for (String encoded : resource.buildings()) {
            try {
                result.add(decode(world.getWorldConfig().getUuid(), encoded));
            } catch (RuntimeException exception) {
                System.err.println("[Civ Buildings] Ignoring invalid persisted building: "
                    + exception.getMessage());
            }
        }
        return List.copyOf(result);
    }

    public void save(World world, List<BuildingPlacementRegistry.BuildingInstance> buildings) {
        CivBuildingDataResource resource = resource(world);
        resource.setBuildings(buildings.stream().map(this::encode).toArray(String[]::new));
        world.getEntityStore().getStore().saveAllResources();
    }

    private CivBuildingDataResource resource(World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        return store.getResource(resourceType);
    }

    private String encode(BuildingPlacementRegistry.BuildingInstance building) {
        PrefabPlacementService.PlacementCandidate placement = building.placement();
        if (placement == null) {
            throw new IllegalStateException("Building has no placement snapshot.");
        }
        StringBuilder floor = new StringBuilder();
        placement.replacedFloorBlocks().forEach((position, blockId) -> {
            if (!floor.isEmpty()) floor.append(';');
            BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
            if (blockType == null) {
                throw new IllegalStateException("Unknown terrain block id " + blockId);
            }
            floor.append(position.x()).append(',').append(position.y()).append(',')
                .append(position.z()).append(',').append(b64(blockType.getId()));
        });

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

        StringBuilder markers = new StringBuilder();
        for (PrefabPlacementService.PlacedMarker marker : building.semanticVolumes()) {
            if (!markers.isEmpty()) markers.append(';');
            markers.append(b64(marker.id())).append(',')
                .append(marker.position().x).append(',').append(marker.position().y).append(',')
                .append(marker.position().z).append(',')
                .append(b64(marker.tags().getOrDefault("civ.type", ""))).append(',')
                .append(b64(marker.tags().getOrDefault("civ.building", "")));
        }
        String entityIds = building.prefabEntityIds().stream()
            .map(UUID::toString)
            .reduce((left, right) -> left + "," + right)
            .orElse("");
        var fp = placement.footprint();
        var b = building.bounds();
        return String.join("|",
            building.id().toString(),
            b64(building.buildingType() == null ? "" : building.buildingType()),
            Integer.toString(building.phase()),
            b64(building.boundsVolumeId()),
            placement.definition().id(),
            placement.anchor().x + "," + placement.anchor().y + "," + placement.anchor().z,
            fp.minX() + "," + fp.minZ() + "," + fp.maxX() + "," + fp.maxZ() + "," + fp.floorY(),
            b.minX() + "," + b.minY() + "," + b.minZ() + "," + b.maxX() + "," + b.maxY() + "," + b.maxZ(),
            b64(floor.toString()),
            b64(markers.toString()),
            placement.orientation().name(),
            b64(entityIds),
            b64(terrain.toString())
        );
    }

    private BuildingPlacementRegistry.BuildingInstance decode(UUID worldId, String encoded) {
        String[] parts = encoded.split("\\|", -1);
        boolean legacy = parts.length == 9;
        if (!legacy && parts.length != 10 && parts.length != 11
            && parts.length != 12 && parts.length != 13) {
            throw new IllegalArgumentException("unexpected field count");
        }

        UUID id = UUID.fromString(parts[0]);
        String buildingType = unb64(parts[1]);
        int phase = legacy ? 1 : Integer.parseInt(parts[2]);
        int offset = legacy ? 0 : 1;
        String boundsVolumeId = unb64(parts[2 + offset]);
        PrefabPlacementService.PlacementDefinition definition = switch (parts[3 + offset]) {
            case "farm" -> PrefabPlacementService.FARM;
            case "mine" -> PrefabPlacementService.minePhase(phase);
            case "wheat_field" -> PrefabPlacementService.WHEAT_FIELD;
            default -> throw new IllegalArgumentException("unknown prefab " + parts[3 + offset]);
        };
        int[] anchor = ints(parts[4 + offset], 3);
        int[] fp = ints(parts[5 + offset], 5);
        double[] bounds = doubles(parts[6 + offset], 6);
        BuildingOrientation orientation = parts.length >= 11
            ? BuildingOrientation.valueOf(parts[10])
            : BuildingOrientation.NORTH;

        Map<BlockPosition, Integer> floor = new LinkedHashMap<>();
        String floorText = unb64(parts[7 + offset]);
        if (!floorText.isEmpty()) {
            for (String entry : floorText.split(";")) {
                String[] values = entry.split(",", -1);
                if (values.length != 4) {
                    throw new IllegalArgumentException("invalid terrain snapshot entry");
                }
                int x = Integer.parseInt(values[0]);
                int y = Integer.parseInt(values[1]);
                int z = Integer.parseInt(values[2]);
                int blockId;
                try {
                    blockId = Integer.parseInt(values[3]);
                } catch (NumberFormatException ignored) {
                    String blockKey = unb64(values[3]);
                    blockId = BlockType.getAssetMap().getIndex(blockKey);
                    if (blockId < 0) {
                        throw new IllegalArgumentException("unknown terrain block " + blockKey);
                    }
                }
                floor.put(new BlockPosition(x, y, z), blockId);
            }
        }

        Map<BlockPosition, PrefabPlacementService.TerrainBlockSnapshot> terrain =
            new LinkedHashMap<>();
        floor.forEach((position, blockId) ->
            terrain.put(position, PrefabPlacementService.TerrainBlockSnapshot.legacy(blockId))
        );
        if (parts.length >= 13) {
            String terrainText = unb64(parts[12]);
            if (!terrainText.isEmpty()) {
                for (String entry : terrainText.split(";")) {
                    String[] values = entry.split(",", -1);
                    if (values.length != 9) {
                        throw new IllegalArgumentException("invalid native terrain snapshot entry");
                    }
                    int x = Integer.parseInt(values[0]);
                    int y = Integer.parseInt(values[1]);
                    int z = Integer.parseInt(values[2]);
                    String blockKey = unb64(values[3]);
                    int blockId = BlockType.getAssetMap().getIndex(blockKey);
                    if (blockId < 0) {
                        throw new IllegalArgumentException("unknown terrain block " + blockKey);
                    }
                    terrain.put(
                        new BlockPosition(x, y, z),
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
        }

        List<PrefabPlacementService.PlacedMarker> markers = new ArrayList<>();
        String markerText = unb64(parts[8 + offset]);
        if (!markerText.isEmpty()) {
            for (String entry : markerText.split(";")) {
                String[] values = entry.split(",", -1);
                if (values.length != 6) continue;
                Map<String, String> tags = new LinkedHashMap<>();
                String type = unb64(values[4]);
                String building = unb64(values[5]);
                if (!type.isEmpty()) tags.put("civ.type", type);
                if (!building.isEmpty()) tags.put("civ.building", building);
                markers.add(new PrefabPlacementService.PlacedMarker(
                    unb64(values[0]),
                    new Vector3i(Integer.parseInt(values[1]), Integer.parseInt(values[2]), Integer.parseInt(values[3])),
                    tags,
                    null
                ));
            }
        }

        List<UUID> prefabEntityIds = new ArrayList<>();
        if (parts.length >= 12) {
            String entityText = unb64(parts[11]);
            if (!entityText.isBlank()) {
                for (String entityId : entityText.split(",")) {
                    prefabEntityIds.add(UUID.fromString(entityId));
                }
            }
        }

        PrefabPlacementService.PlacementCandidate placement =
            PrefabPlacementService.PlacementCandidate.valid(
                definition,
                new Vector3i(anchor[0], anchor[1], anchor[2]),
                new PrefabPlacementService.PlacementFootprint(fp[0], fp[1], fp[2], fp[3], fp[4]),
                floor,
                terrain,
                orientation
            );
        return new BuildingPlacementRegistry.BuildingInstance(
            id,
            worldId,
            buildingType,
            phase,
            boundsVolumeId,
            new BuildingBounds(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]),
            markers,
            orientation,
            placement,
            prefabEntityIds
        );
    }

    private static int[] ints(String value, int count) {
        String[] parts = value.split(",", -1);
        if (parts.length != count) throw new IllegalArgumentException("invalid integer tuple");
        int[] result = new int[count];
        for (int i = 0; i < count; i++) result[i] = Integer.parseInt(parts[i]);
        return result;
    }

    private static double[] doubles(String value, int count) {
        String[] parts = value.split(",", -1);
        if (parts.length != count) throw new IllegalArgumentException("invalid double tuple");
        double[] result = new double[count];
        for (int i = 0; i < count; i++) result[i] = Double.parseDouble(parts[i]);
        return result;
    }

    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String value) {
        if (value.isEmpty()) return "";
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
