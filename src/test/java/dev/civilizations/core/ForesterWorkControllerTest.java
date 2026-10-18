package dev.civilizations.core;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ForesterWorkControllerTest {
    private static final BlockPosition SITE = new BlockPosition(4, 1, 2);

    private static class FakeEngine implements ForesterWorkController.Engine {
        int stock = 4, planted;
        boolean valid = true;
        ForesterWorkController.Navigation navigation = ForesterWorkController.Navigation.MOVING;
        @Override public int saplingsAvailable() { return stock; }
        @Override public BlockPosition selectSite(Set<BlockPosition> reserved) {
            return reserved.contains(SITE) ? null : SITE;
        }
        @Override public boolean siteStillValid(BlockPosition target) { return valid; }
        @Override public ForesterWorkController.Navigation navigate(BlockPosition target) { return navigation; }
        @Override public boolean plant(BlockPosition target) { stock--; planted++; return true; }
    }

    @Test void claimCompetitionAndInterruptReleasesSite() {
        var c = new ForesterWorkController<String>();
        FakeEngine e = new FakeEngine();
        assertEquals(ForesterWorkController.State.TRAVELLING, c.tick("a", e).state());
        assertEquals(ForesterWorkController.State.RETRY, c.tick("b", e).state());
        c.interrupt("a");
        assertEquals(ForesterWorkController.State.TRAVELLING, c.tick("b", e).state());
        e.navigation = ForesterWorkController.Navigation.ARRIVED;
        assertEquals(ForesterWorkController.State.SELECTING, c.tick("b", e).state());
        assertEquals(1, e.planted);
        assertEquals(3, e.stock);
    }

    @Test void unavailableStockAndFailedNavigationDoNotPlant() {
        var c = new ForesterWorkController<String>();
        FakeEngine e = new FakeEngine();
        e.stock = 0;
        assertEquals(ForesterWorkController.State.WAITING_FOR_STOCK, c.tick("a", e).state());
        e.stock = 1;
        e.navigation = ForesterWorkController.Navigation.FAILED;
        assertEquals(ForesterWorkController.State.RETRY, c.tick("a", e).state());
        assertEquals(0, e.planted);
        e.navigation = ForesterWorkController.Navigation.MOVING;
        assertEquals(ForesterWorkController.State.TRAVELLING, c.tick("a", e).state());
        e.valid = false;
        assertEquals(ForesterWorkController.State.RETRY, c.tick("a", e).state());
        assertEquals(0, e.planted);
    }

    @Test void engineParityAcrossIdenticalFeedback() {
        var nativeLike = new ForesterWorkController<String>();
        var headlessLike = new ForesterWorkController<String>();
        FakeEngine a = new FakeEngine(), b = new FakeEngine();
        for (var nav : new ForesterWorkController.Navigation[]{
            ForesterWorkController.Navigation.MOVING,
            ForesterWorkController.Navigation.MOVING,
            ForesterWorkController.Navigation.ARRIVED,
            ForesterWorkController.Navigation.FAILED}) {
            a.navigation = b.navigation = nav;
            assertEquals(nativeLike.tick("worker", a), headlessLike.tick("worker", b));
        }
        assertEquals(a.planted, b.planted);
    }
}
