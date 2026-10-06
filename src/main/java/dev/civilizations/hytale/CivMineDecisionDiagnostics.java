package dev.civilizations.hytale;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.civilizations.core.MineDecisionCategory;
import dev.civilizations.core.MineDecisionEvent;
import dev.civilizations.core.MineDecisionSink;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Opt-in mine decision diagnostics backed by Hytale's plugin logger.
 *
 * <p>The filter is immutable between updates so gameplay callers only pay a cheap category check
 * while diagnostics are disabled. The sink never owns or mutates mine gameplay state.</p>
 */
public final class CivMineDecisionDiagnostics implements MineDecisionSink {

    private final HytaleLogger logger;
    private volatile Set<MineDecisionCategory> enabledCategories;

    public CivMineDecisionDiagnostics(HytaleLogger logger, boolean enabledByDefault) {
        if (logger == null) throw new IllegalArgumentException("logger must not be null");
        this.logger = logger;
        enabledCategories = enabledByDefault
            ? immutable(EnumSet.allOf(MineDecisionCategory.class))
            : Set.of();
    }

    @Override
    public boolean enabled(MineDecisionCategory category) {
        return category != null && enabledCategories.contains(category);
    }

    @Override
    public void record(MineDecisionEvent event) {
        if (event == null || !enabled(event.category())) return;
        logger.atInfo().log("%s", event.format());
    }

    public synchronized Set<MineDecisionCategory> enableAll() {
        enabledCategories = immutable(EnumSet.allOf(MineDecisionCategory.class));
        return enabledCategories;
    }

    public synchronized Set<MineDecisionCategory> enable(Set<MineDecisionCategory> categories) {
        if (categories == null || categories.isEmpty()) return enableAll();
        enabledCategories = immutable(EnumSet.copyOf(categories));
        return enabledCategories;
    }

    public synchronized void disable() {
        enabledCategories = Set.of();
    }

    public Set<MineDecisionCategory> enabledCategories() {
        return enabledCategories;
    }

    public boolean anyEnabled() {
        return !enabledCategories.isEmpty();
    }

    public String statusSummary() {
        if (!anyEnabled()) return "disabled";
        return "enabled categories=" + enabledCategories.stream()
            .map(Enum::name)
            .sorted()
            .collect(Collectors.joining(","));
    }

    public static Set<MineDecisionCategory> parseCategories(String raw) {
        if (raw == null || raw.isBlank()) {
            return EnumSet.allOf(MineDecisionCategory.class);
        }
        EnumSet<MineDecisionCategory> categories = EnumSet.noneOf(MineDecisionCategory.class);
        for (String token : raw.split(",")) {
            String normalized = token.trim();
            if (normalized.isEmpty()) continue;
            try {
                categories.add(MineDecisionCategory.valueOf(normalized.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                    "Unknown mine log category '" + normalized + "'. Available: " + availableCategories(),
                    exception
                );
            }
        }
        if (categories.isEmpty()) throw new IllegalArgumentException("No mine log categories supplied.");
        return categories;
    }

    public static String availableCategories() {
        return java.util.Arrays.stream(MineDecisionCategory.values())
            .map(Enum::name)
            .collect(Collectors.joining(","));
    }

    private static Set<MineDecisionCategory> immutable(EnumSet<MineDecisionCategory> categories) {
        return Collections.unmodifiableSet(EnumSet.copyOf(categories));
    }
}
