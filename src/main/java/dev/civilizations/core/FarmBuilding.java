package dev.civilizations.core;

import java.util.Objects;

/**
 * Farm workplace state for a physical crop cycle.
 *
 * <p>The core owns the gameplay sequence while the Hytale adapter performs
 * planting, native growth observation, harvesting, movement and storage.
 */
public final class FarmBuilding {

    private final String id;
    private final BlockPosition entranceBlock;
    private final BlockPosition exitBlock;

    private String farmerId;
    private WorkState workState = WorkState.WAITING_FOR_FARMER;
    private boolean cropCycleActive;

    public FarmBuilding(String id, BlockPosition entranceBlock, BlockPosition exitBlock) {
        this.id = Objects.requireNonNull(id, "id");
        this.entranceBlock = Objects.requireNonNull(entranceBlock, "entranceBlock");
        this.exitBlock = Objects.requireNonNull(exitBlock, "exitBlock");
    }

    public synchronized boolean assignFarmer(String farmerId) {
        Objects.requireNonNull(farmerId, "farmerId");
        if (this.farmerId != null) return false;
        this.farmerId = farmerId;
        cropCycleActive = false;
        workState = WorkState.WALKING_TO_FARM;
        return true;
    }

    public synchronized void unassignFarmer() {
        farmerId = null;
        cropCycleActive = false;
        workState = WorkState.WAITING_FOR_FARMER;
    }

    public synchronized boolean arriveAtFarm() {
        if (workState == WorkState.WALKING_TO_FARM) {
            workState = WorkState.WALKING_TO_FIELD;
            return true;
        }
        if (workState == WorkState.RETURNING_TO_STORAGE) {
            workState = WorkState.STORING_OUTPUT;
            return true;
        }
        return false;
    }

    public synchronized boolean arriveAtField() {
        if (workState != WorkState.WALKING_TO_FIELD) return false;
        workState = cropCycleActive ? WorkState.HARVESTING_FIELD : WorkState.SOWING_FIELD;
        return true;
    }

    public synchronized boolean sowingComplete(boolean plantedAnyCrop) {
        if (workState != WorkState.SOWING_FIELD) return false;
        if (!plantedAnyCrop) {
            workState = WorkState.WAITING_FOR_INPUTS;
            return false;
        }
        cropCycleActive = true;
        workState = WorkState.WAITING_FOR_GROWTH;
        return true;
    }

    public synchronized boolean cropHarvested() {
        if (workState != WorkState.WAITING_FOR_GROWTH
            && workState != WorkState.HARVESTING_FIELD) {
            return false;
        }
        workState = WorkState.RETURNING_TO_STORAGE;
        return true;
    }

    public synchronized boolean noRipeCropYet() {
        if (workState != WorkState.HARVESTING_FIELD) return false;
        workState = WorkState.WAITING_FOR_GROWTH;
        return true;
    }

    public synchronized boolean fieldCycleFinished() {
        if (workState != WorkState.WAITING_FOR_GROWTH
            && workState != WorkState.HARVESTING_FIELD) {
            return false;
        }
        cropCycleActive = false;
        workState = WorkState.SOWING_FIELD;
        return true;
    }

    public synchronized boolean outputStored() {
        if (workState != WorkState.STORING_OUTPUT) return false;
        workState = WorkState.WALKING_TO_FIELD;
        return true;
    }

    public String id() { return id; }
    public BlockPosition entranceBlock() { return entranceBlock; }
    public BlockPosition exitBlock() { return exitBlock; }
    public synchronized String farmerId() { return farmerId; }
    public synchronized WorkState workState() { return workState; }

    public enum WorkState {
        WAITING_FOR_FARMER,
        WALKING_TO_FARM,
        WAITING_FOR_INPUTS,
        WALKING_TO_FIELD,
        SOWING_FIELD,
        WAITING_FOR_GROWTH,
        HARVESTING_FIELD,
        RETURNING_TO_STORAGE,
        STORING_OUTPUT
    }
}
