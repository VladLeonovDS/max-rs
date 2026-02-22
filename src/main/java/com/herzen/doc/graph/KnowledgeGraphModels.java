package com.herzen.doc.graph;

import java.util.List;
import java.util.Set;

public class KnowledgeGraphModels {
    public record GraphModel(String courseId,
                             Set<String> chapterNodes,
                             Set<String> termNodes,
                             List<GraphEdge> edges) {}

    public record GraphEdge(String from, String to, EdgeType type) {}

    public enum EdgeType { REQUIRES, INTRODUCES, USES }

    public record StudentProfile(Set<String> completedChapterIds, Set<String> masteredTermKeys) {}

    public record Eligibility(String chapterId, boolean eligible, List<String> missingChapters, List<String> missingTerms) {}

    public record GraphValidationIssue(String code, String message, String node) {}
}
