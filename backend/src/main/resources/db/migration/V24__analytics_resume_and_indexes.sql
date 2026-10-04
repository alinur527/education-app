-- Separate completion evidence from resume activity; keep legacy timestamps intact.
ALTER TABLE user_theory_progress ADD COLUMN last_read_at TIMESTAMP;
UPDATE user_theory_progress SET last_read_at=read_at WHERE is_read;
CREATE INDEX sessions_analytics_utc ON test_sessions(user_id,(completed_at AT TIME ZONE 'UTC'),id)
WHERE status='COMPLETED';
