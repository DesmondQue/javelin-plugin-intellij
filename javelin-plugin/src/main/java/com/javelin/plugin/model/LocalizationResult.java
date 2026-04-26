package com.javelin.plugin.model;

public sealed interface LocalizationResult permits StatementResult, MethodResult {
    String fullyQualifiedClass();
    double score();
    double rank();
}
