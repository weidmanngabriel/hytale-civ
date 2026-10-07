package dev.civilizations.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic room excavation geometry. The room prefab is decoration/construction inside this
 * already excavated envelope; Hytale remains authoritative for the actual world blocks.
 */
public record MineRoomGeometry(
    MineRoom room,
    List<List<BlockPosition>> excavationWorkUnits,
    Set<BlockPosition> excavationBlocks,
    BlockPosition entranceWorkTarget
) {
    public MineRoomGeometry {
        if (room == null || excavationWorkUnits == null || excavationBlocks == null
            || entranceWorkTarget == null) {
            throw new IllegalArgumentException("Mine room geometry fields must not be null.");
        }
        excavationWorkUnits = excavationWorkUnits.stream().map(List::copyOf).toList();
        excavationBlocks = Set.copyOf(excavationBlocks);
    }

    public static MineRoomGeometry generate(MineRoom room, MineTunnelGeometry tunnelGeometry) {
        if (room == null || tunnelGeometry == null) {
            throw new IllegalArgumentException("Mine room geometry inputs must not be null.");
        }
        if (room.attachmentSliceIndex() >= tunnelGeometry.slices().size()) {
            throw new IllegalArgumentException("Mine room attachment slice is outside tunnel geometry.");
        }

        Dimensions dimensions = dimensions(room.type());
        MineTunnelGeometry.Slice attachment = tunnelGeometry.slices().get(room.attachmentSliceIndex());
        Cardinal outward = Cardinal.from(room.outwardHeading());
        Cardinal cross = outward.cross();

        LinkedHashSet<BlockPosition> all = new LinkedHashSet<>();
        List<List<BlockPosition>> units = new ArrayList<>();

        int tunnelHalfWidth = Math.max(1, attachment.widthBlocks() / 2);
        int firstRoomDepth = tunnelHalfWidth + 2;
        int roomDepthEnd = firstRoomDepth + dimensions.depth() - 1;
        int halfWidth = dimensions.width() / 2;

        // Access throat: three blocks wide/high from the known tunnel edge into the room.
        for (int depthStart = tunnelHalfWidth; depthStart < firstRoomDepth; depthStart += 2) {
            List<BlockPosition> unit = new ArrayList<>();
            for (int depth = depthStart; depth <= Math.min(firstRoomDepth - 1, depthStart + 1); depth++) {
                addSlice(unit, attachment.floorCenter(), outward, cross, depth, 1, 3);
            }
            addUnit(units, all, unit);
        }

        // Room body: 1-2 blocks of depth per semantic excavation unit.
        for (int depthStart = firstRoomDepth; depthStart <= roomDepthEnd; depthStart += 2) {
            List<BlockPosition> unit = new ArrayList<>();
            for (int depth = depthStart; depth <= Math.min(roomDepthEnd, depthStart + 1); depth++) {
                addSlice(unit, attachment.floorCenter(), outward, cross, depth, halfWidth, dimensions.height());
            }
            addUnit(units, all, unit);
        }

        BlockPosition entrance = offset(attachment.floorCenter(), outward, Math.max(0, tunnelHalfWidth - 1), 0);
        return new MineRoomGeometry(room, units, all, entrance);
    }

    public BlockPosition workTargetForUnit(int unitIndex) {
        if (unitIndex <= 0) return entranceWorkTarget;
        if (unitIndex >= excavationWorkUnits.size()) return room.position();
        List<BlockPosition> previous = excavationWorkUnits.get(unitIndex - 1);
        return previous.stream()
            .min(Comparator
                .comparingInt(BlockPosition::y)
                .thenComparingInt(BlockPosition::x)
                .thenComparingInt(BlockPosition::z))
            .orElse(entranceWorkTarget);
    }

    public static Dimensions dimensions(MineRoom.Type type) {
        return switch (type) {
            case SMALL_NICHE -> new Dimensions(5, 4, 4);
            case MATERIAL_STORAGE -> new Dimensions(7, 6, 4);
            case REST_ACCOMMODATION -> new Dimensions(9, 8, 5);
            default -> new Dimensions(7, 6, 4);
        };
    }

    private static void addSlice(
        List<BlockPosition> target,
        BlockPosition attachment,
        Cardinal outward,
        Cardinal cross,
        int depth,
        int halfWidth,
        int height
    ) {
        for (int lateral = -halfWidth; lateral <= halfWidth; lateral++) {
            for (int y = 0; y < height; y++) {
                target.add(new BlockPosition(
                    attachment.x() + outward.dx * depth + cross.dx * lateral,
                    attachment.y() + y,
                    attachment.z() + outward.dz * depth + cross.dz * lateral
                ));
            }
        }
    }

    private static void addUnit(
        List<List<BlockPosition>> units,
        Set<BlockPosition> all,
        List<BlockPosition> unit
    ) {
        LinkedHashSet<BlockPosition> unique = new LinkedHashSet<>(unit);
        all.addAll(unique);
        units.add(List.copyOf(unique));
    }

    private static BlockPosition offset(BlockPosition origin, Cardinal direction, int distance, int y) {
        return new BlockPosition(
            origin.x() + direction.dx * distance,
            origin.y() + y,
            origin.z() + direction.dz * distance
        );
    }

    public record Dimensions(int width, int depth, int height) {
        public Dimensions {
            if (width < 3 || depth < 2 || height < 3 || width % 2 == 0) {
                throw new IllegalArgumentException("Room dimensions must be odd-width and navigable.");
            }
        }
    }

    private enum Cardinal {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        private final int dx;
        private final int dz;

        Cardinal(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        static Cardinal from(MineHeading heading) {
            return switch (heading) {
                case NORTH -> NORTH;
                case EAST -> EAST;
                case SOUTH -> SOUTH;
                case WEST -> WEST;
                default -> throw new IllegalArgumentException("Room heading must be cardinal.");
            };
        }

        Cardinal cross() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
}
