package dev.civilizations.hytale;

import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineNavigationAnchor;
import dev.civilizations.core.MineNetwork;
import dev.civilizations.core.MineRoom;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MineTunnel;
import dev.civilizations.core.MineTuning;
import dev.civilizations.core.MineWorkFront;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persists Civ-owned mine topology and current excavation progress in the world entity store. */
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

    public List<MineNetwork> loadNetworks(World world) {
        List<MineNetwork> result = new ArrayList<>();
        for (String encoded : resource(world).networks()) {
            try {
                result.add(decodeNetwork(encoded));
            } catch (RuntimeException exception) {
                System.err.println("[Civ Mine] Ignoring invalid persisted network: " + exception.getMessage());
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

    /** Stages segments and semantic mine-network metadata without forcing a global resource flush. */
    public void save(World world, List<MineSegment> segments, List<MineNetwork> networks) {
        CivMineDataResource resource = resource(world);
        resource.setSegments(segments.stream().map(this::encode).toArray(String[]::new));
        resource.setNetworks(networks.stream().map(this::encodeNetwork).toArray(String[]::new));
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
        BlockPosition start = decodePosition(parts[3]);
        int lengthBlocks = parts.length == 9
            ? Integer.parseInt(parts[8])
            : MineTuning.REFERENCE_SEGMENT_LENGTH_BLOCKS;
        return new MineSegment(
            UUID.fromString(parts[0]),
            UUID.fromString(parts[1]),
            parts[2].isBlank() ? null : UUID.fromString(parts[2]),
            start,
            MineDirection.valueOf(parts[4]),
            lengthBlocks,
            MineSegment.Status.valueOf(parts[5]),
            Integer.parseInt(parts[6]),
            Integer.parseInt(parts[7])
        );
    }

    String encodeNetwork(MineNetwork network) {
        List<String> lines = new ArrayList<>();
        lines.add("N|" + network.mineId() + "|" + network.mainTunnelId());
        for (MineTunnel tunnel : network.tunnels()) {
            lines.add(String.join("|",
                "T",
                tunnel.id().toString(),
                tunnel.kind().name(),
                tunnel.parentTunnelId() == null ? "" : tunnel.parentTunnelId().toString(),
                Integer.toString(tunnel.branchDepth()),
                encodePosition(tunnel.origin()),
                encodeIds(tunnel.segmentIds())
            ));
        }
        for (MineRoom room : network.rooms()) {
            lines.add(String.join("|", "R", room.id().toString(), room.tunnelId().toString(),
                room.type().name(), encodePosition(room.position())));
        }
        for (MineWorkFront front : network.workFronts()) {
            lines.add(String.join("|", "W", front.id().toString(), front.tunnelId().toString(),
                front.state().name(), encodePosition(front.position())));
        }
        for (MineNavigationAnchor anchor : network.navigationAnchors()) {
            lines.add(String.join("|", "A", anchor.id().toString(), anchor.tunnelId().toString(),
                anchor.type().name(), encodePosition(anchor.position()), encodeIds(anchor.connectedAnchorIds())));
        }
        return String.join("\n", lines);
    }

    MineNetwork decodeNetwork(String encoded) {
        String[] lines = encoded.split("\\n");
        if (lines.length == 0) throw new IllegalArgumentException("empty network");
        String[] header = lines[0].split("\\|", -1);
        if (header.length != 3 || !"N".equals(header[0])) throw new IllegalArgumentException("invalid network header");

        UUID mineId = UUID.fromString(header[1]);
        UUID mainTunnelId = UUID.fromString(header[2]);
        List<MineTunnel> tunnels = new ArrayList<>();
        List<MineRoom> rooms = new ArrayList<>();
        List<MineWorkFront> fronts = new ArrayList<>();
        List<MineNavigationAnchor> anchors = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String[] parts = lines[i].split("\\|", -1);
            switch (parts[0]) {
                case "T" -> {
                    requireFieldCount(parts, 7);
                    tunnels.add(new MineTunnel(
                        UUID.fromString(parts[1]),
                        MineTunnel.Kind.valueOf(parts[2]),
                        parts[3].isBlank() ? null : UUID.fromString(parts[3]),
                        Integer.parseInt(parts[4]),
                        decodePosition(parts[5]),
                        List.copyOf(decodeIds(parts[6]))
                    ));
                }
                case "R" -> {
                    requireFieldCount(parts, 5);
                    rooms.add(new MineRoom(UUID.fromString(parts[1]), UUID.fromString(parts[2]),
                        MineRoom.Type.valueOf(parts[3]), decodePosition(parts[4])));
                }
                case "W" -> {
                    requireFieldCount(parts, 5);
                    fronts.add(new MineWorkFront(UUID.fromString(parts[1]), UUID.fromString(parts[2]),
                        decodePosition(parts[4]), MineWorkFront.State.valueOf(parts[3])));
                }
                case "A" -> {
                    requireFieldCount(parts, 6);
                    anchors.add(new MineNavigationAnchor(UUID.fromString(parts[1]), UUID.fromString(parts[2]),
                        decodePosition(parts[4]), MineNavigationAnchor.Type.valueOf(parts[3]), decodeIds(parts[5])));
                }
                default -> throw new IllegalArgumentException("unknown network record: " + parts[0]);
            }
        }
        return new MineNetwork(mineId, mainTunnelId, tunnels, rooms, fronts, anchors);
    }

    private static void requireFieldCount(String[] fields, int expected) {
        if (fields.length != expected) throw new IllegalArgumentException("unexpected network field count");
    }

    private static String encodePosition(BlockPosition position) {
        return position.x() + "," + position.y() + "," + position.z();
    }

    private static BlockPosition decodePosition(String encoded) {
        String[] position = encoded.split(",", -1);
        if (position.length != 3) throw new IllegalArgumentException("invalid position");
        return new BlockPosition(
            Integer.parseInt(position[0]), Integer.parseInt(position[1]), Integer.parseInt(position[2])
        );
    }

    private static String encodeIds(Iterable<UUID> ids) {
        List<String> encoded = new ArrayList<>();
        for (UUID id : ids) encoded.add(id.toString());
        return String.join(",", encoded);
    }

    private static Set<UUID> decodeIds(String encoded) {
        Set<UUID> result = new LinkedHashSet<>();
        if (encoded.isBlank()) return result;
        for (String value : encoded.split(",")) result.add(UUID.fromString(value));
        return result;
    }
}
