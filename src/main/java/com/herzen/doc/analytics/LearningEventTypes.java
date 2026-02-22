package com.herzen.doc.analytics;

import java.util.Set;

public final class LearningEventTypes {
    public static final String TERM_CLICK = "term_click";
    public static final String CHAPTER_OPEN = "chapter_open";
    public static final String CHAPTER_COMPLETE = "chapter_complete";
    public static final String ANSWER_SUBMIT = "answer_submit";
    public static final String RECOMMENDATION_ACCEPT = "recommendation_accept";

    public static final Set<String> SUPPORTED = Set.of(
            TERM_CLICK,
            CHAPTER_OPEN,
            CHAPTER_COMPLETE,
            ANSWER_SUBMIT,
            RECOMMENDATION_ACCEPT
    );

    private LearningEventTypes() {}
}
