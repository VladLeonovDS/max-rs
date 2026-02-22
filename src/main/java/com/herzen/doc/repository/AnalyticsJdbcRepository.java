package com.herzen.doc.repository;

import com.herzen.doc.analytics.AnalyticsModels;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class AnalyticsJdbcRepository {
    private final JdbcTemplate jdbcTemplate;

    public AnalyticsJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveEvents(List<AnalyticsModels.EventIn> events) {
        events.forEach(e -> jdbcTemplate.update(
                "INSERT INTO learning_events(student_id, course_id, chapter_id, event_type, ts, payload, recommender_version) VALUES (?,?,?,?,?,?,?)",
                e.studentId(), e.courseId(), e.chapterId(), e.eventType(),
                (e.ts() == null ? Instant.now() : e.ts()).toString(),
                e.payload(), e.recommenderVersion()
        ));
    }

    public List<EventRow> loadEvents() {
        return jdbcTemplate.query(
                "SELECT student_id, course_id, chapter_id, event_type, ts, payload, recommender_version FROM learning_events",
                (rs, n) -> new EventRow(
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), Instant.parse(rs.getString(5)), rs.getString(6), rs.getString(7)
                )
        );
    }

    public List<RecommendationRow> loadRecommendationLog() {
        return jdbcTemplate.query(
                "SELECT student_id, course_id, chapter_id, ts FROM recommendation_log",
                (rs, n) -> new RecommendationRow(rs.getString(1), rs.getString(2), rs.getString(3), Instant.parse(rs.getString(4)))
        );
    }

    public List<PrerequisiteRow> loadPrerequisites(String courseId) {
        return jdbcTemplate.query(
                "SELECT chapter_id, prerequisite_chapter_id FROM chapter_prerequisites WHERE course_id = ?",
                (rs, n) -> new PrerequisiteRow(rs.getString(1), rs.getString(2)),
                courseId
        );
    }

    public void upsertAggregate(AnalyticsModels.AnalyticsAggregate a) {
        jdbcTemplate.update(
                "MERGE INTO analytics_aggregates(scope_type, student_id, course_id, chapter_id, recommender_version, learning_gain, time_to_mastery_seconds, recommendation_acceptance, drop_off, prerequisite_violation, computed_at, counters) KEY(scope_type, student_id, course_id, chapter_id, recommender_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                a.scopeType(), a.studentId(), a.courseId(), a.chapterId(), a.recommenderVersion(),
                a.learningGain(), a.timeToMasterySeconds(), a.recommendationAcceptance(), a.dropOff(), a.prerequisiteViolation(),
                a.computedAt().toString(), toCountersString(a.counters())
        );
    }

    public List<AnalyticsModels.AnalyticsAggregate> queryAggregates(String studentId, String courseId, String chapterId) {
        return jdbcTemplate.query(
                "SELECT scope_type, student_id, course_id, chapter_id, recommender_version, learning_gain, time_to_mastery_seconds, recommendation_acceptance, drop_off, prerequisite_violation, computed_at, counters FROM analytics_aggregates " +
                        "WHERE (? IS NULL OR student_id = ?) AND (? IS NULL OR course_id = ?) AND (? IS NULL OR chapter_id = ?) ORDER BY computed_at DESC",
                (rs, n) -> new AnalyticsModels.AnalyticsAggregate(
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getDouble(6), (Double) rs.getObject(7), rs.getDouble(8), rs.getDouble(9), rs.getDouble(10),
                        Instant.parse(rs.getString(11)), parseCounters(rs.getString(12))
                ),
                studentId, studentId, courseId, courseId, chapterId, chapterId
        );
    }


    public List<AnalyticsModels.AnalyticsAggregate> loadAllAggregates() {
        return jdbcTemplate.query(
                "SELECT scope_type, student_id, course_id, chapter_id, recommender_version, learning_gain, time_to_mastery_seconds, recommendation_acceptance, drop_off, prerequisite_violation, computed_at, counters FROM analytics_aggregates",
                (rs, n) -> new AnalyticsModels.AnalyticsAggregate(
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getDouble(6), (Double) rs.getObject(7), rs.getDouble(8), rs.getDouble(9), rs.getDouble(10),
                        Instant.parse(rs.getString(11)), parseCounters(rs.getString(12))
                )
        );
    }
    private String toCountersString(Map<String, Double> counters) {
        return counters.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).reduce((a, b) -> a + ";" + b).orElse("");
    }

    private Map<String, Double> parseCounters(String value) {
        if (value == null || value.isBlank()) return Map.of();
        return java.util.Arrays.stream(value.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && s.contains("="))
                .map(s -> s.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(p -> p[0], p -> Double.parseDouble(p[1]), (a, b) -> b));
    }

    public record EventRow(String studentId, String courseId, String chapterId, String eventType, Instant ts, String payload, String recommenderVersion) {}
    public record RecommendationRow(String studentId, String courseId, String chapterId, Instant ts) {}
    public record PrerequisiteRow(String chapterId, String prerequisiteChapterId) {}
}
