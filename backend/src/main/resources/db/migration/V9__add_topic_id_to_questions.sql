ALTER TABLE questions
    ADD COLUMN topic_id UUID;

ALTER TABLE questions
    ADD CONSTRAINT fk_questions_topic
        FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE;

CREATE INDEX idx_questions_topic ON questions(topic_id);
