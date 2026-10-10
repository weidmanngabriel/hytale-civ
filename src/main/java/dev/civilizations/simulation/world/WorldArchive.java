package dev.civilizations.simulation.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.civilizations.core.BlockPosition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Immutable, versioned source snapshot. No Civ material classification is discarded at import.
 * Every sampled coordinate, including air, must be present in the declared box.
 */
public record WorldArchive(int formatVersion, String worldId, Bounds bounds, List<Cell> cells) {
    public static final int VERSION = 2;
    /** Conservative initial big-region export limit; runtime memory/latency must be tested. */
    public static final int MAX_EXPORT_CELLS = 2_000_000;
    private static final ObjectMapper JSON = new ObjectMapper();

    public WorldArchive {
        if (formatVersion != VERSION) throw new IllegalArgumentException("Unsupported archive version");
        if (worldId == null || worldId.isBlank()) throw new IllegalArgumentException("worldId required");
        Objects.requireNonNull(bounds, "bounds");
        cells = List.copyOf(cells);
        if (bounds.volume() > 4_000_000) throw new IllegalArgumentException("Archive volume too large");
        if (cells.size() != bounds.volume()) throw new IllegalArgumentException("Incomplete archive");
        var visited = new java.util.HashSet<BlockPosition>();
        for (Cell cell : cells) {
            if (!bounds.contains(cell.x(),cell.y(),cell.z())
                || !visited.add(new BlockPosition(cell.x(),cell.y(),cell.z())))
                throw new IllegalArgumentException("Out-of-bounds or duplicate cell");
        }
    }

    public void write(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (var out = new GZIPOutputStream(Files.newOutputStream(path))) { JSON.writeValue(out, this); }
    }

    public static WorldArchive read(Path path) throws IOException {
        try (var in = new GZIPInputStream(Files.newInputStream(path))) {
            return JSON.readValue(in, WorldArchive.class);
        }
    }

    public Map<BlockPosition, Material> classify() {
        Map<BlockPosition, Material> result = new HashMap<>();
        for (Cell cell : cells) {
            var category = switch (cell.fluidCategory()) {
                case "LAVA" -> Material.LAVA;
                case "WATER" -> Material.WATER;
                case "OTHER" -> Material.OTHER_FLUID;
                default -> cell.blockKey().equals("air") ? Material.AIR : Material.SOLID;
            };
            result.put(new BlockPosition(cell.x(),cell.y(),cell.z()), category);
        }
        return Map.copyOf(result);
    }

    public enum Material { AIR, SOLID, WATER, LAVA, OTHER_FLUID }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Bounds {
            if (minX>=maxX || minY>=maxY || minZ>=maxZ) throw new IllegalArgumentException("Invalid archive bounds");
        }
        public long volume() { return (long)(maxX-minX)*(maxY-minY)*(maxZ-minZ); }
        public boolean contains(int x,int y,int z) {
            return x>=minX&&x<maxX&&y>=minY&&y<maxY&&z>=minZ&&z<maxZ;
        }
    }

    public record Cell(int x,int y,int z,String blockKey,int blockIndex,int rotationIndex,
                       int fluidId,int fluidLevel,String fluidCategory) {
        public Cell(int x,int y,int z,String blockKey,int fluidId,int fluidLevel,String fluidCategory) {
            this(x,y,z,blockKey,0,0,fluidId,fluidLevel,fluidCategory);
        }

        public Cell {
            if(blockKey==null||blockKey.isBlank())throw new IllegalArgumentException("blockKey required");
            if(fluidLevel<0||fluidLevel>255)throw new IllegalArgumentException("Invalid fluid level");
            if(fluidId<0)throw new IllegalArgumentException("Invalid fluid id");
            if(!List.of("NONE","WATER","LAVA","OTHER").contains(fluidCategory))
                throw new IllegalArgumentException("Invalid fluid category");
        }
    }
}
