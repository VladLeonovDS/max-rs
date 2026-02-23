package com.herzen.doc;

import com.herzen.doc.analytics.AnalyticsModels;
import com.herzen.doc.analytics.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AnalyticsServiceTest {
    @Autowired
    private AnalyticsService analyticsService;

    @Test
    void ingestsEventsAndBuildsOverview() {
        var request = new AnalyticsModels.LearningEventIngestRequest(List.of(
                new AnalyticsModels.EventIn("st-a", "course-a", "ch-1", "chapter_open", Instant.now(), "", "v1"),
                new AnalyticsModels.EventIn("st-a", "course-a", "ch-1", "term_click", Instant.now().plusSeconds(2), "term=t", "v1"),
                new AnalyticsModels.EventIn("st-a", "course-a", "ch-1", "chapter_complete", Instant.now().plusSeconds(6), "", "v1"),
                new AnalyticsModels.EventIn("st-a", "course-a", "ch-1", "answer_submit", Instant.now().plusSeconds(8), "attempts=3", "v1")
        ));

        var ack = analyticsService.ingest(request);
        assertEquals(4, ack.accepted());

        analyticsService.recomputeAggregates();
        var overview = analyticsService.overview("st-a", "course-a", "ch-1");
        assertFalse(overview.aggregates().isEmpty());
        var aggregate = overview.aggregates().get(0);
        assertEquals("st-a", aggregate.studentId());
        assertEquals("course-a", aggregate.courseId());
        assertEquals("ch-1", aggregate.chapterId());
        assertTrue(aggregate.learningGain() >= 0.0);
    }

    @Test
    void buildsComparisonAndBottlenecksViews() {
        var request = new AnalyticsModels.LearningEventIngestRequest(List.of(
                new AnalyticsModels.EventIn("st-b", "course-a", "ch-2", "chapter_open", Instant.now(), "", "baseline"),
                new AnalyticsModels.EventIn("st-b", "course-a", "ch-2", "term_click", Instant.now().plusSeconds(1), "term=loop", "baseline"),
                new AnalyticsModels.EventIn("st-b", "course-a", "ch-2", "term_click", Instant.now().plusSeconds(2), "term=loop", "baseline"),
                new AnalyticsModels.EventIn("st-b", "course-a", "ch-2", "term_click", Instant.now().plusSeconds(3), "term=loop", "baseline")
        ));
        analyticsService.ingest(request);
        analyticsService.recomputeAggregates();

        var compare = analyticsService.compareByVersion("course-a");
        assertFalse(compare.rows().isEmpty());

        var bottlenecks = analyticsService.bottlenecks("course-a");
        assertFalse(bottlenecks.bottlenecks().isEmpty());
    }

    @Test
    void recomputeSkipsEventsWithoutChapterId() {
        var request = new AnalyticsModels.LearningEventIngestRequest(List.of(
                new AnalyticsModels.EventIn("st-null", "course-null", null, "chapter_open", Instant.now(), "", "v1"),
                new AnalyticsModels.EventIn("st-null", "course-null", "ch-valid", "chapter_open", Instant.now().plusSeconds(1), "", "v1")
        ));

        analyticsService.ingest(request);
        assertDoesNotThrow(() -> analyticsService.recomputeAggregates());

        var overview = analyticsService.overview("st-null", "course-null", null);
        assertTrue(overview.aggregates().stream().noneMatch(a -> a.chapterId() == null));
        assertTrue(overview.aggregates().stream().anyMatch(a -> "ch-valid".equals(a.chapterId())));
    }

}
