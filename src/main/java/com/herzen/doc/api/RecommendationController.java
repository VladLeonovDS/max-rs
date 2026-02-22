package com.herzen.doc.api;

import com.herzen.doc.recommendation.RecommendationModels;
import com.herzen.doc.recommendation.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/next")
    public ResponseEntity<RecommendationModels.RecommendationResult> next(@RequestParam String studentId,
                                                                          @RequestParam String courseId,
                                                                          @RequestParam(required = false) String completedChapterIds,
                                                                          @RequestParam(required = false, defaultValue = "hybrid") String recommenderVersion) {
        Set<String> completed = parseCsv(completedChapterIds);
        return ResponseEntity.ok(recommendationService.next(studentId, courseId, completed, recommenderVersion));
    }

    private Set<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }
}
