package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import dev.civilizations.core.BuildingBounds;
import dev.civilizations.core.Profession;
import dev.civilizations.hytale.BuildingPlacementRegistry;
import dev.civilizations.hytale.CivUnitRegistry;
import dev.civilizations.hytale.HytaleLoadedBlocks;
import dev.civilizations.hytale.NativeBuildingStorage;
import dev.civilizations.hytale.PrefabPlacementService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Real NPC + authentic forester prefab/chest scenario, isolated from the player's world. */
final class CivForesterNpcProbeCommand extends CommandBase {
    private static final String WORLD="CivForesterNpcProbe";
    private static final String PREFAB="Civilizations/Forester/Forester_01";
    private static final String[] SPECIES={"Plant_Sapling_Oak","Plant_Sapling_Birch","Plant_Sapling_Spruce"};
    private static final int[] COUNTS={1,5,20};
    private static final Vector3i CHEST=new Vector3i(-3,1,-2);
    private static final long TIMEOUT_NANOS=TimeUnit.SECONDS.toNanos(55);
    private final CivUnitRegistry units;
    private final BuildingPlacementRegistry buildings;
    private final NativeBuildingStorage storage=new NativeBuildingStorage();

    CivForesterNpcProbeCommand(CivUnitRegistry units, BuildingPlacementRegistry buildings){
        super("civforesternpcprobe","Run a native chest-fed Civ forester in an isolated world.");
        requireNoPermission();
        this.units=units;
        this.buildings=buildings;
    }
    @Override protected void executeSync(CommandContext context) {
        try {
            System.out.println("CIV_FORESTER_NPC_STARTED prefab="+PREFAB);
            Universe.get().addWorld(WORLD,"Flat","default").whenComplete((world,error)->{
                if(error!=null || world==null){fail("cannot create isolated world",error);return;}
                world.getWorldConfig().setCanUnloadChunks(false);
                CivRuntimeProbeSuite.applyWarmDilation(world);
                world.execute(()->preload(world));
            });
        }catch(Throwable error){fail("startup exception",error);}
    }
    private void preload(World world){
        try {
            var refs=new java.util.ArrayList<CompletableFuture<Ref<ChunkStore>>>();
            for(int cx=-1;cx<=1;cx++)for(int cz=-1;cz<=1;cz++){
                int x=cx*32,z=cz*32;
                refs.add(world.getChunkStore().getChunkReferenceAsync(ChunkUtil.indexChunkFromBlock(x,z),4));
                refs.add(world.getChunkStore().getChunkSectionReferenceAtBlockAsync(x,1,z,4));
                refs.add(world.getChunkStore().getChunkSectionReferenceAtBlockAsync(x,17,z,4));
            }
            CompletableFuture.allOf(refs.toArray(CompletableFuture[]::new)).whenComplete((ignore,error)->
                world.execute(()->{
                    if(error!=null){fail("fixture sections unavailable",error);return;}
                    for(var load:refs){
                        Ref<ChunkStore> found=load.getNow(null);
                        if(found==null || !found.isValid()){fail("fixture section not valid",null);return;}
                    }
                    buildAndSpawn(world);
                }));
        }catch(Throwable error){fail("preload exception",error);}
    }
    private void buildAndSpawn(World world){
        try {
            var path=PrefabStore.get().findBrowsablePrefabPath(PREFAB);
            if(path==null){fail("forester prefab missing "+PREFAB,null);return;}
            BlockSelection source=PrefabStore.get().getPrefab(path);
            if(source==null || source.getBlockCount()==0){fail("forester prefab empty",null);return;}
            new BlockSelection(source).placeNoReturn(world,new Vector3i(0,0,0),world.getEntityStore().getStore());
            UUID buildingId=UUID.randomUUID();
            var marker=new PrefabPlacementService.PlacedMarker(
                "forester-output",CHEST,Map.of("civ.type","output_storage","civ.building","forester_hut"),null);
            var bounds=new BuildingBounds(-4.5,0,-3.5,4.5,6.5,3.5);
            var b=new BuildingPlacementRegistry.BuildingInstance(
                buildingId,world.getWorldConfig().getUuid(),"forester_hut","forester-bounds",
                bounds,List.of(marker),null);
            buildings.restoreWorld(world.getWorldConfig().getUuid(),List.of(b));
            System.out.println("CIV_FORESTER_NPC_PREFAB_READY blocks="+source.getBlockCount());
            world.scheduleAfter(()->stockChestAndSpawn(world,buildingId),900L,TimeUnit.MILLISECONDS);
        }catch(Throwable error){fail("prefab or registry setup failed",error);}
    }
    private void stockChestAndSpawn(World world,UUID buildingId) {
        try {
            var chest=storage.findContainer(world,CHEST);
            if(chest==null || chest.getItemContainer()==null){
                BlockType chestType=HytaleLoadedBlocks.blockType(world,CHEST.x,CHEST.y,CHEST.z);
                String expected=chestType==null?"null":chestType.getId();
                var empty=BlockType.EMPTY;
                boolean removed=dev.civilizations.hytale.HytaleBlockMutations.setBlock(
                    world,CHEST.x,CHEST.y,CHEST.z,empty);
                boolean recreated=removed && dev.civilizations.hytale.HytaleBlockMutations.setBlock(
                    world,CHEST.x,CHEST.y,CHEST.z,chestType);
                chest=storage.findContainer(world,CHEST);
                System.out.println("CIV_FORESTER_NPC_CHEST_REPLACE_TEST original="+expected
                    +" removed="+removed+" recreated="+recreated
                    +" hasContainer="+(chest!=null&&chest.getItemContainer()!=null));
            }
            if(chest==null || chest.getItemContainer()==null){
                System.out.println("CIV_FORESTER_NPC_CHEST_DIAG_BEGIN");
                for(int x=-6;x<=1;x++)for(int y=0;y<=4;y++)for(int z=-5;z<=2;z++){
                    BlockType block=HytaleLoadedBlocks.blockType(world,x,y,z);
                    if(block==null || block.getId()==null || !block.getId().toLowerCase().contains("chest")) continue;
                    Ref<ChunkStore> section=world.getChunkStore().getChunkSectionReferenceAtBlock(x,y,z);
                    var comps=section==null||!section.isValid()?null:world.getChunkStore().getStore()
                        .getComponent(section,com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection.getComponentType());
                    Ref<ChunkStore> blockRef=comps==null?null:comps.getBlockReference(ChunkUtil.indexBlock(x,y,z));
                    var inventory=blockRef==null||!blockRef.isValid()?null:blockRef.getStore().getComponent(
                        blockRef,com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock.getComponentType());
                    System.out.println("CIV_FORESTER_NPC_CHEST_DIAG x="+x+" y="+y+" z="+z
                        +" key="+block.getId()+" blockRef="+(blockRef!=null&&blockRef.isValid())
                        +" itemContainer="+(inventory!=null));
                }
                System.out.println("CIV_FORESTER_NPC_CHEST_DIAG_END");
                fail("native prefab chest not found near "+CHEST,null);return;
            }
            var container=chest.getItemContainer();
            if(container.getCapacity()<3){fail("native chest capacity < 3",null);return;}
            for(int i=0;i<SPECIES.length;i++)
                if(!container.setItemStackForSlot((short)i,new ItemStack(SPECIES[i],COUNTS[i])).succeeded()){
                    fail("native chest could not accept "+SPECIES[i],null);return;
                }
            System.out.println("CIV_FORESTER_NPC_CHEST_READY kinds=3 amounts=1,5,20");
            var spawned=new AtomicReference<Ref<EntityStore>>();
            Vector3d start=new Vector3d(8.5,1,0.5);
            var validation=NPCPlugin.get().spawnNPCWithSpaceValidation(
                world.getEntityStore().getStore(),"Civ_Inhabitant",null,start,new Rotation3f(),
                (npc,ref,store)->spawned.set(ref));
            Ref<EntityStore> ref=spawned.get();
            if(ref==null || !ref.isValid()){fail("native NPC spawn failed: "+validation,null);return;}
            if(!units.toggleClaim(ref) || !units.isClaimed(ref)){fail("cannot claim native NPC",null);return;}
            units.assignProfession(ref,Profession.FORESTER);
            units.assignWorkplace(ref,buildingId);
            if(units.getProfession(ref)!=Profession.FORESTER){fail("native NPC has wrong profession",null);return;}
            System.out.println("CIV_FORESTER_NPC_WORKER_READY role=FORESTER");
            long end=System.nanoTime()+TIMEOUT_NANOS;
            world.scheduleAfter(()->check(world,ref,start,end),500L,TimeUnit.MILLISECONDS);
        }catch(Throwable error){fail("chest stocking or NPC spawn threw",error);}
    }
    private void check(World world,Ref<EntityStore> npc,Vector3d start,long end){
        try {
            if(!npc.isValid()){fail("forester NPC invalid",null);return;}
            var pos=npc.getStore().getComponent(npc,TransformComponent.getComponentType());
            if(pos==null){fail("forester lost transform",null);return;}
            int count=0;
            var seen=new java.util.HashSet<String>();
            for(int x=-22;x<=22;x++)for(int z=-22;z<=22;z++){
                BlockType at=HytaleLoadedBlocks.blockType(world,x,1,z);
                String id=at==null?null:at.getId();
                if(id!=null && id.startsWith("Plant_Sapling_")){
                    count++;
                    seen.add(id);
                }
            }
            var chest=storage.findContainer(world,CHEST);
            if(chest==null || chest.getItemContainer()==null){fail("chest disappeared during work",null);return;}
            for(int i=0;i<SPECIES.length;i++){
                ItemStack stack=chest.getItemContainer().getItemStack((short)i);
                if(stack==null || stack.getQuantity()!=COUNTS[i]){
                    fail("native chest stock was consumed at slot "+i,null);return;
                }
            }
            double dx=pos.getPosition().x-start.x;
            double dz=pos.getPosition().z-start.z;
            double moved=Math.sqrt(dx*dx+dz*dz);
            if(count>=2 && moved>=0.8) {
                System.out.println("CIV_FORESTER_NPC_PLANTED count="+count+" species="+seen+" moved="+moved);
                System.out.println("CIV_FORESTER_NPC_STOCK_UNCHANGED amounts=1,5,20");
                System.out.println("CIV_FORESTER_NPC_PASS");
                HytaleServer.get().shutdownServer();
                return;
            }
            if(System.nanoTime()>=end){
                fail("NPC did not reach two valid native saplings: planted="+count+" moved="+moved,null);return;
            }
            world.scheduleAfter(()->check(world,npc,start,end),500L,TimeUnit.MILLISECONDS);
        }catch(Throwable error){fail("worker progress check threw",error);}
    }
    private static void fail(String reason,Throwable cause){
        System.out.println("CIV_FORESTER_NPC_FAIL "+reason);
        if(cause!=null)cause.printStackTrace(System.out);
        HytaleServer.get().shutdownServer();
    }
}
