package com.herzen.doc.assessment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AssessmentModels {
    public record AssessmentSession(String sessionId,
                                    String courseId,
                                    Instant startedAt,
                                    Set<String> askedTerms,
                                    boolean refinementIssued) {}

    public record AssessmentQuestion(String questionId, String termKey, String prompt, List<String> options, String correctOption) {}

    public record AssessmentAttempt(String sessionId, String questionId, String selectedOption) {}

    public record TermKnowledge(String studentId, String courseId, String termKey, double masteryScore, double confidenceScore) {}

    public record StudentKnowledgeProfile(String studentId, String courseId, Map<String, TermKnowledge> terms) {}

    public record AssessmentStartResponse(String sessionId, List<AssessmentQuestion> questions) {}

    public record AssessmentSubmitResponse(boolean needsRefinement,
                                           List<AssessmentQuestion> refinementQuestions,
                                           StudentKnowledgeProfile profile,
                                           List<LearningEvent> events) {}

    public record LearningEvent(String studentId, String courseId, String eventType, Instant ts, String payload) {}
}
