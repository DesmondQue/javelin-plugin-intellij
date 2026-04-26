package com.javelin.plugin.model;

import java.util.List;

public enum ConfidenceLevel {
    HIGH, MEDIUM, LOW, UNKNOWN;

    /**
     * Derives confidence from how much of the total suspicion score is concentrated
     * in the top-ranked group. A high fraction means the localization is focused.
     */
    public static ConfidenceLevel fromResults(List<LocalizationResult> results) {
        double fraction = topRankFraction(results);
        if (fraction <= 0.0) return UNKNOWN;
        if (fraction >= 0.5) return HIGH;
        if (fraction >= 0.25) return MEDIUM;
        return LOW;
    }

    /**
     * Returns the fraction of total score contributed by the top-ranked group,
     * or 0.0 for empty/zero-score result sets.
     */
    public static double topRankFraction(List<LocalizationResult> results) {
        if (results == null || results.isEmpty()) return 0.0;
        double totalScore = results.stream().mapToDouble(LocalizationResult::score).sum();
        if (totalScore <= 0.0) return 0.0;
        double minRank = results.stream().mapToDouble(LocalizationResult::rank).min().orElse(0.0);
        double topRankScore = results.stream()
                .filter(r -> r.rank() == minRank)
                .mapToDouble(LocalizationResult::score)
                .sum();
        return topRankScore / totalScore;
    }
}
