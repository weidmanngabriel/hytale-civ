package dev.civilizations.plugin;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.ShaderType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import dev.civilizations.simulation.world.WorldArchive;
import java.nio.file.Path;
import java.util.ArrayList;

final class CivWorldArchiveExport {
    private CivWorldArchiveExport() {}

    static Path exportLoaded(World world, WorldArchive.Bounds bounds) throws java.io.IOException {
        if (bounds.volume() > 250000) throw new IllegalArgumentException("Region too large");
        var cells = new ArrayList<WorldArchive.Cell>((int) bounds.volume());
        for (int x=bounds.minX(); x<bounds.maxX(); x++) for (int z=bounds.minZ(); z<bounds.maxZ(); z++) {
            WorldChunk chunk=world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x,z));
            if (chunk==null) throw new IllegalStateException("Chunk not loaded: "+x+","+z);
            for (int y=bounds.minY(); y<bounds.maxY(); y++) {
                BlockType type=chunk.getBlockType(x,y,z);
                if (type==null) throw new IllegalStateException("Block unavailable: "+x+","+y+","+z);
                int fluidId=chunk.getFluidId(x,y,z);
                int fluidLevel=Byte.toUnsignedInt(chunk.getFluidLevel(x,y,z));
                String blockKey=type==BlockType.EMPTY||type.getMaterial()==BlockMaterial.Empty?"air":type.getId();
                Fluid fluid=Fluid.getAssetMap().getAssetOrDefault(fluidId,Fluid.UNKNOWN);
                String category=fluidId==Fluid.EMPTY_ID?"NONE":fluid!=null&&fluid.hasEffect(ShaderType.Lava)?"LAVA":fluid!=null&&fluid.hasEffect(ShaderType.Water)?"WATER":"OTHER";
                cells.add(new WorldArchive.Cell(x,y,z,blockKey,fluidId,fluidLevel,category));
            }
        }
        WorldArchive archive=new WorldArchive(WorldArchive.VERSION,world.getName(),bounds,cells);
        Path output=Path.of("civ-world-archives","region-"+System.currentTimeMillis()+".civworld.gz");
        archive.write(output);
        return output.toAbsolutePath();
    }
}
