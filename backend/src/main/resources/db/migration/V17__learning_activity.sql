ALTER TABLE test_sessions ADD COLUMN practice_mode VARCHAR(30) NOT NULL DEFAULT 'TOPIC_PRACTICE'
 CHECK(practice_mode IN ('TOPIC_PRACTICE','ERROR_REVIEW','MIXED_PRACTICE','MOCK_ENT'));
CREATE INDEX sessions_user_topic_completed ON test_sessions(user_id,topic_id,completed_at DESC) WHERE status='COMPLETED';
CREATE VIEW completed_question_activity AS
 SELECT s.user_id,s.id AS session_id,s.completed_at,s.subject_id,
 (q.value->>'id')::uuid AS question_id,(q.value->>'topicId')::uuid AS topic_id,
 q.value AS snapshot,coalesce(a.is_correct,false) AS correct,a.selected_option_id
 FROM test_sessions s CROSS JOIN LATERAL jsonb_array_elements(s.question_snapshot) q
 LEFT JOIN test_answers a ON a.session_id=s.id AND a.question_id=(q.value->>'id')::uuid
 WHERE s.status='COMPLETED';
