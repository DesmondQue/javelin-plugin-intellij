package com.javelin.plugin.model;

public record FaultLocalizationResult(String fullyQualifiedClass, int lineNumber, double score, int rank) {
}
