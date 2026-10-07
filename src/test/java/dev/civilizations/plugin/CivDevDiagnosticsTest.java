package dev.civilizations.plugin;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivDevDiagnosticsTest {
    @Test
    void eventHistoryIsBoundedAndOrdered() {
        CivDevEventHistory history = new CivDevEventHistory();
        UUID entity = UUID.randomUUID();

        for (int i = 0; i < CivDevEventHistory.MAX_EVENTS_PER_ENTITY + 5; i++) {
            history.record(entity, "tick", Map.of("value", i));
        }

        var events = history.snapshot(entity);
        assertEquals(CivDevEventHistory.MAX_EVENTS_PER_ENTITY, events.size());
        assertEquals(5, events.getFirst().details().get("value"));
        assertEquals(
            CivDevEventHistory.MAX_EVENTS_PER_ENTITY + 4,
            events.getLast().details().get("value")
        );
        assertTrue(events.getFirst().sequence() < events.getLast().sequence());
    }

    @Test
    void runtimeStateTracksOnlyExplicitDevSpawns() {
        CivDevRuntimeState state = new CivDevRuntimeState();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        state.trackSpawn(first);
        state.trackSpawn(second);
        state.trackSpawn(first);

        assertEquals(2, state.spawnedSnapshot().size());
        assertTrue(state.spawnedSnapshot().contains(first));
        state.forget(first);
        assertFalse(state.spawnedSnapshot().contains(first));
        assertTrue(state.spawnedSnapshot().contains(second));
        state.clear();
        assertTrue(state.spawnedSnapshot().isEmpty());
    }
}
