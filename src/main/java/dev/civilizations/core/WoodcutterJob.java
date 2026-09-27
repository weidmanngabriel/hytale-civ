package dev.civilizations.core;

import java.util.Objects;

/**
 * Hytale-independent state machine for one woodcutter work cycle.
 */
public final class WoodcutterJob {

    public static final double CHOP_SECONDS = 2.5;

    private BlockPosition targetTree;
    private double workElapsedSeconds;
    private WorkState state = WorkState.SEARCHING;

    public synchronized boolean assignTarget(BlockPosition targetTree) {
        Objects.requireNonNull(targetTree, "targetTree");
        if (state != WorkState.SEARCHING) {
            return false;
        }

        this.targetTree = targetTree;
        workElapsedSeconds = 0.0;
        state = WorkState.WALKING_TO_TREE;
        return true;
    }

    public synchronized boolean arriveAtTree() {
        if (state != WorkState.WALKING_TO_TREE) {
            return false;
        }

        workElapsedSeconds = 0.0;
        state = WorkState.CHOPPING;
        return true;
    }

    public synchronized boolean advanceWork(double deltaSeconds) {
        if (deltaSeconds < 0.0) {
            throw new IllegalArgumentException("deltaSeconds must be >= 0");
        }
        if (state != WorkState.CHOPPING) {
            return false;
        }

        workElapsedSeconds += deltaSeconds;
        if (workElapsedSeconds + 1.0e-9 < CHOP_SECONDS) {
            return false;
        }

        state = WorkState.READY_TO_FELL;
        return true;
    }

    public synchronized boolean completeFelling() {
        if (state != WorkState.READY_TO_FELL) {
            return false;
        }

        reset();
        return true;
    }

    public synchronized void abandonTarget() {
        reset();
    }

    private void reset() {
        targetTree = null;
        workElapsedSeconds = 0.0;
        state = WorkState.SEARCHING;
    }

    public synchronized BlockPosition targetTree() {
        return targetTree;
    }

    public synchronized double workElapsedSeconds() {
        return workElapsedSeconds;
    }

    public synchronized WorkState state() {
        return state;
    }

    public enum WorkState {
        SEARCHING,
        WALKING_TO_TREE,
        CHOPPING,
        READY_TO_FELL
    }
}
