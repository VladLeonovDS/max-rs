package com.herzen.doc.repository;

import com.herzen.doc.assessment.AssessmentModels.LearningEvent;
import com.herzen.doc.assessment.AssessmentModels.TermKnowledge;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AssessmentJdbcRepository {
    private final JdbcTemplate jdbcTemplate;

    public AssessmentJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveKnowledge(List<TermKnowledge> knowledge) {
        knowledge.forEach(k -> jdbcTemplate.update(
                "MERGE INTO student_knowledge(student_id, course_id, term_key, mastery_score, confidence_score) KEY(student_id, course_id, term_key) VALUES (?,?,?,?,?)",
                k.studentId(), k.courseId(), k.termKey(), k.masteryScore(), k.confidenceScore()));
    }

    public List<TermKnowledge> loadKnowledge(String studentId, String courseId) {
        return jdbcTemplate.query(
                "SELECT student_id, course_id, term_key, mastery_score, confidence_score FROM student_knowledge WHERE student_id=? AND course_id=?",
                (rs, rowNum) -> new TermKnowledge(rs.getString(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getDouble(5)),
                studentId, courseId);
    }

    public void saveEvents(List<LearningEvent> events) {
        events.forEach(e -> jdbcTemplate.update(
                "INSERT INTO learning_events(student_id, course_id, chapter_id, event_type, ts, payload, recommender_version) VALUES (?,?,?,?,?,?,?)",
                e.studentId(), e.courseId(), e.chapterId(), e.eventType(), e.ts().toString(), e.payload(), e.recommenderVersion()));
    }
}
