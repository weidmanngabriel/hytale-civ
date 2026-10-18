package dev.civilizations.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.ForesterPlantingPlanner;
import dev.civilizations.core.ForesterWorkController;
import dev.civilizations.core.Profession;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Executes Core-selected forester work on Hytale's loaded world thread.
 * The original item stacks are read only; sapling species are selected without consumption.
 */
public final class ForesterWorkSystem extends DelayedEntitySystem<EntityStore> {
    private static final float INTERVAL = 0.75f;
    private static final int RADIUS = ForesterPlantingPlanner.PHASE_ONE_RADIUS;
    private final CivUnitRegistry units;
    private final CivActivityRegistry activities;
    private final BuildingPlacementRegistry buildings;
    private final NativeForesterSaplings saplings = new NativeForesterSaplings();
    private final ForesterWorkController<CivUnitRegistry.UnitKey> core = new ForesterWorkController<>();
    private final Map<CivUnitRegistry.UnitKey, ForesterPlantingPlanner> planners = new HashMap<>();
    private final Map<CivUnitRegistry.UnitKey, Random> varieties = new HashMap<>();

    public ForesterWorkSystem(CivUnitRegistry units, CivActivityRegistry activities,
                              BuildingPlacementRegistry buildings) {
        super(INTERVAL);
        this.units=units; this.activities=activities; this.buildings=buildings;
    }

    @Override public boolean isParallel(int archetypeChunkSize, int taskCount) { return false; }
    @Override public Query<EntityStore> getQuery() { return NPCEntity.getComponentType(); }

