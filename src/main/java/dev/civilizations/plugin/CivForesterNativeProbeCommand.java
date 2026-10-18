package dev.civilizations.plugin;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.civilizations.hytale.HytaleBlockMutations;
import dev.civilizations.hytale.HytaleLoadedBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Engine-level placement diagnostic using a disposable, isolated flat Hytale world. */
final class CivForesterNativeProbeCommand extends CommandBase {
    private static final String NAME = "CivForesterPlantProbe";
    private static final String[] SPECIES = {
        "Plant_Sapling_Oak", "Plant_Sapling_Birch", "Plant_Sapling_Spruce"
    };
    CivForesterNativeProbeCommand() {
        super("civforesterplantprobe", "Verify three real native Hytale saplings initialize farming state.");
        requireNoPermission();
    }
    @Override protected void executeSync(CommandContext context) {
        try {
            System.out.println("CIV_FORESTER_NATIVE_STARTED species=Oak,Birch,Spruce");
            Universe.get().addWorld(NAME, "Flat", "default").whenComplete((world,error)->{
                if(error!=null || world==null){ fail("unable to create test world",error);return; }
                world.getWorldConfig().setCanUnloadChunks(false);
                world.execute(()->preload(world));
            });
        }catch(Throwable error){ fail("probe startup",error); }
    }
    private void preload(World world) {
        try {
            List<CompletableFuture<Ref<ChunkStore>>> loads = new ArrayList<>();
            for (int x : new int[]{0,4,8}) {
                long chunkId=ChunkUtil.indexChunkFromBlock(x,0);
                loads.add(world.getChunkStore().getChunkReferenceAsync(chunkId,4));
                loads.add(world.getChunkStore().getChunkSectionReferenceAtBlockAsync(x,1,0,4));
            }
            CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((v,error)->
                world.execute(()->{
                    if(error!=null){fail("chunk preload",error);return;}
                    place(world);
                }));
        }catch(Throwable error){fail("preload exception",error);}
    }
    private void place(World world){
        try {
            BlockType soil=BlockType.getAssetMap().getAsset("Soil_Grass");
            if(soil==null){ fail("native soil asset Soil_Grass not found",null);return;}
            for(int i=0;i<SPECIES.length;i++){
                int x=i*4;
                BlockType seed=BlockType.getAssetMap().getAsset(SPECIES[i]);
                if(seed==null || seed.getFarming()==null){fail("native sapling has no farming "+SPECIES[i],null);return;}
                if(!HytaleBlockMutations.setBlock(world,x,0,0,soil)){fail("soil placement failed "+x,null);return;}
                BlockType before=HytaleLoadedBlocks.blockType(world,x,1,0);
                if(before!=BlockType.EMPTY){fail("planting target occupied "+x+" type="+before,null);return;}
                boolean placed=HytaleBlockMutations.setBlock(world,x,1,0,seed);
                if(!placed){fail("sapling placement failed "+SPECIES[i],null);return;}
                System.out.println("CIV_FORESTER_NATIVE_PLACED species="+SPECIES[i]+" x="+x);
            }
            world.scheduleAfter(()->verify(world),1200L,TimeUnit.MILLISECONDS);
        }catch(Throwable error){fail("native placement exception",error);}
    }
    private void verify(World world){
        try{
            for(int i=0;i<SPECIES.length;i++){
                int x=i*4;
                BlockType actual=HytaleLoadedBlocks.blockType(world,x,1,0);
                if(actual==null || !SPECIES[i].equals(actual.getId())){
                    fail("sapling changed unexpectedly: "+SPECIES[i]+" actual="+actual,null);return;
                }
                Ref<ChunkStore> section=world.getChunkStore().getChunkSectionReferenceAtBlock(x,1,0);
                if(section==null || !section.isValid()){fail("missing sapling section "+x,null);return;}
                var components=world.getChunkStore().getStore().getComponent(section,BlockComponentSection.getComponentType());
                if(components==null){fail("missing native block components "+SPECIES[i],null);return;}
                Ref<ChunkStore> saplingRef=components.getBlockReference(ChunkUtil.indexBlock(x,1,0));
                if(saplingRef==null || !saplingRef.isValid()){fail("missing sapling block entity "+SPECIES[i],null);return;}
                FarmingBlock farming=saplingRef.getStore().getComponent(saplingRef,FarmingBlock.getComponentType());
                if(farming==null){fail("native farming state is missing "+SPECIES[i],null);return;}
                System.out.println("CIV_FORESTER_NATIVE_FARMING species="+SPECIES[i]
                    +" stage="+farming.getCurrentStageSet()
                    +" progress="+farming.getGrowthProgress()
                    +" lastTickSet="+(farming.getLastTickGameTime()!=null));
            }
            System.out.println("CIV_FORESTER_NATIVE_PASS");
            HytaleServer.get().shutdownServer();
        }catch(Throwable error){fail("native farming readback exception",error);}
    }
    private static void fail(String detail,Throwable cause){
        System.out.println("CIV_FORESTER_NATIVE_FAIL "+detail);
        if(cause!=null)cause.printStackTrace(System.out);
        HytaleServer.get().shutdownServer();
    }
}
