package dev.civilizations.simulation.world;

import dev.civilizations.core.BlockPosition;
import java.util.*;

/** Geometric A* for a two-block-tall test NPC on a saved voxel world. */
public final class VoxelWorld {
    private static final int[][] OFFSETS={{1,0},{-1,0},{0,1},{0,-1}};
    private final WorldArchive.Bounds bounds;
    private long revision;
    public long revision() { return revision; }
    private final Map<BlockPosition, WorldArchive.Material> cells;

    public VoxelWorld(WorldArchive archive) {
        this.bounds=archive.bounds();
        this.cells=new HashMap<>(archive.classify());
    }

    public WorldArchive.Material material(BlockPosition p) {
        return cells.get(p); // null means outside known region, not air
    }

    public void set(BlockPosition p, WorldArchive.Material material) {
        if(!bounds.contains(p.x(),p.y(),p.z()))throw new IllegalArgumentException("Outside imported region");
        cells.put(p, Objects.requireNonNull(material));
        revision++;
    }

    public boolean canStand(BlockPosition feet) {
        BlockPosition head=new BlockPosition(feet.x(),feet.y()+1,feet.z());
        BlockPosition ground=new BlockPosition(feet.x(),feet.y()-1,feet.z());
        return material(feet)==WorldArchive.Material.AIR
            && material(head)==WorldArchive.Material.AIR
            && material(ground)==WorldArchive.Material.SOLID;
    }

    /** Traversable adjacent standing cells; shared by A* and spawn-component selection. */
    public List<BlockPosition> walkableNeighbors(BlockPosition p) {
        var result = new ArrayList<BlockPosition>(4);
        if (!canStand(p)) return result;
        for (int[] offset : OFFSETS) {
            int x = p.x() + offset[0], z = p.z() + offset[1];
            for (int y : new int[]{p.y(), p.y() + 1, p.y() - 1}) {
                var n = new BlockPosition(x, y, z);
                if (!canStand(n)) continue;
                if (y > p.y() && material(new BlockPosition(p.x(), p.y() + 2, p.z())) != WorldArchive.Material.AIR) continue;
                if (y < p.y() && material(new BlockPosition(x, p.y() + 1, z)) != WorldArchive.Material.AIR) continue;
                result.add(n);
                break;
            }
        }
        return result;
    }

    public List<BlockPosition> path(BlockPosition start, BlockPosition destination) {
        if(!canStand(start)||!canStand(destination))return List.of();
        record Node(BlockPosition pos,double f) {}
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        Map<BlockPosition,Double> cost=new HashMap<>();
        Map<BlockPosition,BlockPosition> previous=new HashMap<>();
        Set<BlockPosition> closed=new HashSet<>();
        cost.put(start,0.0);open.add(new Node(start, heuristic(start,destination)));
        while(!open.isEmpty()) {
            BlockPosition p=open.remove().pos();
            if(!closed.add(p))continue;
            if(p.equals(destination)) {
                LinkedList<BlockPosition> result=new LinkedList<>();
                for(BlockPosition v=p;v!=null;v=previous.get(v))result.addFirst(v);
                return List.copyOf(result);
            }
            for (BlockPosition n : walkableNeighbors(p)) {
                double candidate = cost.get(p) + (n.y() == p.y() ? 1 : 1.25);
                if (candidate < cost.getOrDefault(n, Double.POSITIVE_INFINITY)) {
                    cost.put(n, candidate);
                    previous.put(n, p);
                    open.add(new Node(n, candidate + heuristic(n, destination)));
                }
            }
        }
        return List.of();
    }

    private static double heuristic(BlockPosition a,BlockPosition b) {
        return Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z())
            +1.25*Math.abs(a.y()-b.y());
    }
}
