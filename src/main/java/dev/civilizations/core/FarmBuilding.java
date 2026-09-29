package dev.civilizations.core;

import java.util.Map;
import java.util.Objects;

/**
 * Farm workplace using the shared production state machine.
 */
public final class FarmBuilding {

    public static final ProductionRecipe WHEAT_RECIPE = new ProductionRecipe(
        "farm_wheat",
        Map.of(),
        Map.of("wheat", 1),
        5.0
    );

    private final String id;
    private final BlockPosition entranceBlock;
    private final BlockPosition exitBlock;
    private final ProductionJob production = new ProductionJob(WHEAT_RECIPE);

    private String farmerId;

    public FarmBuilding(String id, BlockPosition entranceBlock, BlockPosition exitBlock) {
        this.id = Objects.requireNonNull(id, "id");
        this.entranceBlock = Objects.requireNonNull(entranceBlock, "entranceBlock");
        this.exitBlock = Objects.requireNonNull(exitBlock, "exitBlock");
    }

    public synchronized boolean assignFarmer(String farmerId) {
        Objects.requireNonNull(farmerId, "farmerId");
        if (this.farmerId != null) return false;
        if (!production.start()) return false;
        this.farmerId = farmerId;
        return true;
    }

    public synchronized void unassignFarmer() {
        farmerId = null;
        production.stop();
    }

    public synchronized boolean arriveAtFarm() {
        return production.workplaceReached();
    }

    public synchronized boolean arriveAtField() {
        return production.workAreaReached();
    }

    public synchronized boolean advanceWork(double deltaSeconds) {
        return production.advanceWork(deltaSeconds, 1.0);
    }

    public synchronized boolean outputStored() {
        return production.outputStored();
    }

    public String id() { return id; }
    public BlockPosition entranceBlock() { return entranceBlock; }
    public BlockPosition exitBlock() { return exitBlock; }
    public synchronized String farmerId() { return farmerId; }
    public synchronized double workElapsedSeconds() { return production.workElapsedSeconds(); }
    public synchronized WorkState workState() {
        return switch (production.state()) {
            case IDLE -> WorkState.WAITING_FOR_FARMER;
            case TRAVELLING_TO_WORKPLACE -> WorkState.WALKING_TO_FARM;
            case WAITING_FOR_INPUTS -> WorkState.WAITING_FOR_INPUTS;
            case TRAVELLING_TO_WORK_AREA -> WorkState.WALKING_TO_FIELD;
            case WORKING -> WorkState.WORKING_FIELD;
            case CARRYING_OUTPUT -> WorkState.RETURNING_TO_STORAGE;
            case STORING_OUTPUT -> WorkState.STORING_OUTPUT;
        };
    }

    public enum WorkState {
        WAITING_FOR_FARMER,
        WALKING_TO_FARM,
        WAITING_FOR_INPUTS,
        WALKING_TO_FIELD,
        WORKING_FIELD,
        RETURNING_TO_STORAGE,
        STORING_OUTPUT
    }
}
