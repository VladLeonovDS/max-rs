package com.herzen.doc.analytics;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class AnalyticsModels {
    public record LearningEventIngestRequest(List<EventIn> events) {}

    public record EventIn(String studentId,
                          String courseId,
                          String chapterId,
                          String eventType,
                          Instant ts,
                          String payload,
                          String recommenderVersion) {}

    public record LearningEventAck(int accepted, int rejected) {}

    public record AnalyticsAggregate(String scopeType,
                                     String studentId,
                                     String courseId,
                                     String chapterId,
                                     String recommenderVersion,
                                     double learningGain,
                                     Double timeToMasterySeconds,
                                     double recommendationAcceptance,
                                     double dropOff,
                                     double prerequisiteViolation,
                                     Instant computedAt,
                                     Map<String, Double> counters) {}

    public record AnalyticsOverviewResponse(List<AnalyticsAggregate> aggregates) {}

    public record RecommenderComparisonRow(String recommenderVersion,
                                           double avgLearningGain,
                                           double avgTimeToMasterySeconds,
                                           double avgRecommendationAcceptance,
                                           double avgDropOff,
                                           double avgPrerequisiteViolation,
                                           long sampleSize) {}

    public record RecommenderComparisonResponse(List<RecommenderComparisonRow> rows) {}

    public record BottleneckRow(String category, String key, long occurrences, String recommendation) {}

    public record BottleneckResponse(List<BottleneckRow> bottlenecks) {}
}
