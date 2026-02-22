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
    event_type VARCHAR(128) NOT NULL,
    ts VARCHAR(64) NOT NULL,
    payload CLOB
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
