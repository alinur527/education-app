-- Remove the retired legacy seed before any HTTP traffic can reach this application.
DELETE FROM users WHERE lower(email) = lower('test@ent.local');
CREATE UNIQUE INDEX uq_users_email_lower ON users(lower(email));
ALTER TABLE test_sessions ADD COLUMN topic_id UUID REFERENCES topics(id);
ALTER TABLE test_sessions ADD COLUMN question_snapshot JSONB;
CREATE INDEX idx_sessions_user_completed ON test_sessions(user_id, completed_at DESC) WHERE status = 'COMPLETED';
CREATE INDEX idx_questions_topic_active ON questions(topic_id) WHERE is_active;
CREATE INDEX idx_theories_topic_active ON theories(topic_id) WHERE is_active;
ALTER TABLE test_answers ADD CONSTRAINT ck_answer_time CHECK (time_spent_secs IS NULL OR time_spent_secs >= 0);
