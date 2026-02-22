package com.herzen.doc.analytics;

import com.herzen.doc.repository.AnalyticsJdbcRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {
    private final AnalyticsJdbcRepository repository;

    public AnalyticsService(AnalyticsJdbcRepository repository) {
        this.repository = repository;
    }

    public AnalyticsModels.LearningEventAck ingest(AnalyticsModels.LearningEventIngestRequest request) {
        if (request == null || request.events() == null) return new AnalyticsModels.LearningEventAck(0, 0);
        List<AnalyticsModels.EventIn> accepted = request.events().stream()
                .filter(Objects::nonNull)
                .filter(e -> LearningEventTypes.SUPPORTED.contains(e.eventType()))
                .toList();
        repository.saveEvents(accepted);
        return new AnalyticsModels.LearningEventAck(accepted.size(), request.events().size() - accepted.size());
    }

    @Scheduled(fixedDelayString = "${analytics.recompute.fixed-delay-ms:300000}")
    public void scheduledRecompute() {
        recomputeAggregates();
    }

    public void recomputeAggregates() {
        List<AnalyticsJdbcRepository.EventRow> events = repository.loadEvents();
        List<AnalyticsJdbcRepository.RecommendationRow> recs = repository.loadRecommendationLog();

        Map<Key, List<AnalyticsJdbcRepository.EventRow>> grouped = events.stream()
                .filter(e -> e.studentId() != null && e.courseId() != null)
                .collect(Collectors.groupingBy(e -> new Key(e.studentId(), e.courseId(), e.chapterId(), normalizeVersion(e.recommenderVersion()))));

        for (var entry : grouped.entrySet()) {
            Key key = entry.getKey();
            List<AnalyticsJdbcRepository.EventRow> rows = entry.getValue().stream().sorted(Comparator.comparing(AnalyticsJdbcRepository.EventRow::ts)).toList();
            repository.upsertAggregate(buildAggregate("student_chapter", key, rows, recs));
        }
    }

    public AnalyticsModels.AnalyticsOverviewResponse overview(String studentId, String courseId, String chapterId) {
        return new AnalyticsModels.AnalyticsOverviewResponse(repository.queryAggregates(studentId, courseId, chapterId));
    }

    public AnalyticsModels.RecommenderComparisonResponse compareByVersion(String courseId) {
        List<AnalyticsModels.AnalyticsAggregate> rows = repository.loadAllAggregates().stream()
                .filter(a -> courseId == null || courseId.isBlank() || courseId.equals(a.courseId()))
                .toList();
        Map<String, List<AnalyticsModels.AnalyticsAggregate>> byVersion = rows.stream()
                .collect(Collectors.groupingBy(a -> normalizeVersion(a.recommenderVersion())));

        List<AnalyticsModels.RecommenderComparisonRow> compared = byVersion.entrySet().stream()
                .map(e -> {
                    List<AnalyticsModels.AnalyticsAggregate> r = e.getValue();
                    return new AnalyticsModels.RecommenderComparisonRow(
                            e.getKey(),
                            avg(r, AnalyticsModels.AnalyticsAggregate::learningGain),
                            avgNullable(r, AnalyticsModels.AnalyticsAggregate::timeToMasterySeconds),
                            avg(r, AnalyticsModels.AnalyticsAggregate::recommendationAcceptance),
                            avg(r, AnalyticsModels.AnalyticsAggregate::dropOff),
                            avg(r, AnalyticsModels.AnalyticsAggregate::prerequisiteViolation),
                            r.size()
                    );
                })
                .sorted(Comparator.comparing(AnalyticsModels.RecommenderComparisonRow::recommenderVersion))
                .toList();

        return new AnalyticsModels.RecommenderComparisonResponse(compared);
    }

    public AnalyticsModels.BottleneckResponse bottlenecks(String courseId) {
        List<AnalyticsJdbcRepository.EventRow> events = repository.loadEvents().stream()
                .filter(e -> courseId == null || courseId.isBlank() || courseId.equals(e.courseId()))
                .toList();

        Map<String, Long> termClicks = events.stream()
                .filter(e -> LearningEventTypes.TERM_CLICK.equals(e.eventType()))
                .map(e -> extractPayloadValue(e.payload(), "term"))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        Map<String, Long> chapterOpens = events.stream()
                .filter(e -> LearningEventTypes.CHAPTER_OPEN.equals(e.eventType()))
                .collect(Collectors.groupingBy(e -> Optional.ofNullable(e.chapterId()).orElse("unknown"), Collectors.counting()));

        Map<String, Long> chapterCompletes = events.stream()
                .filter(e -> LearningEventTypes.CHAPTER_COMPLETE.equals(e.eventType()))
                .collect(Collectors.groupingBy(e -> Optional.ofNullable(e.chapterId()).orElse("unknown"), Collectors.counting()));

        List<AnalyticsModels.BottleneckRow> out = new ArrayList<>();
        termClicks.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .forEach(e -> out.add(new AnalyticsModels.BottleneckRow("weak_term_link", e.getKey(), e.getValue(),
                        "Проверить определение/связи термина и примеры в главе")));

        chapterOpens.forEach((chapter, opens) -> {
            long completes = chapterCompletes.getOrDefault(chapter, 0L);
            if (opens >= 3 && completes * 1.0 / opens < 0.5) {
                out.add(new AnalyticsModels.BottleneckRow("dropoff_chapter", chapter, opens - completes,
                        "Пересмотреть сложность вопросов и explainability для главы"));
            }
        });

        return new AnalyticsModels.BottleneckResponse(out.stream()
                .sorted(Comparator.comparing(AnalyticsModels.BottleneckRow::occurrences).reversed())
                .toList());
    }

    private AnalyticsModels.AnalyticsAggregate buildAggregate(String scope, Key key,
                                                              List<AnalyticsJdbcRepository.EventRow> rows,
                                                              List<AnalyticsJdbcRepository.RecommendationRow> recs) {
        long opens = count(rows, LearningEventTypes.CHAPTER_OPEN);
        long completes = count(rows, LearningEventTypes.CHAPTER_COMPLETE);
        long answerSubmits = count(rows, LearningEventTypes.ANSWER_SUBMIT);
        long termClicks = count(rows, LearningEventTypes.TERM_CLICK);
        long acceptances = count(rows, LearningEventTypes.RECOMMENDATION_ACCEPT);

        double learningGain = opens == 0 ? 0.0 : ((double) completes / opens);
        double dropOff = opens == 0 ? 0.0 : ((double) Math.max(opens - completes, 0) / opens);

        Instant firstOpen = firstTs(rows, LearningEventTypes.CHAPTER_OPEN);
        Instant firstComplete = firstTs(rows, LearningEventTypes.CHAPTER_COMPLETE);
        Double timeToMastery = (firstOpen != null && firstComplete != null && !firstComplete.isBefore(firstOpen))
                ? (double) (firstComplete.getEpochSecond() - firstOpen.getEpochSecond())
                : null;

        long recShown = recs.stream().filter(r -> r.studentId().equals(key.studentId()) && r.courseId().equals(key.courseId())
                && Objects.equals(r.chapterId(), key.chapterId())).count();
        double recommendationAcceptance = recShown == 0 ? 0.0 : (double) acceptances / recShown;

        double prereqViolation = prerequisiteViolationRate(key, rows);

        Map<String, Double> counters = Map.of(
                "chapter_open", (double) opens,
                "chapter_complete", (double) completes,
                "answer_submit", (double) answerSubmits,
                "term_click", (double) termClicks,
                "recommendation_accept", (double) acceptances,
                "recommendation_shown", (double) recShown
        );

        return new AnalyticsModels.AnalyticsAggregate(scope, key.studentId(), key.courseId(), key.chapterId(), key.version(),
                learningGain, timeToMastery, recommendationAcceptance, dropOff, prereqViolation, Instant.now(), counters);
    }

    private double prerequisiteViolationRate(Key key, List<AnalyticsJdbcRepository.EventRow> rows) {
        if (key.chapterId() == null) return 0.0;
        List<AnalyticsJdbcRepository.PrerequisiteRow> prereq = repository.loadPrerequisites(key.courseId());
        Set<String> req = prereq.stream()
                .filter(p -> p.chapterId().equals(key.chapterId()))
                .map(AnalyticsJdbcRepository.PrerequisiteRow::prerequisiteChapterId)
                .collect(Collectors.toSet());
        if (req.isEmpty()) return 0.0;

        Set<String> completedBefore = rows.stream()
                .filter(r -> LearningEventTypes.CHAPTER_COMPLETE.equals(r.eventType()))
                .map(AnalyticsJdbcRepository.EventRow::chapterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        long violations = req.stream().filter(r -> !completedBefore.contains(r)).count();
        return (double) violations / req.size();
    }

    private String normalizeVersion(String version) {
        return (version == null || version.isBlank()) ? "default" : version;
    }

    private long count(List<AnalyticsJdbcRepository.EventRow> rows, String eventType) {
        return rows.stream().filter(r -> eventType.equals(r.eventType())).count();
    }

    private Instant firstTs(List<AnalyticsJdbcRepository.EventRow> rows, String eventType) {
        return rows.stream().filter(r -> eventType.equals(r.eventType())).map(AnalyticsJdbcRepository.EventRow::ts).min(Comparator.naturalOrder()).orElse(null);
    }

    private double avg(List<AnalyticsModels.AnalyticsAggregate> rows, java.util.function.ToDoubleFunction<AnalyticsModels.AnalyticsAggregate> fn) {
        return rows.stream().mapToDouble(fn).average().orElse(0.0);
    }

    private double avgNullable(List<AnalyticsModels.AnalyticsAggregate> rows, java.util.function.Function<AnalyticsModels.AnalyticsAggregate, Double> fn) {
        return rows.stream().map(fn).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private String extractPayloadValue(String payload, String key) {
        if (payload == null) return null;
        for (String part : payload.split(",")) {
            String p = part.trim();
            if (p.startsWith(key + "=")) return p.substring((key + "=").length());
        }
        return null;
    }

    private record Key(String studentId, String courseId, String chapterId, String version) {}
}
