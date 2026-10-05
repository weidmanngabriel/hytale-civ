package dev.civilizations.hytale;

import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MineTuning;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persists Civ-owned mine tunnel topology and progress in the world entity store. */
public final class CivMinePersistenceService {

    private final ResourceType<EntityStore, CivMineDataResource> resourceType;

    public CivMinePersistenceService(ResourceType<EntityStore, CivMineDataResource> resourceType) {
        this.resourceType = resourceType;
    }

    public List<MineSegment> load(World world) {
        List<MineSegment> result = new ArrayList<>();
        for (String encoded : resource(world).segments()) {
            try {
                result.add(decode(encoded));
            } catch (RuntimeException exception) {
                System.err.println("[Civ Mine] Ignoring invalid persisted segment: " + exception.getMessage());
            }
        }
        return List.copyOf(result);
    }

    /**
     * Stages the latest mine topology/progress in the native world resource.
     *
     * <p>Mine progress changes frequently while workers excavate. Calling Store.saveAllResources()
     * here would start an asynchronous save for every world resource on every segment update. Those
     * global saves can overlap and race on Hytale's shared *.tmp resource files. The normal Hytale
     * autosave and store shutdown lifecycle persist this staged resource instead.</p>
     */
    public void save(World world, List<MineSegment> segments) {
        resource(world).setSegments(segments.stream().map(this::encode).toArray(String[]::new));
    }

    private CivMineDataResource resource(World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        return store.getResource(resourceType);
    }

    String encode(MineSegment segment) {
        BlockPosition start = segment.start();
        return String.join("|",
            segment.id().toString(),
            segment.mineId().toString(),
            segment.parentId() == null ? "" : segment.parentId().toString(),
            start.x() + "," + start.y() + "," + start.z(),
            segment.direction().name(),
            segment.status().name(),
            Integer.toString(segment.nextBlockIndex()),
            Integer.toString(segment.supportsPlaced()),
            Integer.toString(segment.lengthBlocks())
        );
    }

    MineSegment decode(String encoded) {
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 8 && parts.length != 9) {
            throw new IllegalArgumentException("unexpected field count");
        }
        String[] position = parts[3].split(",", -1);
        if (position.length != 3) {
            throw new IllegalArgumentException("invalid start position");
        }
        int lengthBlocks = parts.length == 9
            ? Integer.parseInt(parts[8])
            : MineTuning.REFERENCE_SEGMENT_LENGTH_BLOCKS;
        return new MineSegment(
            UUID.fromString(parts[0]),
            UUID.fromString(parts[1]),
            parts[2].isBlank() ? null : UUID.fromString(parts[2]),
            new BlockPosition(
                Integer.parseInt(position[0]),
                Integer.parseInt(position[1]),
                Integer.parseInt(position[2])
            ),
            MineDirection.valueOf(parts[4]),
            lengthBlocks,
            MineSegment.Status.valueOf(parts[5]),
            Integer.parseInt(parts[6]),
            Integer.parseInt(parts[7])
        );
    }
}
