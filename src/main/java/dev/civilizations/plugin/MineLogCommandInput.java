package dev.civilizations.plugin;

import java.util.Locale;

final class MineLogCommandInput {

    private MineLogCommandInput() {
    }

    static String categoriesArgument(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        int onIndex = lower.lastIndexOf(" on");
        if (onIndex >= 0) {
            String trailing = trimmed.substring(onIndex + 3).trim();
            return trailing.isEmpty() ? null : trailing;
        }
        if (!trimmed.contains(" ")) return trimmed;
        return null;
    }
}
