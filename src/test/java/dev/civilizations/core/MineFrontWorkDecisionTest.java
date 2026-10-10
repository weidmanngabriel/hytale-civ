package dev.civilizations.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MineFrontWorkDecisionTest {
    @Test void ownersAndCapacityAreIdenticalForAllAdapters() {
        var coordinator = new MineFrontCoordinator<String>();
        UUID front=UUID.fromString("00000000-0000-0000-0000-000000000012");
        var first=new BlockPosition(1,2,3);
        var second=new BlockPosition(1,2,4);
        var candidates=List.of(first,second);
        var a=MineFrontWorkDecision.choose(coordinator,front,"a",2,candidates,p->true);
        var b=MineFrontWorkDecision.choose(coordinator,front,"b",2,candidates,p->true);
        assertEquals(MineFrontWorkDecision.Result.CLAIMED,a.result());
        assertEquals(MineFrontWorkDecision.Result.CLAIMED,b.result());
        assertNotEquals(a.block(),b.block());
        assertEquals(MineFrontWorkDecision.Result.FRONT_FULL,
            MineFrontWorkDecision.choose(coordinator,front,"c",2,candidates,p->true).result());
        coordinator.completeClaim(front,"a",a.block());
        coordinator.releaseWorker("a");
        assertEquals(MineFrontWorkDecision.Result.NO_BLOCK,
            MineFrontWorkDecision.choose(coordinator,front,"c",2,candidates,p->false).result());
        assertEquals(MineFrontWorkDecision.Result.CLAIMED,
            MineFrontWorkDecision.choose(coordinator,front,"c",2,candidates,p->true).result());
    }
}
