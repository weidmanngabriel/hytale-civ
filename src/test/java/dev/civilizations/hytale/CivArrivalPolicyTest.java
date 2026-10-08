package dev.civilizations.hytale;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CivArrivalPolicyTest {

    @Test
    void allowsNearbyEndpointWithoutRequiringExactMarkerCenter() {
        Vector3d goal = new Vector3d(20.5, 64, 30.5);
        assertTrue(CivArrivalPolicy.reached(new Vector3d(21.8, 64, 30.5), goal, 1.6, 1.0));
        assertFalse(CivArrivalPolicy.reached(new Vector3d(22.5, 64, 30.5), goal, 1.6, 1.0));
    }

    @Test
    void rejectsDifferentFloorEvenWhenHorizontallyCentered() {
        Vector3d goal = new Vector3d(20.5, 64, 30.5);
        assertFalse(CivArrivalPolicy.reached(new Vector3d(20.5, 67, 30.5), goal, 1.6, 1.0));
        assertFalse(CivArrivalPolicy.reached(new Vector3d(20.5, 62.5, 30.5), goal, 1.6, 1.0));
    }

    @Test
    void respectsTaskSpecificHorizontalAndVerticalLimits() {
        Vector3d goal = new Vector3d(10, 50, 10);
        Vector3d position = new Vector3d(11.2, 50.5, 10);
        assertTrue(CivArrivalPolicy.reached(position, goal, 1.25, 1.25));
        assertFalse(CivArrivalPolicy.reached(position, goal, 0.9, 1.25));
        assertFalse(CivArrivalPolicy.reached(position, goal, 1.25, 0.4));
        assertFalse(CivArrivalPolicy.reached(position, goal, -1, 1));
    }
}
