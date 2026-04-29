package com.javelin.plugin.model;

import java.util.HashMap;
import java.util.Map;

public record RunStats(
        int totalTests,
        int passedTests,
        int failedTests,
        int linesTracked,
        int linesCovered,
        long testExecMs,
        long ochiaiMs,
        long mutationMs,
        int mutantsTotal,
        int mutantsKilled,
        int mutantsSurvived,
        int mutantsNoCoverage
) {

    public boolean hasMutationData() {
        return mutantsTotal > 0;
    }

    public static RunStats fromStatMap(Map<String, String> stats) {
        return new RunStats(
                getInt(stats, "tests.total"),
                getInt(stats, "tests.passed"),
                getInt(stats, "tests.failed"),
                getInt(stats, "lines.tracked"),
                getInt(stats, "lines.covered"),
                getLong(stats, "time.test_exec_ms"),
                getLong(stats, "time.ochiai_ms"),
                getLong(stats, "time.mutation_ms"),
                getInt(stats, "mutants.total"),
                getInt(stats, "mutants.killed"),
                getInt(stats, "mutants.survived"),
                getInt(stats, "mutants.no_coverage")
        );
    }

    public static Map<String, String> newStatMap() {
        return new HashMap<>();
    }

    private static int getInt(Map<String, String> map, String key) {
        String val = map.get(key);
        if (val == null) return 0;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long getLong(Map<String, String> map, String key) {
        String val = map.get(key);
        if (val == null) return 0L;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
