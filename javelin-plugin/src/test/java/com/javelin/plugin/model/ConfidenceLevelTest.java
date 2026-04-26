package com.javelin.plugin.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfidenceLevelTest {

    private static StatementResult sr(double score, double rank) {
        return new StatementResult("com.example.X", 1, score, rank);
    }

    // ── fromResults ───────────────────────────────────────────────────────

    @Test
    void unknownForEmptyList() {
        assertEquals(ConfidenceLevel.UNKNOWN, ConfidenceLevel.fromResults(List.of()));
    }

    @Test
    void unknownForNullList() {
        assertEquals(ConfidenceLevel.UNKNOWN, ConfidenceLevel.fromResults(null));
    }

    @Test
    void unknownWhenAllScoresAreZero() {
        assertEquals(ConfidenceLevel.UNKNOWN,
                ConfidenceLevel.fromResults(List.of(sr(0.0, 1.0), sr(0.0, 2.0))));
    }

    @Test
    void highWhenTopRankDominates() {
        // top rank score = 0.6, total = 1.0 → fraction 0.6 ≥ 0.5 → HIGH
        List<LocalizationResult> results = List.of(sr(0.6, 1.0), sr(0.4, 2.0));
        assertEquals(ConfidenceLevel.HIGH, ConfidenceLevel.fromResults(results));
    }

    @Test
    void highAtExactBoundary() {
        // fraction = 0.5 exactly → HIGH
        List<LocalizationResult> results = List.of(sr(0.5, 1.0), sr(0.5, 2.0));
        assertEquals(ConfidenceLevel.HIGH, ConfidenceLevel.fromResults(results));
    }

    @Test
    void singleResultAlwaysHigh() {
        // 100 % of suspicion is in the single top-ranked entry
        assertEquals(ConfidenceLevel.HIGH, ConfidenceLevel.fromResults(List.of(sr(0.8, 1.0))));
    }

    @Test
    void mediumWhenTopRankIsModerate() {
        // fraction = 0.3, 0.25 ≤ 0.3 < 0.5 → MEDIUM
        List<LocalizationResult> results = List.of(sr(0.3, 1.0), sr(0.7, 2.0));
        assertEquals(ConfidenceLevel.MEDIUM, ConfidenceLevel.fromResults(results));
    }

    @Test
    void mediumAtLowerBoundary() {
        // fraction = 0.25 exactly → MEDIUM
        List<LocalizationResult> results = List.of(sr(0.25, 1.0), sr(0.75, 2.0));
        assertEquals(ConfidenceLevel.MEDIUM, ConfidenceLevel.fromResults(results));
    }

    @Test
    void lowWhenSuspicionIsSpread() {
        // fraction = 0.1 < 0.25 → LOW
        List<LocalizationResult> results = List.of(sr(0.1, 1.0), sr(0.9, 2.0));
        assertEquals(ConfidenceLevel.LOW, ConfidenceLevel.fromResults(results));
    }

    // ── topRankFraction ───────────────────────────────────────────────────

    @Test
    void topRankFractionForEmptyList() {
        assertEquals(0.0, ConfidenceLevel.topRankFraction(List.of()), 1e-9);
    }

    @Test
    void topRankFractionForNullList() {
        assertEquals(0.0, ConfidenceLevel.topRankFraction(null), 1e-9);
    }

    @Test
    void topRankFractionForSingleResult() {
        assertEquals(1.0, ConfidenceLevel.topRankFraction(List.of(sr(0.7, 1.0))), 1e-9);
    }

    @Test
    void topRankFractionAggregatesAllTopRankEntries() {
        // Two items tied at average rank 1.5, one lower-ranked item at rank 3.0
        // top rank score = 0.3 + 0.3 = 0.6, total = 1.0 → fraction = 0.6
        List<LocalizationResult> results = List.of(sr(0.3, 1.5), sr(0.3, 1.5), sr(0.4, 3.0));
        assertEquals(0.6, ConfidenceLevel.topRankFraction(results), 1e-9);
    }

    @Test
    void topRankFractionForAllZeroScores() {
        assertEquals(0.0, ConfidenceLevel.topRankFraction(List.of(sr(0.0, 1.0))), 1e-9);
    }

    @Test
    void topRankFractionIsOneWhenAllAtSameRank() {
        // All items share rank 1 → fraction must be 1.0
        List<LocalizationResult> results = List.of(sr(0.4, 1.0), sr(0.3, 1.0), sr(0.3, 1.0));
        assertEquals(1.0, ConfidenceLevel.topRankFraction(results), 1e-9);
    }
}
