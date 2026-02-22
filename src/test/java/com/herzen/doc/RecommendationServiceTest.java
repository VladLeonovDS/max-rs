package com.herzen.doc;

import com.herzen.doc.assessment.AssessmentModels;
import com.herzen.doc.assessment.AssessmentService;
import com.herzen.doc.recommendation.RecommendationService;
import com.herzen.doc.service.CourseImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RecommendationServiceTest {
    @Autowired
    private CourseImportService importService;
    @Autowired
    private AssessmentService assessmentService;
    @Autowired
    private RecommendationService recommendationService;

    @Test
    void recommendsOnlyEligibleChapterAndIncludesReason() {
        String course = """
                @meta version=\"1.0.0\" course=\"rec-1\"
                @term key=\"t1\"
                @definition term=\"t1\"
                d1
                @term key=\"t2\"
                @definition term=\"t2\"
                d2
                @chapter id=\"c1\" title=\"Basics\" introduces=\"t1\" difficulty=\"1\"
                learn @t1
                @chapter id=\"c2\" title=\"Advanced\" requires=\"c1\" introduces=\"t2\" uses=\"t1\" difficulty=\"4\"
                use @t1 @t2
                @question id=\"q1\" chapter=\"c1\" type=\"single\"
                p
                @key question=\"q1\"
                A
                """;
        assertTrue(importService.importCourse(course, true).valid());

        var start = assessmentService.startAssessment("st-1", "rec-1");
        List<AssessmentModels.AssessmentAttempt> attempts = start.questions().stream()
                .map(q -> new AssessmentModels.AssessmentAttempt(start.sessionId(), q.questionId(), q.correctOption()))
                .toList();
        assessmentService.submitAnswers("st-1", "rec-1", start.sessionId(), attempts);

        var rec = recommendationService.next("st-1", "rec-1", Set.of());
        assertNotNull(rec.chapterId());
        assertEquals("c1", rec.chapterId());
        assertNotNull(rec.reason());
        assertFalse(rec.reason().isBlank());
    }
}
