package dev.civilizations.plugin;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DevEntityTrackerTest {
    @Test void resetNeverCallsRemovalForBorrowedInhabitantsAndRetainsUnloadedSpawnedEntities() {
        var tracker = new DevEntityTracker<String>();
        String borrowed = tracker.add("existing-inhabitant", false);
        tracker.add("spawned-loaded", true);
        String unloaded = tracker.add("spawned-unloaded", true);
        List<String> attempted = new ArrayList<>();
        assertEquals(1, tracker.resetOwned(entity -> { attempted.add(entity); return entity.endsWith("-loaded"); }));
        assertEquals(List.of("spawned-loaded", "spawned-unloaded"), attempted);
        assertEquals("existing-inhabitant", tracker.get(borrowed));
        assertEquals("spawned-unloaded", tracker.get(unloaded));
        assertEquals(1, tracker.resetOwned(entity -> true));
        assertEquals(1, tracker.entries().size());
        assertFalse(tracker.owned(borrowed));
        assertEquals(borrowed, tracker.add("existing-inhabitant", false));
    }
}
