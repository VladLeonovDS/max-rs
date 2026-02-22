package com.herzen.doc.assessment;

import com.herzen.doc.assessment.AssessmentModels.*;
import com.herzen.doc.parser.ParserDtos;
import com.herzen.doc.repository.AssessmentJdbcRepository;
import org.springframework.stereotype.Service;

import com.herzen.doc.analytics.LearningEventTypes;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AssessmentService {
    private final AssessmentJdbcRepository repository;

    private final Map<String, AssessmentSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, List<AssessmentQuestion>> questionBankByCourse = new ConcurrentHashMap<>();

    public AssessmentService(AssessmentJdbcRepository repository) {
        this.repository = repository;
    }

    public void registerCourseQuestions(String courseId, ParserDtos.CourseDoc doc) {
        List<AssessmentQuestion> questions = doc.terms().stream()
                .map(term -> buildQuestion(term.key(), doc))
                .toList();
        questionBankByCourse.put(courseId, questions);
    }

    public AssessmentStartResponse startAssessment(String studentId, String courseId) {
        List<AssessmentQuestion> bank = questionBankByCourse.getOrDefault(courseId, List.of());
        List<AssessmentQuestion> initial = bank.stream().limit(5).toList();
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new AssessmentSession(sessionId, courseId, Instant.now(),
                initial.stream().map(AssessmentQuestion::termKey).collect(Collectors.toSet()), false));
        return new AssessmentStartResponse(sessionId, initial);
    }

    public AssessmentSubmitResponse submitAnswers(String studentId, String courseId, String sessionId, List<AssessmentAttempt> attempts) {
        AssessmentSession session = sessions.get(sessionId);
        if (session == null) {
            return new AssessmentSubmitResponse(false, List.of(), profile(studentId, courseId), List.of());
        }

        List<AssessmentQuestion> bank = questionBankByCourse.getOrDefault(courseId, List.of());
        Map<String, AssessmentQuestion> byId = bank.stream().collect(Collectors.toMap(AssessmentQuestion::questionId, q -> q, (a, b) -> a));

        Map<String, List<Boolean>> correctness = new HashMap<>();
        for (AssessmentAttempt attempt : attempts) {
            AssessmentQuestion q = byId.get(attempt.questionId());
            if (q == null) continue;
            boolean ok = Objects.equals(q.correctOption(), attempt.selectedOption());
            correctness.computeIfAbsent(q.termKey(), k -> new ArrayList<>()).add(ok);
        }

        List<TermKnowledge> knowledge = correctness.entrySet().stream().map(e -> {
            double mastery = e.getValue().stream().mapToInt(v -> v ? 1 : 0).average().orElse(0.0);
            double confidence = Math.min(1.0, e.getValue().size() / 3.0);
            return new TermKnowledge(studentId, courseId, e.getKey(), mastery, confidence);
        }).toList();
        repository.saveKnowledge(knowledge);

        List<LearningEvent> events = List.of(
                new LearningEvent(studentId, courseId, null, LearningEventTypes.ANSWER_SUBMIT, Instant.now(),
                        "session=" + sessionId + ",answers=" + attempts.size() + ",terms=" + knowledge.size(), "default")
        );
        repository.saveEvents(events);

        List<AssessmentQuestion> refinement = List.of();
        boolean needsRefinement = false;
        if (!session.refinementIssued()) {
            Set<String> lowConfidenceTerms = knowledge.stream()
                    .filter(k -> k.confidenceScore() < 0.67)
                    .map(TermKnowledge::termKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            refinement = bank.stream()
                    .filter(q -> lowConfidenceTerms.contains(q.termKey()) && !session.askedTerms().contains(q.termKey()))
                    .limit(3)
                    .toList();
            needsRefinement = !refinement.isEmpty();

            if (needsRefinement) {
                Set<String> asked = new HashSet<>(session.askedTerms());
                asked.addAll(refinement.stream().map(AssessmentQuestion::termKey).toList());
                sessions.put(sessionId, new AssessmentSession(sessionId, courseId, session.startedAt(), asked, true));
                repository.saveEvents(List.of(new LearningEvent(studentId, courseId, null, LearningEventTypes.ANSWER_SUBMIT, Instant.now(), "refinement_questions=" + refinement.size(), "default")));
            }
        }

        return new AssessmentSubmitResponse(needsRefinement, refinement, profile(studentId, courseId), events);
    }

    public StudentKnowledgeProfile profile(String studentId, String courseId) {
        Map<String, TermKnowledge> map = repository.loadKnowledge(studentId, courseId).stream()
                .collect(Collectors.toMap(TermKnowledge::termKey, t -> t, (a, b) -> b));
        return new StudentKnowledgeProfile(studentId, courseId, map);
    }

    private AssessmentQuestion buildQuestion(String termKey, ParserDtos.CourseDoc doc) {
        String definition = doc.definitions().stream()
                .filter(d -> d.termKey().equals(termKey))
                .map(ParserDtos.DefinitionDoc::text)
                .findFirst()
                .orElse("Определение отсутствует");

        String normalizedDefinition = normalizeDefinitionForPrompt(definition);
        String prompt = "Какой термин соответствует определению: «" + normalizedDefinition + "»?";

        String correctOption = resolveDisplayName(termKey, definition);
        List<String> options = doc.definitions().stream()
                .filter(d -> !d.termKey().equals(termKey))
                .map(d -> resolveDisplayName(d.termKey(), d.text()))
                .distinct()
                .limit(3)
                .collect(Collectors.toCollection(ArrayList::new));
        options.add(correctOption);

        while (options.size() < 4) {
            options.add("Термин отсутствует");
        }

        Collections.shuffle(options);
        return new AssessmentQuestion("assess-" + termKey, termKey, prompt, options, correctOption);
    }

    private String normalizeDefinitionForPrompt(String definition) {
        if (definition == null || definition.isBlank()) {
            return "Определение отсутствует";
        }

        String normalized = definition.trim().replaceAll("\\s+", " ");
        int separatorIndex = normalized.indexOf("—");
        if (separatorIndex <= 0) {
            separatorIndex = normalized.indexOf("-");
        }

        if (separatorIndex > 0 && separatorIndex + 1 < normalized.length()) {
            return normalized.substring(separatorIndex + 1).trim();
        }
        return normalized;
    }

    private String resolveDisplayName(String termKey, String definition) {
        if (definition != null && !definition.isBlank()) {
            String normalized = definition.trim().replaceAll("\\s+", " ");
            int separatorIndex = normalized.indexOf("—");
            if (separatorIndex <= 0) {
                separatorIndex = normalized.indexOf("-");
            }
            if (separatorIndex > 0) {
                String candidate = normalized.substring(0, separatorIndex).trim();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        return termKey.replace('_', ' ');
    }
}
