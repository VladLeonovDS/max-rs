package com.herzen.doc;

import com.herzen.doc.assessment.AssessmentModels;
import com.herzen.doc.assessment.AssessmentService;
import com.herzen.doc.service.CourseImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AssessmentServiceTest {
    @Autowired
    private CourseImportService importService;

    @Autowired
    private AssessmentService assessmentService;

    @Test
    void runsAssessmentAndStoresKnowledgeProfile() {
        String course = """
                @meta version=\"1.0.0\" course=\"inf-8\"
                @term key=\"algorithm\"
                @definition term=\"algorithm\"
                finite steps
                @term key=\"complexity\"
                @definition term=\"complexity\"
                resources usage
                @chapter id=\"c1\" title=\"Start\" introduces=\"algorithm\"
                body @algorithm
                @question id=\"q1\" chapter=\"c1\" type=\"single\"
                p
                @key question=\"q1\"
                A
                """;

        var imported = importService.importCourse(course, true);
        assertTrue(imported.valid());

        AssessmentModels.AssessmentStartResponse start = assessmentService.startAssessment("student-1", "inf-8", "c1");
        assertFalse(start.questions().isEmpty());

        List<AssessmentModels.AssessmentAttempt> attempts = start.questions().stream()
                .map(q -> new AssessmentModels.AssessmentAttempt(start.sessionId(), q.questionId(), q.correctOption()))
                .toList();

        var submit = assessmentService.submitAnswers("student-1", "inf-8", start.sessionId(), attempts);
        assertNotNull(submit.profile());
        assertFalse(submit.profile().terms().isEmpty());

        var profile = assessmentService.profile("student-1", "inf-8");
        assertFalse(profile.terms().isEmpty());
        profile.terms().values().forEach(k -> {
            assertTrue(k.masteryScore() >= 0.0 && k.masteryScore() <= 1.0);
            assertTrue(k.confidenceScore() >= 0.0 && k.confidenceScore() <= 1.0);
        });
    }
    @Test
    void buildsLocalizedTermQuestionsWithoutLeakingAnswerInOptionsText() {
        String course = """
                @meta version="1.0.0" course="inf-8-local"
                @term key="information"
                @definition term="information"
                Информация — это сведения об объектах и процессах.
                @term key="data"
                @definition term="data"
                Данные — это форма представления информации для хранения и передачи.
                @term key="algorithm"
                @definition term="algorithm"
                Алгоритм — это точная последовательность шагов решения задачи.
                @term key="encoding"
                @definition term="encoding"
                Кодирование — это представление информации с помощью правил.
                @chapter id="c1" title="Start" introduces="information"
                body
                @question id="q1" chapter="c1" type="single"
                p
                @key question="q1"
                A
                """;

        var imported = importService.importCourse(course, true);
        assertTrue(imported.valid());

        var start = assessmentService.startAssessment("student-2", "inf-8-local", "c1");
        assertFalse(start.questions().isEmpty());

        var question = start.questions().get(0);
        assertTrue(question.prompt().startsWith("Какой термин соответствует определению:"));
        assertFalse(question.prompt().contains("information"));
        assertTrue(question.options().stream().noneMatch(option -> option.contains(" — ")));
    }

}
