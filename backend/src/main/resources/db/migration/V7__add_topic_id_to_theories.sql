ALTER TABLE theories
    ADD COLUMN topic_id UUID;

ALTER TABLE theories
    ADD CONSTRAINT fk_theories_topic
        FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE;

CREATE INDEX idx_theories_topic ON theories(topic_id);
