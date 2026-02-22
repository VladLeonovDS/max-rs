package com.herzen.doc.api;

import com.herzen.doc.assessment.AssessmentModels;
import com.herzen.doc.assessment.AssessmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assessment")
public class AssessmentController {
    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @PostMapping("/start")
    public ResponseEntity<AssessmentModels.AssessmentStartResponse> start(@RequestBody StartRequest request) {
        return ResponseEntity.ok(assessmentService.startAssessment(request.studentId(), request.courseId(), request.chapterId()));
    }

    @PostMapping("/submit")
    public ResponseEntity<AssessmentModels.AssessmentSubmitResponse> submit(@RequestBody SubmitRequest request) {
        return ResponseEntity.ok(assessmentService.submitAnswers(
                request.studentId(), request.courseId(), request.sessionId(), request.attempts()));
    }

    @GetMapping("/profile")
    public ResponseEntity<AssessmentModels.StudentKnowledgeProfile> profile(@RequestParam String studentId,
                                                                            @RequestParam String courseId) {
        return ResponseEntity.ok(assessmentService.profile(studentId, courseId));
    }

    public record StartRequest(String studentId, String courseId, String chapterId) {}

    public record SubmitRequest(String studentId,
                                String courseId,
                                String sessionId,
                                List<AssessmentModels.AssessmentAttempt> attempts) {}
}
