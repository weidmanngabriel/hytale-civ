package dev.civilizations.scenario;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.WorldPosition;

/** Shared deterministic start fixture for headless and future Hytale miner scenarios. */
public final class MinerBasicScenario {

    public static final String ID = "miner-basic";
    public static final String DISPLAY_NAME = "Miner – Single Segment";
    public static final String MINER_ID = "miner-1";
    public static final WorldPosition MINER_START = new WorldPosition(11.5, 20.0, 33.5);
    public static final BlockPosition TUNNEL_START = new BlockPosition(10, 20, 30);
    public static final MineDirection DIRECTION = MineDirection.NORTH;

    private MinerBasicScenario() {
    }
}
