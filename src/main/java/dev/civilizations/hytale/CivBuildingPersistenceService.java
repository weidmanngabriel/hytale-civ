package dev.civilizations.hytale;

import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
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
 * stable building id, placement transform and the terrain snapshot required for demolition.
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
            floor.append(position.x()).append(',').append(position.y()).append(',')
                .append(position.z()).append(',').append(blockId);
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
        var fp = placement.footprint();
        var b = building.bounds();
        return String.join("|",
            building.id().toString(),
            b64(building.buildingType() == null ? "" : building.buildingType()),
            b64(building.boundsVolumeId()),
            placement.definition().id(),
            placement.anchor().x + "," + placement.anchor().y + "," + placement.anchor().z,
            fp.minX() + "," + fp.minZ() + "," + fp.maxX() + "," + fp.maxZ() + "," + fp.floorY(),
            b.minX() + "," + b.minY() + "," + b.minZ() + "," + b.maxX() + "," + b.maxY() + "," + b.maxZ(),
            b64(floor.toString()),
            b64(markers.toString())
        );
    }

    private BuildingPlacementRegistry.BuildingInstance decode(UUID worldId, String encoded) {
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 9) {
            throw new IllegalArgumentException("unexpected field count");
        }
        UUID id = UUID.fromString(parts[0]);
        String buildingType = unb64(parts[1]);
        String boundsVolumeId = unb64(parts[2]);
        PrefabPlacementService.PlacementDefinition definition = switch (parts[3]) {
            case "farm" -> PrefabPlacementService.FARM;
            case "wheat_field" -> PrefabPlacementService.WHEAT_FIELD;
            default -> throw new IllegalArgumentException("unknown prefab " + parts[3]);
        };
        int[] anchor = ints(parts[4], 3);
        int[] fp = ints(parts[5], 5);
        double[] bounds = doubles(parts[6], 6);

        Map<BlockPosition, Integer> floor = new LinkedHashMap<>();
        String floorText = unb64(parts[7]);
        if (!floorText.isEmpty()) {
            for (String entry : floorText.split(";")) {
                int[] values = ints(entry, 4);
                floor.put(new BlockPosition(values[0], values[1], values[2]), values[3]);
            }
        }

        List<PrefabPlacementService.PlacedMarker> markers = new ArrayList<>();
        String markerText = unb64(parts[8]);
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

        PrefabPlacementService.PlacementCandidate placement =
            PrefabPlacementService.PlacementCandidate.valid(
                definition,
                new Vector3i(anchor[0], anchor[1], anchor[2]),
                new PrefabPlacementService.PlacementFootprint(fp[0], fp[1], fp[2], fp[3], fp[4]),
                floor
            );
        return new BuildingPlacementRegistry.BuildingInstance(
            id,
            worldId,
            buildingType,
            boundsVolumeId,
            new BuildingBounds(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]),
            markers,
            placement
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
