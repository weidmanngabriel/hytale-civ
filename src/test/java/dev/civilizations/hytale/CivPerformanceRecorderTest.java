package dev.civilizations.hytale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CivPerformanceRecorderTest {
    @AfterEach
    void releaseGlobal() {
        CivPerformanceRecorder.uninstall(CivPerformanceRecorder.current());
    }

    @Test
    void inactiveProfilerDoesNotCollectAnything() {
        CivPerformanceRecorder recorder = new CivPerformanceRecorder();
        CivPerformanceRecorder.install(recorder);
        assertEquals(0, CivPerformanceRecorder.beginMeasured());
        CivPerformanceRecorder.endMeasured("miner.tick", 0);
        assertFalse(recorder.isActive());
        assertEquals(false, recorder.report().get("available"));
    }

    @Test
    void recordingHasBoundedDurationAndRetainsFinishedReport() {
        AtomicLong time = new AtomicLong(10_000_000_000L);
        CivPerformanceRecorder recorder = new CivPerformanceRecorder(time::get);
        CivPerformanceRecorder.install(recorder);
        assertTrue(recorder.start());
        assertFalse(recorder.start(), "A second player may not reset the recording timeout");
        long started = CivPerformanceRecorder.beginMeasured();
        time.addAndGet(5_000_000L);
        CivPerformanceRecorder.endMeasured("miner.tick", started);
        recorder.capture(120, 12);
        assertEquals(120, recorder.report().get("loadedHytaleEntities"));
        assertEquals(12, recorder.report().get("loadedCivResidents"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) recorder.report().get("systems");
        assertEquals(1L, rows.getFirst().get("calls"));
        assertEquals("miner.tick", rows.getFirst().get("system"));
        assertEquals(5.0, (Double) rows.getFirst().get("totalMs"), 0.0001);
        assertTrue(recorder.stop());
        assertFalse(recorder.isActive());
        assertEquals("manual", recorder.report().get("endReason"));
        assertTrue(recorder.start());
        time.addAndGet(901_000_000_000L);
        assertFalse(recorder.isActive(), "Recording expires without explicit stop");
        assertEquals("time_limit", recorder.report().get("endReason"));
        assertFalse(recorder.stop());
    }

    @Test
    void snapshotHistoryIsPaginatedAndProfilerBookkeepingIsSeparate() {
        AtomicLong time = new AtomicLong(100_000_000_000L);
        CivPerformanceRecorder recorder = new CivPerformanceRecorder(time::get);
        CivPerformanceRecorder.install(recorder);
        assertTrue(recorder.start());
        for (int second = 1; second <= 22; second++) {
            long begin = CivPerformanceRecorder.beginMeasured();
            time.addAndGet(2_000_000L);
            CivPerformanceRecorder.endMeasured("farmer.tick", begin);
            time.addAndGet(998_000_000L);
            recorder.capture(200 + second, 10);
        }
        Map<String, Object> page = recorder.samples(10, 1000);
        assertEquals(22, page.get("total"));
        assertEquals(10, ((List<?>) page.get("samples")).size());
        assertEquals(0, recorder.samples(-99, 5).get("offset"));
        assertTrue((Double) recorder.report().get("profilerBookkeepingMs") >= 0.0);
        assertTrue(recorder.stop());
    }

    @Test
    void intervalSamplesAndEventsAreBoundedAndShareTimeline() {
        AtomicLong time = new AtomicLong(1_000_000_000L);
        CivPerformanceRecorder recorder = new CivPerformanceRecorder(time::get);
        CivPerformanceRecorder.install(recorder);
        assertTrue(recorder.start());
        recorder.event("phase", "start");
        long started = CivPerformanceRecorder.beginMeasured();
        time.addAndGet(25_000_000L);
        CivPerformanceRecorder.endMeasured("miner.tick", started);
        time.addAndGet(975_000_000L);
        recorder.capture(30, 3);
        long next = CivPerformanceRecorder.beginMeasured();
        time.addAndGet(5_000_000L);
        CivPerformanceRecorder.endMeasured("miner.tick", next);
        time.addAndGet(995_000_000L);
        recorder.capture(30, 3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points =
            (List<Map<String, Object>>) recorder.samples(0, 10).get("samples");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> first =
            (List<Map<String, Object>>) points.get(0).get("systems");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> second =
            (List<Map<String, Object>>) points.get(1).get("systems");
        assertEquals(25.0, (Double) first.get(0).get("intervalMs"), 0.001);
        assertEquals(5.0, (Double) second.get(0).get("intervalMs"), 0.001);
        assertEquals(1L, second.get(0).get("intervalCalls"));
        assertEquals(2, recorder.events(0, 100).get("total"));
        assertEquals(1.0, (Double) points.get(0).get("intervalSeconds"), 0.001);
        assertTrue(recorder.stop());
        assertEquals(2, recorder.events(0, 100).get("total"));
    }

    @Test
    void measuresBaselineAndEnabledInstrumentationWithoutFlakyTimeThresholds() {
        final int iterations = 60_000;
        long ignored = 0;
        long baselineStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) ignored += i * 3L;
        long baselineNanos = System.nanoTime() - baselineStart;
        CivPerformanceRecorder recorder = new CivPerformanceRecorder();
        CivPerformanceRecorder.install(recorder);
        assertTrue(recorder.start());
        long profiledStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            long begin = CivPerformanceRecorder.beginMeasured();
            ignored += i * 3L;
            CivPerformanceRecorder.endMeasured("benchmark.work", begin);
        }
        long profiledNanos = System.nanoTime() - profiledStart;
        assertTrue(recorder.stop());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) recorder.report().get("systems");
        assertEquals(iterations, ((Number) rows.getFirst().get("calls")).intValue());
        assertTrue(profiledNanos >= 0);
        System.out.println("Civ profiler overhead comparison (non-gating): baseline ns="
            + baselineNanos + " instrumented ns=" + profiledNanos + " sink=" + ignored);
    }
}
