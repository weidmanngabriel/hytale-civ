package dev.civilizations.hytale;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Defines the visible construction order for occupied prefab Y-layers.
 *
 * <p>Ordinary prefabs keep the legacy bottom-to-top order. Prefabs that author a
 * semantic construction ground level build the surface and everything above it
 * first, then continue below ground from top to bottom.</p>
 */
final class PrefabConstructionOrder {

    private PrefabConstructionOrder() {
    }

    static List<Integer> order(Collection<Integer> occupiedLayers, Integer groundLevelY) {
        TreeSet<Integer> sorted = new TreeSet<>(occupiedLayers);
        if (groundLevelY == null) {
            return List.copyOf(sorted);
        }

        List<Integer> ordered = new ArrayList<>(sorted.size());
        sorted.stream()
            .filter(y -> y >= groundLevelY)
            .forEach(ordered::add);
        sorted.stream()
            .filter(y -> y < groundLevelY)
            .sorted(Comparator.reverseOrder())
            .forEach(ordered::add);
        return List.copyOf(ordered);
    }
}
