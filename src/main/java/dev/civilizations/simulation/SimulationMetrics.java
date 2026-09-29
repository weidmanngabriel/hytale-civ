package dev.civilizations.simulation;

import dev.civilizations.core.WorkDecisionSchedule;

/** Deterministic operation counts for headless gameplay scenarios. */
public final class SimulationMetrics {

    private long ticks;
    private long decisions;
    private long immediateDecisions;
    private long retryDecisions;
    private long treeSearches;
    private long constructionSearches;
    private long fieldSearches;
    private long movementRequests;
    private long failedPlans;
    private long treesFelled;
    private long constructionsCompleted;
    private long farmOutputsStored;

    void recordTick() {
        ticks++;
    }

    void recordDecision(WorkDecisionSchedule.DecisionKind kind) {
        if (kind == WorkDecisionSchedule.DecisionKind.NONE) {
            return;
        }
        decisions++;
        if (kind == WorkDecisionSchedule.DecisionKind.IMMEDIATE) {
            immediateDecisions++;
        }
        if (kind == WorkDecisionSchedule.DecisionKind.RETRY) {
            retryDecisions++;
        }
    }

    void recordTreeSearch() {
        treeSearches++;
    }

    void recordConstructionSearch() {
        constructionSearches++;
    }

    void recordFieldSearch() {
        fieldSearches++;
    }

    void recordMovementRequest() {
        movementRequests++;
    }

    void recordFailedPlan() {
        failedPlans++;
    }

    void recordTreeFelled() {
        treesFelled++;
    }

    void recordConstructionCompleted() {
        constructionsCompleted++;
    }

    void recordFarmOutputStored() {
        farmOutputsStored++;
    }

    public Snapshot snapshot() {
        return new Snapshot(
            ticks,
            decisions,
            immediateDecisions,
            retryDecisions,
            treeSearches,
            constructionSearches,
            fieldSearches,
            movementRequests,
            failedPlans,
            treesFelled,
            constructionsCompleted,
            farmOutputsStored
        );
    }

    public record Snapshot(
        long ticks,
        long decisions,
        long immediateDecisions,
        long retryDecisions,
        long treeSearches,
        long constructionSearches,
        long fieldSearches,
        long movementRequests,
        long failedPlans,
        long treesFelled,
        long constructionsCompleted,
        long farmOutputsStored
    ) {
    }
}
