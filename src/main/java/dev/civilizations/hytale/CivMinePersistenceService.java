package dev.civilizations.hytale;

import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineNavigationAnchor;
import dev.civilizations.core.MineNetwork;
import dev.civilizations.core.MineRoom;
import dev.civilizations.core.MineTunnel;
import dev.civilizations.core.MineWorkFront;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persists Civ-owned semantic mine-network state in the world entity store. */
public final class CivMinePersistenceService {

    private static final String FORMAT_HEADER = "N2";

    private final ResourceType<EntityStore, CivMineDataResource> resourceType;

    public CivMinePersistenceService(ResourceType<EntityStore, CivMineDataResource> resourceType) {
        this.resourceType = resourceType;
    }

    public List<MineNetwork> loadNetworks(World world) {
        List<MineNetwork> result = new ArrayList<>();
        for (String encoded : resource(world).networks()) {
            try {
                result.add(decodeNetwork(encoded));
            } catch (RuntimeException exception) {
                System.err.println("[Civ Mine] Ignoring incompatible persisted mine network: " + exception.getMessage());
            }
        }
        return List.copyOf(result);
    }

    /**
     * Stages current semantic mine state in the native world resource without forcing a global
     * resource flush. Hytale autosave and store shutdown remain responsible for durable writes.
     */
    public void save(World world, List<MineNetwork> networks) {
        resource(world).setNetworks(networks.stream().map(this::encodeNetwork).toArray(String[]::new));
    }

    private CivMineDataResource resource(World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        return store.getResource(resourceType);
    }

    String encodeNetwork(MineNetwork network) {
        List<String> lines = new ArrayList<>();
        lines.add(FORMAT_HEADER + "|" + network.mineId() + "|" + network.mainTunnelId());
        for (MineTunnel tunnel : network.tunnels()) {
            lines.add(String.join("|",
                "T",
                tunnel.id().toString(),
                tunnel.kind().name(),
                tunnel.parentTunnelId() == null ? "" : tunnel.parentTunnelId().toString(),
                Integer.toString(tunnel.branchDepth()),
                encodePosition(tunnel.origin())
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
        if (header.length != 3 || !FORMAT_HEADER.equals(header[0])) {
            throw new IllegalArgumentException("unsupported mine persistence format");
        }

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
                    requireFieldCount(parts, 6);
                    tunnels.add(new MineTunnel(
                        UUID.fromString(parts[1]),
                        MineTunnel.Kind.valueOf(parts[2]),
                        parts[3].isBlank() ? null : UUID.fromString(parts[3]),
                        Integer.parseInt(parts[4]),
                        decodePosition(parts[5])
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
