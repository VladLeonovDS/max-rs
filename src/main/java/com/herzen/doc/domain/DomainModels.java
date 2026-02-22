package com.herzen.doc.domain;

import java.util.List;

public class DomainModels {
    public record Course(String id, String version, String title,
                         List<Chapter> chapters,
                         List<Term> terms,
                         List<Question> questions) {}

    public record Chapter(String id, String title, Integer difficulty, String content) {}
    public record Term(String key, String definition) {}
    public record Question(String id, String chapterId, QuestionType type, String prompt, AnswerKey answerKey) {}
    public record AnswerKey(String value) {}

    public enum QuestionType { SINGLE, MULTI, TEXT }
}
