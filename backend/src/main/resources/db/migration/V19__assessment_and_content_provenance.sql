-- Additive extension: existing snapshots, scores, UUIDs and V1-V18 remain untouched.
ALTER TABLE content_records DROP CONSTRAINT content_records_kind_check;
ALTER TABLE content_records ADD CONSTRAINT content_records_kind_check CHECK(kind IN ('SUBJECT','TOPIC','THEORY','QUESTION','COURSE','MODULE','LESSON','QUIZ','ASSIGNMENT','CONTEXT'));
CREATE TABLE context_versions (
 content_id UUID NOT NULL REFERENCES content_records(id),version BIGINT NOT NULL,payload JSONB NOT NULL,
 published_at TIMESTAMPTZ NOT NULL DEFAULT now(),PRIMARY KEY(content_id,version)
);
ALTER TABLE questions ADD COLUMN assessment JSONB;
ALTER TABLE questions ALTER COLUMN correct_option_id DROP NOT NULL;
ALTER TABLE test_answers ADD COLUMN answer_payload JSONB;
ALTER TABLE test_answers ADD COLUMN earned_points INTEGER;
ALTER TABLE test_answers ADD COLUMN max_points INTEGER;
ALTER TABLE test_answers ALTER COLUMN selected_option_id DROP NOT NULL;
ALTER TABLE test_sessions ALTER COLUMN subject_id DROP NOT NULL;
ALTER TABLE test_sessions ADD COLUMN context_snapshot JSONB NOT NULL DEFAULT '{}';
ALTER TABLE test_sessions ADD COLUMN snapshot_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE test_sessions ADD COLUMN earned_points INTEGER;
ALTER TABLE test_sessions ADD COLUMN max_points INTEGER;
ALTER TABLE test_sessions ADD COLUMN deadline_at TIMESTAMPTZ;
ALTER TABLE test_sessions ADD COLUMN exam_configuration JSONB;

CREATE OR REPLACE VIEW completed_question_activity AS
 SELECT s.user_id,s.id AS session_id,s.completed_at,
 coalesce((q.value->>'subjectId')::uuid,s.subject_id) AS subject_id,
 (q.value->>'id')::uuid AS question_id,(q.value->>'topicId')::uuid AS topic_id,
 q.value AS snapshot,coalesce(a.is_correct,false) AS correct,a.selected_option_id,
 coalesce(a.earned_points,CASE WHEN a.is_correct THEN 1 ELSE 0 END) AS earned_points,
 coalesce(a.max_points,(q.value->'assessment'->>'maxPoints')::integer,1) AS max_points,
 a.answer_payload,s.context_snapshot
 FROM test_sessions s CROSS JOIN LATERAL jsonb_array_elements(s.question_snapshot) q
 LEFT JOIN test_answers a ON a.session_id=s.id AND a.question_id=(q.value->>'id')::uuid
 WHERE s.status='COMPLETED';

CREATE TABLE source_registry (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), external_key VARCHAR(100) UNIQUE NOT NULL,
 metadata JSONB NOT NULL, revision BIGINT NOT NULL DEFAULT 1,
 created_by UUID NOT NULL REFERENCES users(id), updated_by UUID NOT NULL REFERENCES users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE content_external_refs (
 namespace VARCHAR(100) NOT NULL, external_key VARCHAR(160) NOT NULL,
 content_id UUID NOT NULL REFERENCES content_records(id), applied_checksum VARCHAR(64) NOT NULL,
 applied_revision BIGINT NOT NULL, source_version VARCHAR(100) NOT NULL,
 PRIMARY KEY(namespace,external_key)
);
CREATE TABLE content_pack_batches (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), namespace VARCHAR(100) NOT NULL,
 pack_version VARCHAR(100) NOT NULL,batch_key VARCHAR(100) NOT NULL,request_hash VARCHAR(64) NOT NULL,
 created_by UUID NOT NULL REFERENCES users(id),request JSONB NOT NULL,preview JSONB NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('VALID','INVALID','APPLIED')),
 result JSONB,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),applied_at TIMESTAMPTZ,
 UNIQUE(namespace,pack_version,batch_key)
);
CREATE TABLE content_update_candidates (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),content_id UUID NOT NULL REFERENCES content_records(id),
 batch_id UUID NOT NULL REFERENCES content_pack_batches(id),base_revision BIGINT NOT NULL,
 incoming_payload JSONB NOT NULL,checksum VARCHAR(64) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'CONFLICT',created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(batch_id,content_id)
);
CREATE TABLE material_external_refs (
 namespace VARCHAR(100) NOT NULL,external_key VARCHAR(160) NOT NULL,sha256 VARCHAR(64) NOT NULL,
 material_id UUID NOT NULL REFERENCES materials(id),PRIMARY KEY(namespace,external_key)
);

-- New attempts only. Historic result snapshots retain their original content.
CREATE VIEW eligible_practice_questions AS
 SELECT q.* FROM questions q JOIN topics t ON t.id=q.topic_id JOIN subjects s ON s.id=q.subject_id
 WHERE q.is_active AND t.is_active AND s.is_active AND (
  coalesce(q.assessment->>'contextKey','')='' OR EXISTS(
   SELECT 1 FROM content_records c WHERE c.id::text=split_part(q.assessment->>'contextKey',':',1)
    AND c.kind='CONTEXT' AND c.parent_id=q.topic_id AND c.published_payload IS NOT NULL AND c.status<>'ARCHIVED'
  )
 );

CREATE TABLE editorial_reviews (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),content_id UUID NOT NULL REFERENCES content_records(id),
 revision BIGINT NOT NULL,reviewed_payload JSONB NOT NULL,reviewer_id UUID NOT NULL REFERENCES users(id),
 decision VARCHAR(30) NOT NULL CHECK(decision IN ('APPROVED','CHANGES_REQUIRED')),note TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX editorial_reviews_content ON editorial_reviews(content_id,created_at DESC);
