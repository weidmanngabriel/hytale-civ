package dev.civilizations.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MineRoomProgressTest {
    @Test void sharedProgressFinishesExcavationBeforeBuildAndIsIdempotentForSections() {
        var room = new MineRoom(new UUID(0,1),new UUID(0,2),MineRoom.Type.SMALL_NICHE,new BlockPosition(0,10,0));
        room = room.beginExcavation(); assertEquals(MineRoom.State.EXCAVATING,room.state());
        room = room.completeExcavationUnit(2); assertEquals(1,room.excavationWorkUnitIndex());
        assertEquals(MineRoom.State.EXCAVATING,room.state());
        room = room.completeExcavationUnit(2); assertEquals(MineRoom.State.READY_TO_BUILD,room.state());
        room = room.completeBuildSection(0,2); room = room.completeBuildSection(0,2);
        assertEquals(1,room.completedBuildSections().size());assertEquals(MineRoom.State.READY_TO_BUILD,room.state());
        room = room.completeBuildSection(1,2);assertEquals(MineRoom.State.BUILT,room.state());
        assertThrows(IllegalArgumentException.class,()->new MineRoom(new UUID(0,1),new UUID(0,2),MineRoom.Type.SMALL_NICHE,
            new BlockPosition(0,10,0)).completeBuildSection(2,2));
    }
}
