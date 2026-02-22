package com.herzen.doc.service;

import com.herzen.doc.assessment.AssessmentService;
import com.herzen.doc.domain.DomainModels;
import com.herzen.doc.graph.KnowledgeGraphModels;
import com.herzen.doc.graph.KnowledgeGraphService;
import com.herzen.doc.parser.HerzenDocParser;
import com.herzen.doc.parser.ParserDtos;
import com.herzen.doc.validation.HerzenDocValidator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CourseImportService {
    private final HerzenDocParser parser;
    private final HerzenDocValidator validator;
    private final KnowledgeGraphService graphService;
    private final AssessmentService assessmentService;

    public CourseImportService(HerzenDocParser parser,
                               HerzenDocValidator validator,
                               KnowledgeGraphService graphService,
                               AssessmentService assessmentService) {
        this.parser = parser;
        this.validator = validator;
        this.graphService = graphService;
        this.assessmentService = assessmentService;
    }

    public ImportResult importCourse(String content, boolean dryRun) {
        HerzenDocParser.ParseResult parseResult = parser.parse(content);
        List<ParserDtos.ParseError> errors = new ArrayList<>(parseResult.errors());
        errors.addAll(validator.validate(parseResult.doc()));

        DomainModels.Course course = null;
        List<KnowledgeGraphModels.GraphValidationIssue> graphIssues = List.of();

        if (errors.isEmpty()) {
            course = toDomain(parseResult.doc());

            var graphResult = graphService.loadAndPersist(
                    course.id(),
                    course.chapters(),
                    course.terms().stream().map(DomainModels.Term::key).collect(Collectors.toSet()),
                    parseResult.doc().chapters().stream().collect(Collectors.toMap(ParserDtos.ChapterDoc::id, ParserDtos.ChapterDoc::prerequisiteChapterIds)),
                    parseResult.doc().chapters().stream().collect(Collectors.toMap(ParserDtos.ChapterDoc::id, ParserDtos.ChapterDoc::introducedTermKeys)),
                    parseResult.doc().chapters().stream().collect(Collectors.toMap(ParserDtos.ChapterDoc::id, ParserDtos.ChapterDoc::usedTermKeys))
            );
            graphIssues = graphResult.issues();

            if (graphIssues.isEmpty()) {
                assessmentService.registerCourseQuestions(course.id(), parseResult.doc());
            }
        }

        return new ImportResult(dryRun, errors.isEmpty() && graphIssues.isEmpty(), course, errors, graphIssues);
    }

    public List<String> eligibleChapters(String courseId, Set<String> completedChapterIds, Set<String> masteredTermKeys) {
        return graphService.eligibleChapters(courseId,
                new KnowledgeGraphModels.StudentProfile(
                        completedChapterIds == null ? Set.of() : completedChapterIds,
                        masteredTermKeys == null ? Set.of() : masteredTermKeys));
    }

    public KnowledgeGraphModels.Eligibility explainChapter(String courseId, String chapterId,
                                                           Set<String> completedChapterIds,
                                                           Set<String> masteredTermKeys) {
        return graphService.explainChapter(courseId, chapterId,
                new KnowledgeGraphModels.StudentProfile(
                        completedChapterIds == null ? Set.of() : completedChapterIds,
                        masteredTermKeys == null ? Set.of() : masteredTermKeys));
    }

    private DomainModels.Course toDomain(ParserDtos.CourseDoc doc) {
        Map<String, String> defs = doc.definitions().stream().collect(Collectors.toMap(ParserDtos.DefinitionDoc::termKey, ParserDtos.DefinitionDoc::text, (a, b) -> a));
        Map<String, ParserDtos.AnswerKeyDoc> keys = doc.keys().stream().collect(Collectors.toMap(ParserDtos.AnswerKeyDoc::questionId, Function.identity(), (a, b) -> a));

        List<DomainModels.Chapter> chapters = doc.chapters().stream()
                .map(c -> new DomainModels.Chapter(c.id(), c.title(), c.difficulty(), c.content()))
                .toList();

        List<DomainModels.Term> terms = doc.terms().stream()
                .map(t -> new DomainModels.Term(t.key(), defs.get(t.key())))
                .toList();

        List<DomainModels.Question> questions = doc.questions().stream()
                .map(q -> new DomainModels.Question(
                        q.id(),
                        q.chapterId(),
                        DomainModels.QuestionType.valueOf(q.type().toUpperCase()),
                        q.prompt(),
                        new DomainModels.AnswerKey(keys.get(q.id()) == null ? null : keys.get(q.id()).value())
                ))
                .toList();

        return new DomainModels.Course(doc.courseId(), doc.version(), doc.title(), chapters, terms, questions);
    }

    public record ImportResult(boolean dryRun,
                               boolean valid,
                               DomainModels.Course course,
                               List<ParserDtos.ParseError> errors,
                               List<KnowledgeGraphModels.GraphValidationIssue> graphIssues) {
    }
}
