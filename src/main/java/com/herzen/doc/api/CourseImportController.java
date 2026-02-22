package com.herzen.doc.api;

import com.herzen.doc.graph.KnowledgeGraphModels;
import com.herzen.doc.service.CourseImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/courses")
public class CourseImportController {
    private final CourseImportService importService;

    public CourseImportController(CourseImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/import")
    public ResponseEntity<CourseImportService.ImportResult> importCourse(@RequestBody ImportRequest request) {
        return ResponseEntity.ok(importService.importCourse(request.content(), request.dryRun()));
    }

    @PostMapping("/{courseId}/eligible")
    public ResponseEntity<Set<String>> eligibleChapters(@PathVariable String courseId, @RequestBody ProfileRequest request) {
        return ResponseEntity.ok(Set.copyOf(importService.eligibleChapters(courseId, request.completedChapterIds(), request.masteredTermKeys())));
    }

    @PostMapping("/{courseId}/chapters/{chapterId}/explain")
    public ResponseEntity<KnowledgeGraphModels.Eligibility> explainChapter(@PathVariable String courseId,
                                                                            @PathVariable String chapterId,
                                                                            @RequestBody ProfileRequest request) {
        return ResponseEntity.ok(importService.explainChapter(courseId, chapterId, request.completedChapterIds(), request.masteredTermKeys()));
    }

    public record ImportRequest(String content, boolean dryRun) {}

    public record ProfileRequest(Set<String> completedChapterIds, Set<String> masteredTermKeys) {}
}
