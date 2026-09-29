package dev.civilizations.core;

import java.util.Objects;

/**
 * Hytale-independent production state machine.
 *
 * <p>The core decides which semantic phase comes next. Adapters decide where
 * inputs are found, how workers move, how world work is performed, and which
 * native container receives the output.
 */
public final class ProductionJob {

    private final ProductionRecipe recipe;
    private State state = State.IDLE;
    private double workElapsedSeconds;

    public ProductionJob(ProductionRecipe recipe) {
        this.recipe = Objects.requireNonNull(recipe, "recipe");
    }

    public synchronized boolean start() {
        if (state != State.IDLE) return false;
        state = State.TRAVELLING_TO_WORKPLACE;
        return true;
    }

    public synchronized boolean workplaceReached() {
        if (state == State.TRAVELLING_TO_WORKPLACE) {
            state = recipe.inputs().isEmpty()
                ? State.TRAVELLING_TO_WORK_AREA
                : State.WAITING_FOR_INPUTS;
            return true;
        }
        if (state == State.CARRYING_OUTPUT) {
            state = State.STORING_OUTPUT;
            return true;
        }
        return false;
    }

    public synchronized boolean inputsReady() {
        if (state != State.WAITING_FOR_INPUTS) return false;
        state = State.TRAVELLING_TO_WORK_AREA;
        return true;
    }

    public synchronized boolean workAreaReached() {
        if (state != State.TRAVELLING_TO_WORK_AREA) return false;
        workElapsedSeconds = 0.0;
        state = State.WORKING;
        return true;
    }

    public synchronized boolean advanceWork(double deltaSeconds, double speedMultiplier) {
        if (deltaSeconds < 0.0 || !Double.isFinite(deltaSeconds)) {
            throw new IllegalArgumentException("deltaSeconds must be finite and >= 0");
        }
        if (!(speedMultiplier > 0.0) || !Double.isFinite(speedMultiplier)) {
            throw new IllegalArgumentException("speedMultiplier must be finite and > 0");
        }
        if (state != State.WORKING) return false;
        workElapsedSeconds += deltaSeconds * speedMultiplier;
        if (workElapsedSeconds + 1.0e-9 < recipe.baseWorkSeconds()) return false;
        workElapsedSeconds = 0.0;
        state = State.CARRYING_OUTPUT;
        return true;
    }

    public synchronized boolean outputStored() {
        if (state != State.STORING_OUTPUT) return false;
        state = recipe.inputs().isEmpty()
            ? State.TRAVELLING_TO_WORK_AREA
            : State.WAITING_FOR_INPUTS;
        return true;
    }

    public synchronized void stop() {
        state = State.IDLE;
        workElapsedSeconds = 0.0;
    }

    public ProductionRecipe recipe() { return recipe; }
    public synchronized State state() { return state; }
    public synchronized double workElapsedSeconds() { return workElapsedSeconds; }

    public enum State {
        IDLE,
        TRAVELLING_TO_WORKPLACE,
        WAITING_FOR_INPUTS,
        TRAVELLING_TO_WORK_AREA,
        WORKING,
        CARRYING_OUTPUT,
        STORING_OUTPUT
    }
}
