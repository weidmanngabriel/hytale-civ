package dev.civilizations.scenario;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.WorldPosition;

import java.util.List;

/**
 * Shared deterministic fixture for the basic woodcutter flow.
 *
 * <p>The simulator and the real Hytale runtime adapter consume the same coordinates. Engine
 * specifics such as flat-world generation, prefab placement and native harvesting stay outside
 * this pure-Java scenario definition.</p>
 */
public final class WoodcutterBasicScenario {

    public static final String ID = "woodcutter-basic";
    public static final String DISPLAY_NAME = "Woodcutter Basic";
    public static final String TEST_WORLD_NAME = "civ-test-woodcutter-basic";

    /** Flat-world ground occupies y=0; entities stand at y=1. */
    public static final WorldPosition WOODCUTTER_START = new WorldPosition(0.5, 1.0, 0.5);

    /** Trunk bases for the deterministic fixture. */
    public static final List<BlockPosition> TREE_BASES = List.of(
        new BlockPosition(6, 1, 0),
        new BlockPosition(10, 1, 4),
        new BlockPosition(14, 1, -3)
    );

    private WoodcutterBasicScenario() {
    }
}
