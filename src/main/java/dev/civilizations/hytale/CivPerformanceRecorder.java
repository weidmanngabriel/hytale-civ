package dev.civilizations.hytale;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

/**
 * Opt-in, server-wide bounded runtime profiler. The default (inactive) instrumentation
 * performs only a volatile reference check. No gameplay search is repeated by this class.
 */
public final class CivPerformanceRecorder {
    public static final int MAX_SECONDS = 15 * 60;
    private static final int MAX_EVENTS = 2048;
    private static final long MAX_NANOS = MAX_SECONDS * 1_000_000_000L;
    private static volatile CivPerformanceRecorder installed;

    private final LongSupplier nanoClock;
    private volatile Session active;
    private volatile Report lastReport;

    public CivPerformanceRecorder() { this(System::nanoTime); }
    public CivPerformanceRecorder(LongSupplier nanoClock) { this.nanoClock = nanoClock; }

    /** Connects existing ECS systems without inserting recorder state into gameplay logic. */
    public static void install(CivPerformanceRecorder recorder) { installed = recorder; }
    public static void uninstall(CivPerformanceRecorder recorder) {
        if (installed == recorder) installed = null;
    }

    public static CivPerformanceRecorder current() { return installed; }

    public static long beginMeasured() {
        CivPerformanceRecorder recorder = installed;
        return recorder == null || recorder.active == null ? 0 : recorder.nanoClock.getAsLong();
    }

    public static void endMeasured(String category, long startedNanos) {
        if (startedNanos == 0) return;
        CivPerformanceRecorder recorder = installed;
        if (recorder != null) recorder.measure(category, startedNanos);
    }

    /** Optional bounded, timestamped marker emitted only during an active recording. */
    public synchronized void event(String type, String detail) {
        expireIfNeeded();
        Session s = active;
        if (s == null || s.events.size() >= MAX_EVENTS) return;
        String kind = sanitize(type, 64);
        String message = sanitize(detail, 160);
        if (kind.isEmpty()) return;
        double elapsed = Math.max(0, nanoClock.getAsLong() - s.startedNanos) / 1_000_000_000.0;
        s.events.add(Map.of("elapsedSeconds", elapsed, "type", kind, "detail", message));
    }

    private static String sanitize(String value, int max) {
        if (value == null) return "";
        return value.replaceAll("[\\\\p{Cntrl}]", " ").strip().substring(0, Math.min(max, value.strip().length()));
    }

    /** Events share the same monotonic timeline as system samples. */
    public synchronized Map<String, Object> events(int offset, int count) {
        expireIfNeeded();
        List<Map<String, Object>> values = active != null ? active.events
            : lastReport == null ? List.of() : lastReport.events;
        int from = Math.max(0, Math.min(offset, values.size()));
        int to = Math.min(values.size(), from + Math.max(1, Math.min(100, count)));
        return Map.of("total", values.size(), "offset", from, "events",
            List.copyOf(values.subList(from, to)));
    }

    public synchronized boolean start() {
        expireIfNeeded();
        if (active != null) return false;
        active = new Session(nanoClock.getAsLong(), Instant.now().toString());
        return true;
    }

    public synchronized boolean stop() {
        expireIfNeeded();
        if (active == null) return false;
        finish(active, nanoClock.getAsLong(), "manual");
        return true;
    }

    public boolean isActive() { return statusActive(); }

    private synchronized boolean statusActive() {
        expireIfNeeded();
        return active != null;
    }

    /**
     * This is the native entity-store snapshot, not a second query for NPCs.
     * Called by the existing player ECS refresh at most once per second.
     */
    /** Avoid repeating a loaded-Civ enumeration once per player in multiplayer. */
    public synchronized boolean needsCapture() {
        expireIfNeeded();
        Session s = active;
        return s != null && Math.max(0,
            (nanoClock.getAsLong() - s.startedNanos) / 1_000_000_000L) != s.lastSecond;
    }

