CREATE TABLE IF NOT EXISTS chapter_prerequisites (
    course_id VARCHAR(128) NOT NULL,
    chapter_id VARCHAR(128) NOT NULL,
    prerequisite_chapter_id VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS chapter_terms (
    course_id VARCHAR(128) NOT NULL,
    chapter_id VARCHAR(128) NOT NULL,
    term_key VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS chapter_metadata (
    course_id VARCHAR(128) NOT NULL,
    chapter_id VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    difficulty INT NOT NULL,
    PRIMARY KEY (course_id, chapter_id)
);

CREATE TABLE IF NOT EXISTS student_knowledge (
    student_id VARCHAR(128) NOT NULL,
    course_id VARCHAR(128) NOT NULL,
    term_key VARCHAR(128) NOT NULL,
    mastery_score DOUBLE NOT NULL,
    confidence_score DOUBLE NOT NULL,
    PRIMARY KEY (student_id, course_id, term_key)
);

CREATE TABLE IF NOT EXISTS learning_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id VARCHAR(128) NOT NULL,
    course_id VARCHAR(128) NOT NULL,
    chapter_id VARCHAR(128),
    event_type VARCHAR(128) NOT NULL,
    ts VARCHAR(64) NOT NULL,
    payload CLOB,
    recommender_version VARCHAR(128)
);

CREATE TABLE IF NOT EXISTS recommendation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id VARCHAR(128) NOT NULL,
    course_id VARCHAR(128) NOT NULL,
    chapter_id VARCHAR(128) NOT NULL,
    score DOUBLE NOT NULL,
    reason CLOB NOT NULL,
    factors CLOB,
    ts VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS analytics_aggregates (
    scope_type VARCHAR(64) NOT NULL,
    student_id VARCHAR(128),
    course_id VARCHAR(128),
    chapter_id VARCHAR(128),
    recommender_version VARCHAR(128),
    learning_gain DOUBLE NOT NULL,
    time_to_mastery_seconds DOUBLE,
    recommendation_acceptance DOUBLE NOT NULL,
    drop_off DOUBLE NOT NULL,
    prerequisite_violation DOUBLE NOT NULL,
    computed_at VARCHAR(64) NOT NULL,
    counters CLOB,
    PRIMARY KEY (scope_type, student_id, course_id, chapter_id, recommender_version)
);