    @Override public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                               Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        CivUnitRegistry.UnitKey key = units.keyOf(ref);
        if (!ref.isValid() || units.getProfession(ref) != Profession.FORESTER) {
            release(key, ref);
            return;
        }
        if (!activities.autonomousWorkAllowed(ref)) {
            core.interrupt(key);
            units.clearMoveTarget(ref);
            return;
        }
        TransformComponent transform=buffer.getComponent(ref,TransformComponent.getComponentType());
        if(transform==null) return;
        World world=store.getExternalData().getWorld();
        BuildingPlacementRegistry.BuildingInstance home=workplace(ref,world,transform.getPosition());
        if(home==null) {
            release(key,ref);
            return;
        }
        Vector3i chestMarker=home.semanticVolumes().stream()
            .filter(m->m.hasTag("civ.type","output_storage"))
            .map(PrefabPlacementService.PlacedMarker::position).findFirst().orElse(null);
        if(chestMarker==null) return;
        List<String> palette=saplings.available(world,chestMarker);
        Vector3d actor=transform.getPosition();
        BlockPosition center=new BlockPosition(
            (int)Math.floor((home.bounds().minX()+home.bounds().maxX())/2.0),
            (int)Math.floor((home.bounds().minY()+home.bounds().maxY())/2.0),
            (int)Math.floor((home.bounds().minZ()+home.bounds().maxZ())/2.0));
        ForesterPlantingPlanner planner=planners.computeIfAbsent(key,k->
            new ForesterPlantingPlanner(center,RADIUS,ForesterPlantingPlanner.PHASE_ONE_SPACING,
                Integer.toUnsignedLong(k.hashCode())));
        Random variety=varieties.computeIfAbsent(key,k->new Random(Integer.toUnsignedLong(k.hashCode())^0xC0FFEEL));
        var status=core.tick(key,new ForesterWorkController.Engine(){
            @Override public int saplingsAvailable(){ return palette.size(); }
            @Override public BlockPosition selectSite(Set<BlockPosition> reserved){
                return planner.chooseSurface(
                    new BlockPosition((int)Math.floor(actor.x),(int)Math.floor(actor.y),(int)Math.floor(actor.z)),
                    Set.of(),reserved,
                    column->surface(world,column.x(),column.z(),center.y()),
                    position->eligible(world,position,home));
            }
            @Override public boolean siteStillValid(BlockPosition goal){
                return eligible(world,goal,home);
            }
            @Override public ForesterWorkController.Navigation navigate(BlockPosition goal){
                Vector3d work=new Vector3d(goal.x()+1.5,goal.y(),goal.z()+0.5);
                if (actor.distanceSquared(work)<1.75) {
                    units.clearMoveTarget(ref);
                    return ForesterWorkController.Navigation.ARRIVED;
                }
                units.setMoveTarget(ref,work);
                return ForesterWorkController.Navigation.MOVING;
            }
            @Override public boolean plant(BlockPosition goal) {
                String varietyKey=NativeForesterSaplings.choose(palette,variety);
                if(varietyKey==null || !eligible(world,goal,home)) return false;
                BlockType sapling=BlockType.getAssetMap().getAsset(varietyKey);
                if(sapling==null || sapling.getFarming()==null) return false;
                boolean placed=HytaleBlockMutations.setBlock(world,goal.x(),goal.y(),goal.z(),sapling);
                if(placed) System.out.println("[CivForester] planted="+varietyKey+" x="+goal.x()+" y="+goal.y()+" z="+goal.z());
                return placed;
            }
        });
        if (status.state() != ForesterWorkController.State.TRAVELLING) units.clearMoveTarget(ref);
    }

    private BuildingPlacementRegistry.BuildingInstance workplace(Ref<EntityStore> ref,
                                                      World world, Vector3d position) {
        UUID worldId=world.getWorldConfig().getUuid();
        BuildingPlacementRegistry.BuildingInstance nearest=null;
        double distance=Double.POSITIVE_INFINITY;
        for(var building:buildings.buildings(worldId)) {
            if(!"forester_hut".equals(building.buildingType())) continue;
            if(buildings.isUpgrading(worldId,building.id())) continue;
            double dx=position.x-(building.bounds().minX()+building.bounds().maxX())/2.0;
            double dz=position.z-(building.bounds().minZ()+building.bounds().maxZ())/2.0;
            double d=dx*dx+dz*dz;
            boolean assigned=units.workersAt(building.id()).contains(ref);
            if(!assigned && (units.workersAt(building.id()).size()>=building.workerCapacity() || d>32*32))continue;
            if(assigned) return building;
            if(d<distance){nearest=building;distance=d;}
        }
        if(nearest!=null) units.assignWorkplace(ref,nearest.id());
        return nearest;
    }

    private BlockPosition surface(World world,int x,int z,int yHome){
        for(int y=yHome+RADIUS;y>=yHome-RADIUS;y--){
            BlockType support=HytaleLoadedBlocks.blockType(world,x,y-1,z);
            BlockType space=HytaleLoadedBlocks.blockType(world,x,y,z);
            if(support==null||space==null)continue;
            if(space!=BlockType.EMPTY && !"air".equals(space.getId()))continue;
            if(soil(support))return new BlockPosition(x,y,z);
        }
        return null;
    }
    private static boolean soil(BlockType block){
        if(block==null||block.getId()==null)return false;
        return block.getId().startsWith("Soil_Grass")||block.getId().equals("Soil_Dirt");
    }
    private boolean eligible(World world,BlockPosition p,BuildingPlacementRegistry.BuildingInstance home){
        BlockType under=HytaleLoadedBlocks.blockType(world,p.x(),p.y()-1,p.z());
        BlockType at=HytaleLoadedBlocks.blockType(world,p.x(),p.y(),p.z());
        if(!soil(under)||at==null||!(at==BlockType.EMPTY||"air".equals(at.getId())))return false;
        if(buildings.findAt(home.worldId(),new Vector3i(p.x(),p.y(),p.z()))!=null)return false;
        for(int x=p.x()-3;x<=p.x()+3;x++)for(int z=p.z()-3;z<=p.z()+3;z++){
            int dx=x-p.x(),dz=z-p.z();
            if(dx*dx+dz*dz>=16)continue;
            for(int y=p.y()-3;y<=p.y()+8;y++){
                BlockType b=HytaleLoadedBlocks.blockType(world,x,y,z);
                String id=b==null?null:b.getId();
                if(id!=null && (id.contains("_Trunk")||id.startsWith("Plant_Sapling_")))return false;
            }
        }
        return true;
    }
    private void release(CivUnitRegistry.UnitKey key,Ref<EntityStore> ref){
        core.interrupt(key); planners.remove(key); varieties.remove(key);
        if(ref.isValid())units.clearMoveTarget(ref);
    }
}
