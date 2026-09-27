package dev.civilizations.core;

import java.util.Objects;

/**
 * Minimal farm workplace for the first production vertical slice.
 */
public final class FarmBuilding {

    public static final int WHEAT_TARGET = 10;
    public static final double PRODUCTION_SECONDS = 5.0;

    private final String id;
    private final BlockPosition entranceBlock;
    private final BlockPosition exitBlock;

    private String farmerId;
    private int wheat;
    private double workElapsedSeconds;
    private WorkState workState = WorkState.WAITING_FOR_FARMER;

    public FarmBuilding(String id, BlockPosition entranceBlock, BlockPosition exitBlock) {
        this.id = Objects.requireNonNull(id, "id");
        this.entranceBlock = Objects.requireNonNull(entranceBlock, "entranceBlock");
        this.exitBlock = Objects.requireNonNull(exitBlock, "exitBlock");
    }

    public synchronized boolean assignFarmer(String farmerId) {
        Objects.requireNonNull(farmerId, "farmerId");

        if (this.farmerId != null || workState == WorkState.COMPLETE) {
            return false;
        }

        this.farmerId = farmerId;
        workElapsedSeconds = 0.0;
        workState = WorkState.WALKING_TO_ENTRANCE;
        return true;
    }

    public synchronized void unassignFarmer() {
        farmerId = null;
        workElapsedSeconds = 0.0;
        workState = wheat >= WHEAT_TARGET
            ? WorkState.COMPLETE
            : WorkState.WAITING_FOR_FARMER;
    }

    public synchronized boolean enterBuilding() {
        if (workState != WorkState.WALKING_TO_ENTRANCE) {
            return false;
        }

        workElapsedSeconds = 0.0;
        workState = WorkState.WORKING_INSIDE;
        return true;
    }

    public synchronized boolean advanceWork(double deltaSeconds) {
        if (deltaSeconds < 0.0) {
            throw new IllegalArgumentException("deltaSeconds must be >= 0");
        }

        if (workState != WorkState.WORKING_INSIDE) {
            return false;
        }

        workElapsedSeconds += deltaSeconds;
        if (workElapsedSeconds + 1.0e-9 < PRODUCTION_SECONDS) {
            return false;
        }

        wheat++;
        workElapsedSeconds = 0.0;
        workState = WorkState.LEAVING_BUILDING;
        return true;
    }

    public synchronized boolean exitBuilding() {
        if (workState != WorkState.LEAVING_BUILDING) {
            return false;
        }

        workState = wheat >= WHEAT_TARGET
            ? WorkState.COMPLETE
            : WorkState.WALKING_TO_ENTRANCE;
        return true;
    }

    public String id() {
        return id;
    }

    public BlockPosition entranceBlock() {
        return entranceBlock;
    }

    public BlockPosition exitBlock() {
        return exitBlock;
    }

    public synchronized String farmerId() {
        return farmerId;
    }

    public synchronized int wheat() {
        return wheat;
    }

    public synchronized double workElapsedSeconds() {
        return workElapsedSeconds;
    }

    public synchronized WorkState workState() {
        return workState;
    }

    public enum WorkState {
        WAITING_FOR_FARMER,
        WALKING_TO_ENTRANCE,
        WORKING_INSIDE,
        LEAVING_BUILDING,
        COMPLETE
    }
}
