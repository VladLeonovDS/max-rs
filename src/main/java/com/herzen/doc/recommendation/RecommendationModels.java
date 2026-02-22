package com.herzen.doc.recommendation;

import java.util.List;

public class RecommendationModels {
    public record RecommendationResult(String chapterId,
                                       double score,
                                       String reason,
                                       List<FactorScore> factors,
                                       boolean coldStartFallback) {}

    public record FactorScore(String name, double value) {}
}
