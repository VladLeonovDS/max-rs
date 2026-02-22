package com.herzen.doc.parser;

import java.util.ArrayList;
import java.util.List;

public class ParserDtos {
    public record CourseDoc(String version, String courseId, String title,
                            List<ChapterDoc> chapters,
                            List<TermDoc> terms,
                            List<DefinitionDoc> definitions,
                            List<QuestionDoc> questions,
                            List<AnswerKeyDoc> keys) {}

    public record ChapterDoc(String id, String title, Integer difficulty, String content,
                             List<String> prerequisiteChapterIds, List<String> introducedTermKeys, List<String> usedTermKeys,
                             int line) {}
    public record TermDoc(String key, int line) {}
    public record DefinitionDoc(String termKey, String text, int line) {}
    public record QuestionDoc(String id, String chapterId, String type, String prompt, int line) {}
    public record AnswerKeyDoc(String questionId, String value, int line) {}

    public record ParseError(String code, String message, int line, String block, String sectionId) {}

    public static CourseDoc emptyCourse() {
        return new CourseDoc(null, null, null,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
}
