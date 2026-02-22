package com.herzen.doc.repository;

import com.herzen.doc.domain.DomainModels;
import com.herzen.doc.graph.KnowledgeGraphModels.EdgeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class GraphJdbcRepository {
    private final JdbcTemplate jdbcTemplate;

    public GraphJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replaceCourseGraph(String courseId,
                                   List<DomainModels.Chapter> chapters,
                                   List<ChapterPrerequisiteRow> prerequisites,
                                   List<ChapterTermRow> chapterTerms) {
        jdbcTemplate.update("DELETE FROM chapter_prerequisites WHERE course_id = ?", courseId);
        jdbcTemplate.update("DELETE FROM chapter_terms WHERE course_id = ?", courseId);
        jdbcTemplate.update("DELETE FROM chapter_metadata WHERE course_id = ?", courseId);

        prerequisites.forEach(r -> jdbcTemplate.update(
                "INSERT INTO chapter_prerequisites(course_id, chapter_id, prerequisite_chapter_id) VALUES (?,?,?)",
                r.courseId(), r.chapterId(), r.prerequisiteChapterId()));

        chapterTerms.forEach(r -> jdbcTemplate.update(
                "INSERT INTO chapter_terms(course_id, chapter_id, term_key, role) VALUES (?,?,?,?)",
                r.courseId(), r.chapterId(), r.termKey(), r.role().name().toLowerCase()));

        chapters.forEach(c -> jdbcTemplate.update(
                "INSERT INTO chapter_metadata(course_id, chapter_id, title, difficulty) VALUES (?,?,?,?)",
                courseId, c.id(), c.title(), c.difficulty() == null ? 3 : c.difficulty()));
    }

    public List<ChapterPrerequisiteRow> loadPrerequisites(String courseId) {
        return jdbcTemplate.query(
                "SELECT course_id, chapter_id, prerequisite_chapter_id FROM chapter_prerequisites WHERE course_id = ?",
                (rs, rowNum) -> new ChapterPrerequisiteRow(rs.getString(1), rs.getString(2), rs.getString(3)),
                courseId);
    }

    public List<ChapterTermRow> loadChapterTerms(String courseId) {
        return jdbcTemplate.query(
                "SELECT course_id, chapter_id, term_key, role FROM chapter_terms WHERE course_id = ?",
                (rs, rowNum) -> new ChapterTermRow(rs.getString(1), rs.getString(2), rs.getString(3), EdgeType.valueOf(rs.getString(4).toUpperCase())),
                courseId);
    }

    public record ChapterPrerequisiteRow(String courseId, String chapterId, String prerequisiteChapterId) {}
    public record ChapterTermRow(String courseId, String chapterId, String termKey, EdgeType role) {}
}
