package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineSegment;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CivMineDebugServiceTest {

    @Test
    void branchLevelFollowsPersistedParentChain() {
        UUID mineId = UUID.randomUUID();
        MineSegment root = segment(mineId, null, 0);
        MineSegment child = segment(mineId, root.id(), 10);
        MineSegment grandchild = segment(mineId, child.id(), 20);
        Map<UUID, MineSegment> byId = Map.of(
            root.id(), root,
            child.id(), child,
            grandchild.id(), grandchild
        );

        assertEquals(0, CivMineDebugService.branchLevel(root, byId));
        assertEquals(1, CivMineDebugService.branchLevel(child, byId));
        assertEquals(2, CivMineDebugService.branchLevel(grandchild, byId));
    }

    @Test
    void branchLevelStopsAtMissingParentInsteadOfInventingTopology() {
        UUID mineId = UUID.randomUUID();
        MineSegment orphan = segment(mineId, UUID.randomUUID(), 0);

        assertEquals(0, CivMineDebugService.branchLevel(orphan, Map.of(orphan.id(), orphan)));
    }

    private static MineSegment segment(UUID mineId, UUID parentId, int x) {
        return MineSegment.reserved(
            UUID.randomUUID(),
            mineId,
            parentId,
            new BlockPosition(x, 20, 0),
            MineDirection.EAST,
            8
        );
    }
}
