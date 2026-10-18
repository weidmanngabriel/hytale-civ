package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WoodCargoLedgerTest {

    @Test
    void conservesWoodAcrossCompleteAndPartialPickupDepositAndResume() {
        var ledger = new WoodCargoLedger();
        var origin = new BlockPosition(3, 0, 5);

        ledger.fell(origin, 140);
        assertEquals(140, ledger.pickUp("woodcutter", origin));
        ledger.fell(origin, 130);
        assertEquals(60, ledger.pickUp("woodcutter", origin));
        assertEquals(200, ledger.carried("woodcutter"));
        assertEquals(70, ledger.ground());
        assertEquals(270, ledger.total());
        assertTrue(ledger.shouldReturn("woodcutter"));

        assertEquals(200, ledger.deposit("woodcutter"));
        assertEquals(200, ledger.warehouse());
        assertEquals(70, ledger.pickUp("woodcutter", origin));
        assertEquals(0, ledger.ground());
        assertEquals(270, ledger.total());
        assertEquals(70, ledger.deposit("woodcutter"));
        assertEquals(270, ledger.warehouse());
        assertEquals(270, ledger.total());
    }

    @Test
    void cannotExceedCapacityOrCreateMissingPiles() {
        var ledger = new WoodCargoLedger();
        var position = new BlockPosition(0, 0, 0);
        assertEquals(0, ledger.pickUp("one", position));
        assertThrows(IllegalArgumentException.class, () -> ledger.fell(position, 0));
        ledger.fell(position, 250);
        assertEquals(200, ledger.pickUp("one", position));
        assertEquals(0, ledger.pickUp("one", position));
        assertEquals(50, ledger.ground());
        assertEquals(50, ledger.pickUp("two", position));
        assertEquals(250, ledger.total());
        assertEquals(0, ledger.ground());
    }
}
