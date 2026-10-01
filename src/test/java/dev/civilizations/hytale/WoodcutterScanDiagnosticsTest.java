package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WoodcutterScanDiagnosticsTest {

    @Test
    void recordsOnlyWhileEnabledAndAggregatesRuntimeSamples() {
        WoodcutterScanDiagnostics diagnostics = new WoodcutterScanDiagnostics();

        diagnostics.record(1_000_000L, 10, 1, 1, 12, 1);
        assertEquals(0, diagnostics.snapshot().scans());

        WoodcutterScanDiagnostics.ToggleResult enabled = diagnostics.toggle();
        assertTrue(enabled.enabled());

        diagnostics.record(2_000_000L, 100, 2, 2, 40, 2);
        diagnostics.record(4_000_000L, 200, 3, 3, 60, 3);

        WoodcutterScanDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(2, snapshot.scans());
        assertEquals(3.0, snapshot.averageMillis(), 1.0e-9);
        assertEquals(4.0, snapshot.maxMillis(), 1.0e-9);
        assertEquals(300, snapshot.positionsChecked());
        assertEquals(5, snapshot.treeBases());
        assertEquals(5, snapshot.treesCollected());
        assertEquals(100, snapshot.treeBlocksCollected());
        assertEquals(5, snapshot.workTargetChecks());

        WoodcutterScanDiagnostics.ToggleResult disabled = diagnostics.toggle();
        assertFalse(disabled.enabled());
        assertEquals(2, disabled.snapshot().scans());
    }

    @Test
    void enablingAgainResetsPreviousMeasurementWindow() {
        WoodcutterScanDiagnostics diagnostics = new WoodcutterScanDiagnostics();
        diagnostics.toggle();
        diagnostics.record(2_000_000L, 100, 2, 2, 40, 2);
        diagnostics.toggle();

        WoodcutterScanDiagnostics.ToggleResult enabledAgain = diagnostics.toggle();

        assertTrue(enabledAgain.enabled());
        assertEquals(0, enabledAgain.snapshot().scans());
        assertEquals(0, enabledAgain.snapshot().positionsChecked());
    }
}
