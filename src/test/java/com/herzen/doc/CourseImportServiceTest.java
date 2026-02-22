package com.herzen.doc;

import com.herzen.doc.service.CourseImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CourseImportServiceTest {
    @Autowired
    private CourseImportService service;

    @Test
    void importsValidDocInDryRunAndBuildsGraphEligibility() {
        String doc = """
                @meta version=\"1.0.0\" course=\"informatics-8\"
                @term key=\"algorithm\"
                @definition term=\"algorithm\"
                Definition
                @term key=\"complexity\"
                @definition term=\"complexity\"
                Definition
                @chapter id=\"ch1\" title=\"Intro\" introduces=\"algorithm\"
                Learn @algorithm
                @chapter id=\"ch2\" title=\"Next\" requires=\"ch1\" uses=\"complexity\"
                Uses @complexity
                @question id=\"q1\" chapter=\"ch1\" type=\"single\"
                Prompt
                @key question=\"q1\"
                A
                """;
        var result = service.importCourse(doc, true);
        assertTrue(result.valid());
        assertEquals("informatics-8", result.course().id());

        var eligible = service.eligibleChapters("informatics-8", Set.of(), Set.of("algorithm"));
        assertTrue(eligible.contains("ch1"));
        assertFalse(eligible.contains("ch2"));

        var explain = service.explainChapter("informatics-8", "ch2", Set.of(), Set.of("algorithm"));
        assertFalse(explain.eligible());
        assertTrue(explain.missingChapters().contains("ch1"));
        assertTrue(explain.missingTerms().contains("complexity"));
    }

    @Test
    void reportsStructuredErrors() {
        String doc = """
                @term key=\"algorithm\"
                @question id=\"q1\" chapter=\"missing\" type=\"single\"
                Prompt
                """;
        var result = service.importCourse(doc, true);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals("MISSING_META")));
        assertTrue(result.errors().stream().anyMatch(e -> e.line() > 0));
    }
}
