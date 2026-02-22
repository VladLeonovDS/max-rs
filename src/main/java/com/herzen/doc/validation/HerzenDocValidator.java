package com.herzen.doc.validation;

import com.herzen.doc.parser.ParserDtos.CourseDoc;
import com.herzen.doc.parser.ParserDtos.ParseError;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class HerzenDocValidator {
    public List<ParseError> validate(CourseDoc doc) {
        List<ParseError> errors = new ArrayList<>();

        duplicate(doc.terms().stream().map(t -> new Row(t.key(), t.line(), "term")).toList(), "DUPLICATE_TERM", errors);
        duplicate(doc.chapters().stream().map(c -> new Row(c.id(), c.line(), "chapter")).toList(), "DUPLICATE_CHAPTER", errors);
        duplicate(doc.questions().stream().map(q -> new Row(q.id(), q.line(), "question")).toList(), "DUPLICATE_QUESTION", errors);

        Set<String> termKeys = doc.terms().stream().map(t -> t.key()).collect(Collectors.toSet());
        doc.definitions().forEach(d -> {
            if (!termKeys.contains(d.termKey())) {
                errors.add(new ParseError("TERM_NOT_FOUND", "Definition references unknown term: " + d.termKey(), d.line(), "definition", d.termKey()));
            }
        });

        Set<String> termsWithDef = doc.definitions().stream().map(d -> d.termKey()).collect(Collectors.toSet());
        doc.terms().forEach(t -> {
            if (!termsWithDef.contains(t.key())) {
                errors.add(new ParseError("MISSING_DEFINITION", "Term has no definition: " + t.key(), t.line(), "term", t.key()));
            }
        });

        Set<String> chapterIds = doc.chapters().stream().map(c -> c.id()).collect(Collectors.toSet());
        doc.questions().forEach(q -> {
            if (!chapterIds.contains(q.chapterId())) {
                errors.add(new ParseError("CHAPTER_NOT_FOUND", "Question references unknown chapter: " + q.chapterId(), q.line(), "question", q.id()));
            }
        });

        Set<String> qIds = doc.questions().stream().map(q -> q.id()).collect(Collectors.toSet());
        doc.keys().forEach(k -> {
            if (!qIds.contains(k.questionId())) {
                errors.add(new ParseError("QUESTION_NOT_FOUND", "Key references unknown question: " + k.questionId(), k.line(), "key", k.questionId()));
            }
        });

        return errors;
    }

    private void duplicate(List<Row> rows, String code, List<ParseError> errors) {
        Map<String, Long> counts = rows.stream().collect(Collectors.groupingBy(Row::id, Collectors.counting()));
        rows.forEach(r -> {
            if (r.id() != null && counts.getOrDefault(r.id(), 0L) > 1) {
                errors.add(new ParseError(code, "Duplicate " + r.block() + " id/key: " + r.id(), r.line(), r.block(), r.id()));
            }
        });
    }

    private record Row(String id, int line, String block) {}
}
