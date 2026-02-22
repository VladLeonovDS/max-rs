package com.herzen.doc.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class RecommendationJdbcRepository {
    private final JdbcTemplate jdbcTemplate;

    public RecommendationJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ChapterTermRoleRow> loadChapterTerms(String courseId, String chapterId) {
        return jdbcTemplate.query(
                "SELECT chapter_id, term_key, role FROM chapter_terms WHERE course_id=? AND chapter_id=?",
                (rs, n) -> new ChapterTermRoleRow(rs.getString(1), rs.getString(2), rs.getString(3)),
                courseId, chapterId);
    }

    public Integer loadChapterDifficulty(String courseId, String chapterId) {
        List<Integer> rows = jdbcTemplate.query(
                "SELECT difficulty FROM chapter_metadata WHERE course_id=? AND chapter_id=?",
                (rs, n) -> rs.getInt(1),
                courseId, chapterId);
        return rows.isEmpty() ? 3 : rows.get(0);
    }

    public List<StudentTermRow> loadStudentKnowledge(String studentId, String courseId) {
        return jdbcTemplate.query(
                "SELECT student_id, term_key, mastery_score FROM student_knowledge WHERE student_id=? AND course_id=?",
                (rs, n) -> new StudentTermRow(rs.getString(1), rs.getString(2), rs.getDouble(3)),
                studentId, courseId);
    }

    public List<StudentTermRow> loadCourseKnowledge(String courseId) {
        return jdbcTemplate.query(
                "SELECT student_id, term_key, mastery_score FROM student_knowledge WHERE course_id=?",
                (rs, n) -> new StudentTermRow(rs.getString(1), rs.getString(2), rs.getDouble(3)),
                courseId);
    }

    public long studentCount(String courseId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT student_id) FROM student_knowledge WHERE course_id=?",
                Long.class,
                courseId);
        return value == null ? 0 : value;
    }

    public void saveRecommendationLog(String studentId, String courseId, String chapterId, double score, String factors, String reason) {
        jdbcTemplate.update(
                "INSERT INTO recommendation_log(student_id, course_id, chapter_id, score, reason, factors, ts) VALUES (?,?,?,?,?,?,?)",
                studentId, courseId, chapterId, score, reason, factors, Instant.now().toString());
    }

    public record ChapterTermRoleRow(String chapterId, String termKey, String role) {}
    public record StudentTermRow(String studentId, String termKey, double masteryScore) {}
}
