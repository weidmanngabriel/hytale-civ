package dev.civilizations.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MineWorkerEntryPolicyTest {
    @Test void entersViaAccessAndConnectorBeforeWork() {
        assertEquals(MineWorkerEntryPolicy.Destination.WORKPLACE_ACCESS,
            MineWorkerEntryPolicy.next(true,false,false));
        assertEquals(MineWorkerEntryPolicy.Destination.TUNNEL_CONNECTOR,
            MineWorkerEntryPolicy.next(true,true,false));
        assertEquals(MineWorkerEntryPolicy.Destination.WORK_FRONT,
            MineWorkerEntryPolicy.next(true,true,true));
    }
    @Test void missingAccessMarkerStillRequiresConnector() {
        assertEquals(MineWorkerEntryPolicy.Destination.TUNNEL_CONNECTOR,
            MineWorkerEntryPolicy.next(false,false,false));
    }
}
