package dev.civilizations.simulation.local;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.simulation.prefab.PrefabSimulationLoader;
import dev.civilizations.simulation.prefab.PrefabSimulationModel;
import dev.civilizations.simulation.world.VoxelWorld;
import dev.civilizations.simulation.world.WorldArchive;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Places authored Civ mine blocks and translates its existing trigger markers into world coordinates. */
public final class MineSandboxPrefab {
    public static final Path DEFAULT_PREFAB =
        Path.of("asset-pack/Server/Prefabs/Civilizations/Mine/Mine_01.prefab.json");

    private final PrefabSimulationModel prefab;

    public MineSandboxPrefab(Path source) throws IOException {
        prefab = new PrefabSimulationLoader().load(source);
        prefab.requireMarker("workplace_access");
        prefab.requireMarker("mine_tunnel_connector");
    }

    public PrefabSimulationModel model() { return prefab; }

    public Placement place(VoxelWorld world, WorldArchive.Bounds region, BlockPosition origin) {
        var bounds = prefab.blockBounds();
        if (!region.contains(origin.x()+bounds.minX(),origin.y()+bounds.minY(),origin.z()+bounds.minZ())
            || !region.contains(origin.x()+bounds.maxX(),origin.y()+bounds.maxY(),origin.z()+bounds.maxZ()))
            throw new IllegalArgumentException("Prefab would extend beyond imported world; choose a different anchor.");
        for (var cell : prefab.cells().entrySet()) {
            BlockPosition local=cell.getKey();
            BlockPosition actual=new BlockPosition(origin.x()+local.x(),origin.y()+local.y(),origin.z()+local.z());
            WorldArchive.Material material=cell.getValue()==PrefabSimulationModel.Cell.DOOR
                ? WorldArchive.Material.AIR : WorldArchive.Material.SOLID;
            if (world.material(actual)!=material) world.set(actual,material);
        }
        // The authored prefab uses explicit Empty cells to clear its footprint.
        // This reduced loader only retains nonempty blocks, so navigation through pre-existing
        // terrain is not guaranteed until empty-cell placement is supported explicitly.
        List<Marker> markers=prefab.markers().stream().map(m->new Marker(m.name(),m.type(),
            new Box(m.bounds().minX()+origin.x(),m.bounds().minY()+origin.y(),m.bounds().minZ()+origin.z(),
                m.bounds().maxX()+origin.x(),m.bounds().maxY()+origin.y(),m.bounds().maxZ()+origin.z()))).toList();
        BlockPosition access=standableAtMarker(world, require(markers,"workplace_access"));
        BlockPosition connector=standableAtMarker(world, require(markers,"mine_tunnel_connector"));
        return new Placement(origin,access,connector,markers);
    }

    private static Marker require(List<Marker> markers,String type) {
        return markers.stream().filter(m->type.equals(m.type())).findFirst().orElseThrow();
    }

    private static BlockPosition standableAtMarker(VoxelWorld world,Marker marker) {
        Box b=marker.bounds();
        int x=(int)Math.floor((b.minX()+b.maxX())/2.0);
        int y=(int)Math.floor(b.minY());
        int z=(int)Math.floor((b.minZ()+b.maxZ())/2.0);
        BlockPosition result=null;
        double best=Double.POSITIVE_INFINITY;
        for(int dy=-3;dy<=4;dy++)for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
            BlockPosition p=new BlockPosition(x+dx,y+dy,z+dz);
            if(!world.canStand(p))continue;
            double dist=dx*dx+dz*dz+dy*dy*1.5;
            if(dist<best){best=dist;result=p;}
        }
        if(result==null)throw new IllegalArgumentException("No two-block-clear standable point near prefab "+marker.type());
        return result;
    }

    public record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {}
    public record Marker(String name,String type,Box bounds) {}
    public record Placement(BlockPosition origin,BlockPosition access,BlockPosition connector,List<Marker> markers) {}
}
