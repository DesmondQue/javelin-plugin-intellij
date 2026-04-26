package com.javelin.plugin.model;

import java.util.List;

public record RankGroup(
        double rank,
        double score,
        List<LocalizationResult> results,
        int topN
) {
}
