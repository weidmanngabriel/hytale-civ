package dev.civilizations.hytale;

/** Presentation snapshot for the currently inspected building or construction site. */
public record BuildingInfoSnapshot(
    String name,
    String phase,
    String status,
    String progress,
    String workers
) {
    public BuildingInfoSnapshot {
        name = safe(name, "Gebäude");
        phase = safe(phase, "");
        status = safe(status, "");
        progress = safe(progress, "");
        workers = safe(workers, "");
    }

    private static String safe(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
