package com.herzen.doc.graph;

import com.herzen.doc.domain.DomainModels;
import com.herzen.doc.graph.KnowledgeGraphModels.*;
import com.herzen.doc.repository.GraphJdbcRepository;
import com.herzen.doc.repository.GraphJdbcRepository.ChapterPrerequisiteRow;
import com.herzen.doc.repository.GraphJdbcRepository.ChapterTermRow;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class KnowledgeGraphService {
    private final GraphJdbcRepository repository;
    private final Map<String, GraphModel> graphCache = new ConcurrentHashMap<>();

    public KnowledgeGraphService(GraphJdbcRepository repository) {
        this.repository = repository;
    }

    public GraphLoadResult loadAndPersist(String courseId,
                                          List<DomainModels.Chapter> chapters,
                                          Set<String> allTerms,
                                          Map<String, List<String>> chapterPrerequisites,
                                          Map<String, List<String>> chapterIntroduces,
                                          Map<String, List<String>> chapterUses) {
        Set<String> chapterNodes = chapters.stream().map(DomainModels.Chapter::id).collect(Collectors.toSet());
        List<GraphEdge> edges = new ArrayList<>();

        for (var e : chapterPrerequisites.entrySet()) {
            for (String required : e.getValue()) {
                edges.add(new GraphEdge(e.getKey(), required, EdgeType.REQUIRES));
            }
        }
        for (var e : chapterIntroduces.entrySet()) {
            for (String term : e.getValue()) {
                edges.add(new GraphEdge(e.getKey(), term, EdgeType.INTRODUCES));
            }
        }
        for (var e : chapterUses.entrySet()) {
            for (String term : e.getValue()) {
                edges.add(new GraphEdge(e.getKey(), term, EdgeType.USES));
            }
        }

        GraphModel model = new GraphModel(courseId, chapterNodes, allTerms, edges);
        List<GraphValidationIssue> issues = validateGraph(model);

        if (issues.isEmpty()) {
            List<ChapterPrerequisiteRow> prereqRows = edges.stream()
                    .filter(e -> e.type() == EdgeType.REQUIRES)
                    .map(e -> new ChapterPrerequisiteRow(courseId, e.from(), e.to()))
                    .toList();

            List<ChapterTermRow> termRows = edges.stream()
                    .filter(e -> e.type() == EdgeType.INTRODUCES || e.type() == EdgeType.USES)
                    .map(e -> new ChapterTermRow(courseId, e.from(), e.to(), e.type()))
                    .toList();

            repository.replaceCourseGraph(courseId, chapters, prereqRows, termRows);
            graphCache.put(courseId, model);
        }
        return new GraphLoadResult(model, issues);
    }

    public List<String> eligibleChapters(String courseId, StudentProfile profile) {
        GraphModel model = readModel(courseId);
        if (model == null) return List.of();

        return model.chapterNodes().stream()
                .filter(ch -> !profile.completedChapterIds().contains(ch))
                .filter(ch -> explainChapterEligibility(model, ch, profile).eligible())
                .sorted()
                .toList();
    }

    public Eligibility explainChapter(String courseId, String chapterId, StudentProfile profile) {
        GraphModel model = readModel(courseId);
        if (model == null) {
            return new Eligibility(chapterId, false, List.of("COURSE_GRAPH_NOT_FOUND"), List.of());
        }
        return explainChapterEligibility(model, chapterId, profile);
    }

    private Eligibility explainChapterEligibility(GraphModel model, String chapterId, StudentProfile profile) {
        List<String> missingChapters = model.edges().stream()
                .filter(e -> e.type() == EdgeType.REQUIRES && e.from().equals(chapterId))
                .map(GraphEdge::to)
                .filter(req -> !profile.completedChapterIds().contains(req))
                .distinct()
                .sorted()
                .toList();

        List<String> missingTerms = model.edges().stream()
                .filter(e -> e.type() == EdgeType.USES && e.from().equals(chapterId))
                .map(GraphEdge::to)
                .filter(term -> !profile.masteredTermKeys().contains(term))
                .distinct()
                .sorted()
                .toList();

        return new Eligibility(chapterId, missingChapters.isEmpty() && missingTerms.isEmpty(), missingChapters, missingTerms);
    }

    private GraphModel readModel(String courseId) {
        GraphModel cached = graphCache.get(courseId);
        if (cached != null) return cached;

        List<ChapterPrerequisiteRow> prereq = repository.loadPrerequisites(courseId);
        List<ChapterTermRow> termRows = repository.loadChapterTerms(courseId);
        if (prereq.isEmpty() && termRows.isEmpty()) return null;

        Set<String> chapters = new HashSet<>();
        Set<String> terms = new HashSet<>();
        List<GraphEdge> edges = new ArrayList<>();

        for (ChapterPrerequisiteRow r : prereq) {
            chapters.add(r.chapterId());
            chapters.add(r.prerequisiteChapterId());
            edges.add(new GraphEdge(r.chapterId(), r.prerequisiteChapterId(), EdgeType.REQUIRES));
        }
        for (ChapterTermRow r : termRows) {
            chapters.add(r.chapterId());
            terms.add(r.termKey());
            edges.add(new GraphEdge(r.chapterId(), r.termKey(), r.role()));
        }

        GraphModel model = new GraphModel(courseId, chapters, terms, edges);
        graphCache.put(courseId, model);
        return model;
    }

    private List<GraphValidationIssue> validateGraph(GraphModel model) {
        List<GraphValidationIssue> issues = new ArrayList<>();

        for (GraphEdge edge : model.edges()) {
            if (edge.type() == EdgeType.REQUIRES) {
                if (!model.chapterNodes().contains(edge.from()) || !model.chapterNodes().contains(edge.to())) {
                    issues.add(new GraphValidationIssue("CHAPTER_REF_NOT_FOUND", "Requires edge references missing chapter", edge.from() + "->" + edge.to()));
                }
            } else {
                if (!model.chapterNodes().contains(edge.from()) || !model.termNodes().contains(edge.to())) {
                    issues.add(new GraphValidationIssue("TERM_REF_NOT_FOUND", "Chapter-term edge references missing node", edge.from() + "->" + edge.to()));
                }
            }
        }

        Map<String, List<String>> adj = new HashMap<>();
        model.chapterNodes().forEach(c -> adj.put(c, new ArrayList<>()));
        model.edges().stream().filter(e -> e.type() == EdgeType.REQUIRES).forEach(e -> adj.getOrDefault(e.from(), new ArrayList<>()).add(e.to()));

        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String node : model.chapterNodes()) {
            if (hasCycle(node, adj, visiting, visited)) {
                issues.add(new GraphValidationIssue("CYCLE_DETECTED", "Cycle detected in prerequisite graph", node));
                break;
            }
        }

        for (String chapter : model.chapterNodes()) {
            boolean connected = model.edges().stream().anyMatch(e -> e.from().equals(chapter) || e.to().equals(chapter));
            if (!connected) {
                issues.add(new GraphValidationIssue("ORPHAN_CHAPTER", "Chapter has no graph links", chapter));
            }
        }
        for (String term : model.termNodes()) {
            boolean connected = model.edges().stream().anyMatch(e -> e.to().equals(term));
            if (!connected) {
                issues.add(new GraphValidationIssue("ORPHAN_TERM", "Term is not introduced/used by any chapter", term));
            }
        }

        return issues;
    }

    private boolean hasCycle(String node, Map<String, List<String>> adj, Set<String> visiting, Set<String> visited) {
        if (visited.contains(node)) return false;
        if (visiting.contains(node)) return true;

        visiting.add(node);
        for (String next : adj.getOrDefault(node, List.of())) {
            if (hasCycle(next, adj, visiting, visited)) return true;
        }
        visiting.remove(node);
        visited.add(node);
        return false;
    }

    public record GraphLoadResult(GraphModel graph, List<GraphValidationIssue> issues) {}
}
