package com.herzen.doc.recommendation;

import com.herzen.doc.repository.RecommendationJdbcRepository;
import com.herzen.doc.repository.RecommendationJdbcRepository.ChapterTermRoleRow;
import com.herzen.doc.repository.RecommendationJdbcRepository.StudentTermRow;
import com.herzen.doc.service.CourseImportService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {
    private final CourseImportService courseImportService;
    private final RecommendationJdbcRepository repository;

    public RecommendationService(CourseImportService courseImportService, RecommendationJdbcRepository repository) {
        this.courseImportService = courseImportService;
        this.repository = repository;
    }

    public RecommendationModels.RecommendationResult next(String studentId, String courseId, Set<String> completedChapterIds) {
        Map<String, Double> studentMastery = repository.loadStudentKnowledge(studentId, courseId).stream()
                .collect(Collectors.toMap(StudentTermRow::termKey, StudentTermRow::masteryScore, (a, b) -> b));
        Set<String> mastered = studentMastery.entrySet().stream().filter(e -> e.getValue() >= 0.6).map(Map.Entry::getKey).collect(Collectors.toSet());

        List<String> eligible = courseImportService.eligibleChapters(courseId, completedChapterIds, mastered);
        if (eligible.isEmpty()) {
            return new RecommendationModels.RecommendationResult(null, 0.0, "Нет логически доступных глав", List.of(), true);
        }

        boolean coldStart = repository.studentCount(courseId) < 3;
        RecommendationModels.RecommendationResult best = null;

        for (String chapterId : eligible) {
            List<ChapterTermRoleRow> terms = repository.loadChapterTerms(courseId, chapterId);
            List<String> introduces = terms.stream().filter(t -> "introduces".equalsIgnoreCase(t.role())).map(ChapterTermRoleRow::termKey).distinct().toList();

            double newCoverage = introduces.isEmpty() ? 0.0 :
                    introduces.stream().filter(t -> studentMastery.getOrDefault(t, 0.0) < 0.6).count() / (double) introduces.size();

            int difficulty = Optional.ofNullable(repository.loadChapterDifficulty(courseId, chapterId)).orElse(3);
            double avgMastery = studentMastery.values().stream().mapToDouble(v -> v).average().orElse(0.4);
            double targetDifficulty = 1 + 4 * avgMastery;
            double difficultyFit = 1.0 - Math.min(1.0, Math.abs(difficulty - targetDifficulty) / 4.0);

            double historical = coldStart ? 0.0 : historicalSuccessSimilarStudents(studentId, courseId, introduces, studentMastery);

            double score;
            List<RecommendationModels.FactorScore> factors;
            if (coldStart) {
                score = 0.65 * newCoverage + 0.35 * difficultyFit;
                factors = List.of(
                        new RecommendationModels.FactorScore("new_term_coverage", newCoverage),
                        new RecommendationModels.FactorScore("difficulty_fit", difficultyFit)
                );
            } else {
                score = 0.4 * newCoverage + 0.2 * difficultyFit + 0.4 * historical;
                factors = List.of(
                        new RecommendationModels.FactorScore("new_term_coverage", newCoverage),
                        new RecommendationModels.FactorScore("difficulty_fit", difficultyFit),
                        new RecommendationModels.FactorScore("historical_success_similar", historical)
                );
            }

            String reason = buildReason(coldStart, newCoverage, difficultyFit, historical);
            RecommendationModels.RecommendationResult candidate = new RecommendationModels.RecommendationResult(chapterId, score, reason, factors, coldStart);
            if (best == null || candidate.score() > best.score()) {
                best = candidate;
            }
        }
        if (best != null) {
            repository.saveRecommendationLog(studentId, courseId, best.chapterId(), best.score(), serializeFactors(best.factors()), best.reason());
        }
        return best;
    }


    private String serializeFactors(List<RecommendationModels.FactorScore> factors) {
        return factors.stream()
                .map(f -> f.name() + "=" + String.format(java.util.Locale.US, "%.4f", f.value()))
                .collect(Collectors.joining(";"));
    }
    private double historicalSuccessSimilarStudents(String studentId, String courseId, List<String> chapterTerms, Map<String, Double> targetProfile) {
        if (chapterTerms.isEmpty()) return 0.0;

        Map<String, Map<String, Double>> byStudent = repository.loadCourseKnowledge(courseId).stream()
                .collect(Collectors.groupingBy(StudentTermRow::studentId,
                        Collectors.toMap(StudentTermRow::termKey, StudentTermRow::masteryScore, (a, b) -> b)));

        List<Map<String, Double>> similarProfiles = byStudent.entrySet().stream()
                .filter(e -> !e.getKey().equals(studentId))
                .filter(e -> similarity(targetProfile, e.getValue()) >= 0.3)
                .map(Map.Entry::getValue)
                .toList();

        if (similarProfiles.isEmpty()) return 0.0;

        double total = 0.0;
        int count = 0;
        for (Map<String, Double> profile : similarProfiles) {
            for (String term : chapterTerms) {
                if (profile.containsKey(term)) {
                    total += profile.get(term);
                    count++;
                }
            }
        }
        return count == 0 ? 0.0 : total / count;
    }

    private double similarity(Map<String, Double> a, Map<String, Double> b) {
        Set<String> keys = new HashSet<>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        if (keys.isEmpty()) return 0.0;

        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (String k : keys) {
            double va = a.getOrDefault(k, 0.0);
            double vb = b.getOrDefault(k, 0.0);
            dot += va * vb;
            na += va * va;
            nb += vb * vb;
        }
        if (na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private String buildReason(boolean coldStart, double coverage, double difficultyFit, double historical) {
        if (coldStart) {
            return String.format("Cold-start: выбрана глава с максимальным покрытием новых терминов (%.2f) и хорошим уровнем сложности (%.2f)", coverage, difficultyFit);
        }
        return String.format("Глава выбрана по сумме факторов: новые термины=%.2f, сложность=%.2f, успех похожих студентов=%.2f", coverage, difficultyFit, historical);
    }
}
