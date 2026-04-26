package com.javelin.plugin.model;

public record StatementResult(
        String fullyQualifiedClass,
        int lineNumber,
        double score,
        double rank
) implements LocalizationResult {}
