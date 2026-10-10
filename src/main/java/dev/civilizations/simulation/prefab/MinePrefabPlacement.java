package dev.civilizations.simulation.prefab;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineHeading;
import dev.civilizations.simulation.world.VoxelWorld;
import dev.civilizations.simulation.world.WorldArchive;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Places the authored Mine_01 block snapshot into an imported sandbox voxel region.
 * Hytale entities/physics are not emulated; semantic markers are read from the same prefab JSON.
 */
public final class MinePrefabPlacement {
    public static final String ASSET = "asset-pack/Server/Prefabs/Civilizations/Mine/Mine_01.prefab.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private MinePrefabPlacement() {}

    public record Marker(String name, String type, PrefabSimulationModel.Box bounds) {}
    public record Placement(BlockPosition origin, BlockPosition access, BlockPosition connector,
                            MineHeading heading, int changedBlocks, List<Marker> markers) {}

    public static Placement place(VoxelWorld world, Path prefab, BlockPosition origin) throws IOException {
        var model = new PrefabSimulationLoader().load(prefab);
        var tree = JSON.readTree(prefab.toFile());
        var changes = new ArrayList<Edit>();
        // Validate ALL positions first: an out-of-bounds prefab must never partially modify the region.
        for (var block : tree.path("blocks")) {
            var p = new BlockPosition(origin.x() + block.path("x").asInt() - model.anchorX(),
                origin.y() + block.path("y").asInt() - model.anchorY(),
                origin.z() + block.path("z").asInt() - model.anchorZ());
            if (world.material(p) == null) {
                throw new IllegalArgumentException("Prefab extends outside exported region near " + p);
            }
            var name = block.path("name").asText("");
            var value = name.equalsIgnoreCase("Empty") || name.toLowerCase().contains("door")
                ? WorldArchive.Material.AIR : WorldArchive.Material.SOLID;
            if (world.material(p) != value) changes.add(new Edit(p,value));
        }
        for (var edit : changes) world.set(edit.position(),edit.material());

        var markers = new ArrayList<Marker>();
        for (var marker : model.markers()) {
            var b = marker.bounds();
            markers.add(new Marker(marker.name(),marker.type(),new PrefabSimulationModel.Box(
                b.minX()+origin.x()-model.anchorX(),b.minY()+origin.y()-model.anchorY(),
                b.minZ()+origin.z()-model.anchorZ(),b.maxX()+origin.x()-model.anchorX(),
                b.maxY()+origin.y()-model.anchorY(),b.maxZ()+origin.z()-model.anchorZ())));
        }
        var access = markerFeet(world, markers,"workplace_access");
        var connector = markerFeet(world, markers,"mine_tunnel_connector");
        MineHeading heading = null;
        for (var entity : tree.path("entities")) {
            var tags = entity.path("Components").path("TriggerVolume").path("Tags");
            if ("mine_tunnel_connector".equals(tags.path("civ.type").asText(""))) {
                var direction = tags.path("civ.direction").asText("").toUpperCase(java.util.Locale.ROOT);
                try { heading = MineHeading.valueOf(direction); }
                catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Unsupported authored mine heading: " + direction,ex);
                }
                break;
            }
        }
        if (heading == null) throw new IllegalArgumentException("Missing authored connector heading");
        return new Placement(origin,access,connector,heading,changes.size(),List.copyOf(markers));
    }

    private static BlockPosition markerFeet(VoxelWorld world,List<Marker> markers,String type) {
        var b = markers.stream().filter(m -> m.type().equals(type))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("Missing prefab marker "+type)).bounds();
        var choices = new ArrayList<BlockPosition>();
        for (int x=(int)Math.floor(b.minX()); x<=Math.ceil(b.maxX()); x++)
            for (int y=(int)Math.floor(b.minY()); y<=Math.ceil(b.maxY()); y++)
                for (int z=(int)Math.floor(b.minZ()); z<=Math.ceil(b.maxZ()); z++) {
                    var p = new BlockPosition(x,y,z);
                    if (world.canStand(p)) choices.add(p);
                }
        return choices.stream().min(Comparator
            .comparingDouble((BlockPosition p)->Math.pow(p.x()+0.5-b.centerX(),2)
                +Math.pow(p.z()+0.5-b.centerZ(),2))
            .thenComparingInt(p->Math.abs(p.y()-(int)Math.round(b.minY())))
            .thenComparingInt(BlockPosition::x).thenComparingInt(BlockPosition::z))
            .orElseThrow(() -> new IllegalArgumentException(
                "No standable position inside authored "+type+" marker; check prefab and placement height"));
    }

    private record Edit(BlockPosition position, WorldArchive.Material material) {}
}
