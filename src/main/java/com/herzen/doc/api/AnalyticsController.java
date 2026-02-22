package com.herzen.doc.api;

import com.herzen.doc.analytics.AnalyticsModels;
import com.herzen.doc.analytics.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping("/events")
    public ResponseEntity<AnalyticsModels.LearningEventAck> ingest(@RequestBody AnalyticsModels.LearningEventIngestRequest request) {
        return ResponseEntity.ok(analyticsService.ingest(request));
    }

    @PostMapping("/recompute")
    public ResponseEntity<Void> recompute() {
        analyticsService.recomputeAggregates();
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/overview")
    public ResponseEntity<AnalyticsModels.AnalyticsOverviewResponse> overview(@RequestParam(required = false) String studentId,
                                                                              @RequestParam(required = false) String courseId,
                                                                              @RequestParam(required = false) String chapterId) {
        return ResponseEntity.ok(analyticsService.overview(studentId, courseId, chapterId));
    }

    @GetMapping("/compare")
    public ResponseEntity<AnalyticsModels.RecommenderComparisonResponse> compare(@RequestParam(required = false) String courseId) {
        return ResponseEntity.ok(analyticsService.compareByVersion(courseId));
    }

    @GetMapping("/bottlenecks")
    public ResponseEntity<AnalyticsModels.BottleneckResponse> bottlenecks(@RequestParam(required = false) String courseId) {
        return ResponseEntity.ok(analyticsService.bottlenecks(courseId));
    }
}
