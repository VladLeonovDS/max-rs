package com.herzen.doc.parser;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.herzen.doc.parser.ParserDtos.*;

@Component
public class HerzenDocParser {
    private static final Pattern MARKER_PATTERN = Pattern.compile("^@([a-z][a-z0-9_]*)\\s*(.*)$");
    private static final Pattern ATTR_PATTERN = Pattern.compile("([a-z][a-z0-9_]*)=\"((?:\\\\.|[^\"\\\\])*)\"");

    public ParseResult parse(String content) {
        List<ParseError> errors = new ArrayList<>();
        List<String> lines = Arrays.asList(content.split("\\R", -1));

        String version = null, courseId = null, title = null;
        List<ChapterDoc> chapters = new ArrayList<>();
        List<TermDoc> terms = new ArrayList<>();
        List<DefinitionDoc> definitions = new ArrayList<>();
        List<QuestionDoc> questions = new ArrayList<>();
        List<AnswerKeyDoc> keys = new ArrayList<>();

        String pendingMarker = null;
        Map<String, String> pendingAttrs = Map.of();
        int pendingLine = -1;
        StringBuilder body = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNo = i + 1;
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) continue;

            Matcher markerMatcher = MARKER_PATTERN.matcher(trimmed);
            if (markerMatcher.matches()) {
                flushPending(pendingMarker, pendingAttrs, pendingLine, body.toString().trim(), chapters, terms, definitions, questions, keys, errors);

                pendingMarker = markerMatcher.group(1);
                pendingAttrs = parseAttrs(markerMatcher.group(2), lineNo, pendingMarker, errors);
                pendingLine = lineNo;
                body.setLength(0);

                if ("meta".equals(pendingMarker)) {
                    version = pendingAttrs.get("version");
                    courseId = pendingAttrs.get("course");
                    title = pendingAttrs.get("title");
                    if (version == null) errors.add(new ParseError("MISSING_FIELD", "@meta.version required", lineNo, "meta", "meta"));
                    if (courseId == null) errors.add(new ParseError("MISSING_FIELD", "@meta.course required", lineNo, "meta", "meta"));
                    pendingMarker = null;
                }
            } else if (pendingMarker != null && !trimmed.isEmpty()) {
                if (body.length() > 0) body.append("\n");
                body.append(line);
            }
        }

        flushPending(pendingMarker, pendingAttrs, pendingLine, body.toString().trim(), chapters, terms, definitions, questions, keys, errors);

        if (version == null || courseId == null) {
            errors.add(new ParseError("MISSING_META", "Document must contain @meta with version and course", 1, "meta", "meta"));
        }

        CourseDoc doc = new CourseDoc(version, courseId, title, chapters, terms, definitions, questions, keys);
        return new ParseResult(doc, errors);
    }

    private Map<String, String> parseAttrs(String attrsStr, int line, String marker, List<ParseError> errors) {
        Map<String, String> attrs = new HashMap<>();
        Matcher matcher = ATTR_PATTERN.matcher(attrsStr);
        while (matcher.find()) {
            attrs.put(matcher.group(1), unescape(matcher.group(2), line, marker, errors));
        }

        String rest = attrsStr.replaceAll("([a-z][a-z0-9_]*)=\"((?:\\\\.|[^\"\\\\])*)\"", "").trim();
        if (!rest.isEmpty()) {
            errors.add(new ParseError("INVALID_ATTR_SYNTAX", "Cannot parse attributes: " + rest, line, marker, marker));
        }
        return attrs;
    }

    private String unescape(String raw, int line, String marker, List<ParseError> errors) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\') {
                if (i + 1 >= raw.length()) {
                    errors.add(new ParseError("INVALID_ESCAPE", "Dangling escape", line, marker, marker));
                    break;
                }
                char n = raw.charAt(++i);
                switch (n) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case '@' -> sb.append('@');
                    default -> {
                        errors.add(new ParseError("INVALID_ESCAPE", "Unknown escape: \\" + n, line, marker, marker));
                        sb.append(n);
                    }
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void flushPending(String marker, Map<String, String> attrs, int line, String body,
                              List<ChapterDoc> chapters, List<TermDoc> terms, List<DefinitionDoc> defs,
                              List<QuestionDoc> questions, List<AnswerKeyDoc> keys, List<ParseError> errors) {
        if (marker == null) return;

        switch (marker) {
            case "chapter" -> {
                String id = attrs.get("id");
                String title = attrs.get("title");
                Integer difficulty = null;
                if (attrs.get("difficulty") != null) {
                    try {
                        difficulty = Integer.parseInt(attrs.get("difficulty"));
                    } catch (NumberFormatException e) {
                        errors.add(new ParseError("INVALID_FIELD", "@chapter difficulty must be integer", line, "chapter", id));
                    }
                }
                if (id == null || title == null) {
                    errors.add(new ParseError("MISSING_FIELD", "@chapter id/title required", line, "chapter", id));
                    return;
                }

                List<String> prereq = csv(attrs.get("requires"));
                List<String> introduces = csv(attrs.get("introduces"));
                List<String> uses = extractUsedTerms(body);
                chapters.add(new ChapterDoc(id, title, difficulty, body, prereq, introduces, uses, line));
            }
            case "term" -> {
                String key = attrs.get("key");
                if (key == null) errors.add(new ParseError("MISSING_FIELD", "@term key required", line, "term", null));
                else terms.add(new TermDoc(key, line));
            }
            case "definition" -> {
                String term = attrs.get("term");
                if (term == null) errors.add(new ParseError("MISSING_FIELD", "@definition term required", line, "definition", null));
                else defs.add(new DefinitionDoc(term, body, line));
            }
            case "question" -> {
                String id = attrs.get("id");
                String chapter = attrs.get("chapter");
                String type = attrs.get("type");
                if (id == null || chapter == null || type == null) {
                    errors.add(new ParseError("MISSING_FIELD", "@question id/chapter/type required", line, "question", id));
                    return;
                }
                if (!Set.of("single", "multi", "text").contains(type)) {
                    errors.add(new ParseError("INVALID_QUESTION_TYPE", "Unsupported question type: " + type, line, "question", id));
                }
                questions.add(new QuestionDoc(id, chapter, type, body, line));
            }
            case "key" -> {
                String question = attrs.get("question");
                if (question == null) errors.add(new ParseError("MISSING_FIELD", "@key question required", line, "key", null));
                else keys.add(new AnswerKeyDoc(question, body, line));
            }
            default -> errors.add(new ParseError("UNKNOWN_MARKER", "Unsupported marker @" + marker, line, marker, marker));
        }
    }

    private List<String> csv(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(v -> !v.isEmpty()).distinct().toList();
    }

    private List<String> extractUsedTerms(String body) {
        if (body == null || body.isBlank()) return List.of();
        Matcher matcher = Pattern.compile("@([a-zA-Z0-9_-]+)").matcher(body);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return List.copyOf(keys);
    }

    public record ParseResult(CourseDoc doc, List<ParseError> errors) {}
}
