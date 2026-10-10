package dev.civilizations.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.civilizations.core.MineWorkerRouteDecision.*;

class MineWorkerRouteDecisionTest {
    @Test void minerMustEnterThroughAccessThenConnector() {
        assertEquals(Destination.WORKPLACE_ACCESS,next(false,false));
        assertEquals(Destination.TUNNEL_CONNECTOR,next(true,false));
        assertEquals(Destination.WORK_FRONT,next(true,true));
        assertEquals(Destination.WORKPLACE_ACCESS,next(false,true));
    }
}
