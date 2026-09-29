package dev.civilizations.hytale;

import com.hypixel.hytale.server.npc.role.RoleDebugFlags;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CivPathDebugServiceTest {

    @Test
    void enablingPathDebugPreservesExistingFlagsWithoutMutatingInput() {
        EnumSet<RoleDebugFlags> original = EnumSet.of(RoleDebugFlags.DisplayName);

        EnumSet<RoleDebugFlags> updated = CivPathDebugService.withPathFlag(original, true);

        assertEquals(EnumSet.of(RoleDebugFlags.DisplayName), original);
        assertTrue(updated.contains(RoleDebugFlags.DisplayName));
        assertTrue(updated.contains(RoleDebugFlags.VisPath));
    }

    @Test
    void disablingPathDebugRemovesOnlyVisPath() {
        EnumSet<RoleDebugFlags> original =
            EnumSet.of(RoleDebugFlags.DisplayName, RoleDebugFlags.VisPath);

        EnumSet<RoleDebugFlags> updated = CivPathDebugService.withPathFlag(original, false);

        assertTrue(updated.contains(RoleDebugFlags.DisplayName));
        assertFalse(updated.contains(RoleDebugFlags.VisPath));
    }
}
