package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineRoomGeometryTest {

    @Test
    void excavationIsSplitIntoSmallDepthWorkUnitsAndCoversWholeRoom() {
        UUID tunnelId = UUID.randomUUID();
        MineTunnelPath path = MinePathPlanner.plan(
            MineTunnel.Kind.MAIN,
            new BlockPosition(0, 20, 0),
            new BlockPosition(0, 20, 0),
            MineHeading.EAST,
            80,
            42L
        );
        MineTunnelGeometry tunnel = MineTunnelVoxelizer.voxelize(path);
        int attachment = 30;
        MineTunnelGeometry.Slice slice = tunnel.slices().get(attachment);
        MineRoomGeometry.Dimensions dimensions = MineRoomGeometry.dimensions(MineRoom.Type.MATERIAL_STORAGE);
        BlockPosition center = new BlockPosition(
            slice.floorCenter().x() + slice.widthBlocks() / 2 + 2 + dimensions.depth() / 2,
            slice.floorCenter().y(),
            slice.floorCenter().z()
        );
        MineRoom room = new MineRoom(
            UUID.randomUUID(), tunnelId, MineRoom.Type.MATERIAL_STORAGE, center,
            MineHeading.EAST, attachment, MineRoom.State.PLANNED, 0, Set.of()
        );

        MineRoomGeometry geometry = MineRoomGeometry.generate(room, tunnel);
        Set<BlockPosition> union = new HashSet<>();
        geometry.excavationWorkUnits().forEach(union::addAll);

        assertEquals(geometry.excavationBlocks(), union);
        assertTrue(geometry.excavationWorkUnits().size() >= 3);
        assertTrue(geometry.excavationWorkUnits().stream().allMatch(unit -> !unit.isEmpty()));
        assertTrue(geometry.excavationBlocks().contains(room.position()));
    }
}
