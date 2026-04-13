package com.javelin.plugin.model;

import java.util.List;

public record RankGroup(
        int rank,
        double score,
        List<FaultLocalizationResult> lines,
        int topN
) {
}