    public synchronized void capture(int loadedEntities, int loadedCivResidents) {
        expireIfNeeded();
        Session s = active;
        if (s == null) return;
        long elapsed = Math.max(0, (nanoClock.getAsLong() - s.startedNanos) / 1_000_000_000L);
        if (elapsed == s.lastSecond) return;
        s.lastSecond = elapsed;
        s.loadedEntities = Math.max(0, loadedEntities);
        s.loadedCivResidents = Math.max(0, loadedCivResidents);
        if (s.samples.size() < MAX_SECONDS) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("elapsedSeconds", elapsed);
            row.put("loadedHytaleEntities", s.loadedEntities);
            row.put("loadedCivResidents", s.loadedCivResidents);
            List<Map<String, Object>> interval = new ArrayList<>();
            for (Map<String, Object> cumulative : systemStats(s)) {
                String key = (String) cumulative.get("system");
                double total = (Double) cumulative.get("totalMs");
                long calls = (Long) cumulative.get("calls");
                double previousTotal = s.previousMs.getOrDefault(key, 0.0);
                long previousCalls = s.previousCalls.getOrDefault(key, 0L);
                interval.add(Map.of("system", key, "intervalMs", Math.max(0.0, total - previousTotal),
                    "intervalCalls", Math.max(0L, calls - previousCalls)));
                s.previousMs.put(key, total);
                s.previousCalls.put(key, calls);
            }
            row.put("intervalSeconds", s.previousCaptureSeconds < 0 ? elapsed : elapsed - s.previousCaptureSeconds);
            s.previousCaptureSeconds = elapsed;
            row.put("systems", List.copyOf(interval));
            s.samples.add(Map.copyOf(row));
        }
    }

    public synchronized Map<String, Object> status() {
        expireIfNeeded();
        Session s = active;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("active", s != null);
        data.put("maxDurationSeconds", MAX_SECONDS);
        data.put("hasPreviousReport", lastReport != null);
        if (s != null) {
            long elapsed = Math.min(MAX_SECONDS, Math.max(0,
                (nanoClock.getAsLong() - s.startedNanos) / 1_000_000_000L));
            data.put("startedAt", s.startedAt);
            data.put("elapsedSeconds", elapsed);
            data.put("remainingSeconds", MAX_SECONDS - elapsed);
            data.put("sampleCount", s.samples.size());
            data.put("loadedHytaleEntities", s.loadedEntities);
            data.put("loadedCivResidents", s.loadedCivResidents);
        }
        return data;
    }

    /** Current (if recording) or most recent finished aggregate. */
    public synchronized Map<String, Object> report() {
        expireIfNeeded();
        if (active != null) return reportOf(active, nanoClock.getAsLong(), "recording");
        if (lastReport != null) return lastReport.summary;
        return Map.of("available", false);
    }

    /** Bounded output suitable for GitHub Actions logs and the existing command bridge. */
    public synchronized Map<String, Object> samples(int offset, int count) {
        expireIfNeeded();
        List<Map<String, Object>> values = active == null
            ? lastReport == null ? List.of() : lastReport.samples
            : active.samples;
        int from = Math.max(0, Math.min(offset, values.size()));
        int to = Math.min(values.size(), from + Math.max(1, Math.min(10, count)));
        return Map.of("total", values.size(), "offset", from, "samples",
            List.copyOf(values.subList(from, to)));
    }

    /** Tracks HUD/snapshot overhead in addition to per-operation aggregation. */
    public void recordProfilerOverhead(long nanos) {
        Session s = active;
        if (s != null && nanos >= 0) s.bookkeepingNanos.add(nanos);
    }

    private void measure(String key, long start) {
        Session s = active;
        if (s == null) return;
        long after = nanoClock.getAsLong();
        if (after - s.startedNanos >= MAX_NANOS) {
            synchronized (this) { expireIfNeeded(); }
            return;
        }
        long beganBookkeeping = after;
        long duration = Math.max(0, after - start);
        s.metrics.computeIfAbsent(key, ignored -> new Metric()).add(duration);
        s.bookkeepingNanos.add(Math.max(0, nanoClock.getAsLong() - beganBookkeeping));
    }

    private void expireIfNeeded() {
        Session s = active;
        if (s != null && nanoClock.getAsLong() - s.startedNanos >= MAX_NANOS) {
            finish(s, nanoClock.getAsLong(), "time_limit");
        }
    }

    private void finish(Session s, long ended, String reason) {
        Map<String, Object> summary = reportOf(s, ended, reason);
        lastReport = new Report(summary, List.copyOf(s.samples), List.copyOf(s.events));
        active = null;
    }

    private static Map<String, Object> reportOf(Session s, long now, String reason) {
        double seconds = Math.max(0.001, (Math.min(now - s.startedNanos, MAX_NANOS)) / 1_000_000_000.0);
        long measured = s.metrics.values().stream().mapToLong(x -> x.nanoseconds.sum()).sum();
        long overhead = s.bookkeepingNanos.sum();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("available", true);
        map.put("schema", "hytale-civ-performance");
        map.put("version", 1);
        map.put("startedAt", s.startedAt);
        map.put("durationSeconds", seconds);
        map.put("endReason", reason);
        map.put("maxDurationSeconds", MAX_SECONDS);
        map.put("sampleCount", s.samples.size());
        map.put("eventCount", s.events.size());
        map.put("loadedHytaleEntities", s.loadedEntities);
        map.put("loadedCivResidents", s.loadedCivResidents);
        map.put("profilerBookkeepingMs", overhead / 1_000_000.0);
        map.put("profilerBookkeepingMsPerSecond", overhead / 1_000_000.0 / seconds);
        map.put("profilerShareOfMeasuredTimePercent", measured + overhead == 0
            ? 0.0 : 100.0 * overhead / (measured + overhead));
        map.put("systems", systemStats(s, seconds));
        return map;
    }

    private static List<Map<String, Object>> systemStats(Session s) {
        return systemStats(s, Math.max(0.001, s.lastSecond));
    }

    private static List<Map<String, Object>> systemStats(Session s, double seconds) {
        return s.metrics.entrySet().stream()
            .map(entry -> {
                Metric metric = entry.getValue();
                long count = metric.count.sum();
                long nanos = metric.nanoseconds.sum();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("system", entry.getKey());
                row.put("calls", count);
                row.put("callsPerSecond", count / seconds);
                row.put("totalMs", nanos / 1_000_000.0);
                row.put("msPerSecond", nanos / 1_000_000.0 / seconds);
                row.put("averageMs", count == 0 ? 0.0 : nanos / (1_000_000.0 * count));
                row.put("maxMs", metric.maximum.get() / 1_000_000.0);
                row.put("p95ApproxMs", metric.p95Micros() / 1000.0);
                return Map.copyOf(row);
            })
            .sorted(Comparator.comparingDouble((Map<String, Object> row) ->
                (double) row.get("totalMs")).reversed())
            .toList();
    }

    private static final class Session {
        final long startedNanos;
        final String startedAt;
        final ConcurrentHashMap<String, Metric> metrics = new ConcurrentHashMap<>();
        final LongAdder bookkeepingNanos = new LongAdder();
        final List<Map<String, Object>> samples = new ArrayList<>();
        final List<Map<String, Object>> events = new ArrayList<>();
        final Map<String, Double> previousMs = new LinkedHashMap<>();
        final Map<String, Long> previousCalls = new LinkedHashMap<>();
        long previousCaptureSeconds = -1;
        int loadedEntities;
        int loadedCivResidents;
        long lastSecond = -1;
        Session(long startedNanos, String startedAt) {
            this.startedNanos = startedNanos;
            this.startedAt = startedAt;
        }
    }

    private static final class Metric {
        final LongAdder count = new LongAdder();
        final LongAdder nanoseconds = new LongAdder();
        final LongAccumulator maximum = new LongAccumulator(Long::max, 0);
        final AtomicLongArray buckets = new AtomicLongArray(40);
        void add(long nanos) {
            count.increment();
            nanoseconds.add(nanos);
            maximum.accumulate(nanos);
            long micros = Math.max(1, nanos / 1000);
            int index = Math.min(39, 63 - Long.numberOfLeadingZeros(micros));
            buckets.incrementAndGet(index);
        }
        long p95Micros() {
            long total = count.sum();
            if (total == 0) return 0;
            long threshold = (long) Math.ceil(total * 0.95);
            long reached = 0;
            for (int i = 0; i < buckets.length(); i++) {
                reached += buckets.get(i);
                if (reached >= threshold) return 1L << (i + 1);
            }
            return 1L << 40;
        }
    }

    private record Report(Map<String, Object> summary, List<Map<String, Object>> samples,
                          List<Map<String, Object>> events) {}
}
