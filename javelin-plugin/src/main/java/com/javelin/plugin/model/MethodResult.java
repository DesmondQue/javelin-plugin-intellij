package com.javelin.plugin.model;

public record MethodResult(
        String fullyQualifiedClass,
        String methodName,
        String descriptor,
        double score,
        double rank,
        int firstLine,
        int lastLine
) implements LocalizationResult {}
