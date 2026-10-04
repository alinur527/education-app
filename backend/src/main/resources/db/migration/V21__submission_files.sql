-- Preserve only the latest legacy submission and grade that actually exist.
-- No invented earlier revisions; content revision is independent of optimistic revision.
ALTER TABLE materials ADD COLUMN scan_status VARCHAR(30) NOT NULL DEFAULT 'UNSCANNED_LEGACY';
ALTER TABLE materials ADD COLUMN sha256 VARCHAR(64);
ALTER TABLE materials ADD COLUMN scanned_at TIMESTAMPTZ;
CREATE TABLE submission_revisions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), assignment_id UUID NOT NULL REFERENCES assignments(id),
 user_id UUID NOT NULL REFERENCES users(id), content_revision BIGINT NOT NULL CHECK(content_revision>0),
 text TEXT NOT NULL DEFAULT '', submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 request_key UUID, request_hash VARCHAR(64), legacy_imported BOOLEAN NOT NULL DEFAULT false,
 UNIQUE(assignment_id,user_id,content_revision), UNIQUE(user_id,request_key)
);
CREATE INDEX submission_revisions_history ON submission_revisions(assignment_id,user_id,content_revision DESC);
INSERT INTO submission_revisions(assignment_id,user_id,content_revision,text,submitted_at,legacy_imported)
 SELECT assignment_id,user_id,1,text,submitted_at,true FROM assignment_submissions;
ALTER TABLE assignment_submissions ADD COLUMN content_revision BIGINT NOT NULL DEFAULT 1;
ALTER TABLE assignment_submissions ADD COLUMN latest_submission_id UUID REFERENCES submission_revisions(id);
UPDATE assignment_submissions s SET latest_submission_id=r.id FROM submission_revisions r WHERE r.assignment_id=s.assignment_id AND r.user_id=s.user_id;
CREATE TABLE submission_grade_history (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), submission_id UUID NOT NULL REFERENCES submission_revisions(id),
 score INTEGER NOT NULL, max_score INTEGER NOT NULL CHECK(max_score>0), feedback TEXT,
 graded_by UUID REFERENCES users(id), graded_at TIMESTAMPTZ NOT NULL DEFAULT now(), legacy_imported BOOLEAN NOT NULL DEFAULT false,
 CHECK(score>=0 AND score<=max_score)
);
CREATE INDEX submission_grade_revision ON submission_grade_history(submission_id,graded_at DESC,id);
INSERT INTO submission_grade_history(submission_id,score,max_score,feedback,graded_by,graded_at,legacy_imported)
 SELECT s.latest_submission_id,s.score,a.max_score,s.feedback,s.graded_by,coalesce(s.graded_at,s.submitted_at),true
 FROM assignment_submissions s JOIN assignments a ON a.id=s.assignment_id WHERE s.score IS NOT NULL;
CREATE TABLE submission_files (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id), assignment_id UUID NOT NULL REFERENCES assignments(id),
 request_key UUID NOT NULL, storage_key VARCHAR(200) NOT NULL UNIQUE, original_file_name VARCHAR(255) NOT NULL,
 mime_type VARCHAR(150) NOT NULL, size BIGINT NOT NULL CHECK(size>0 AND size<=20971520), sha256 VARCHAR(64) NOT NULL,
 scan_status VARCHAR(30) NOT NULL CHECK(scan_status IN ('PENDING','CLEAN','INFECTED','SCAN_FAILED','UNSCANNED')),
 scan_engine VARCHAR(100) NOT NULL DEFAULT '', scan_message VARCHAR(300) NOT NULL DEFAULT '', scan_attempts INTEGER NOT NULL DEFAULT 0,
 scanned_at TIMESTAMPTZ, next_scan_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), deleted_at TIMESTAMPTZ,
 UNIQUE(user_id,request_key)
);
CREATE INDEX submission_files_owner ON submission_files(user_id,assignment_id,created_at DESC);
CREATE INDEX submission_files_retry ON submission_files(next_scan_at) WHERE scan_status IN ('PENDING','SCAN_FAILED','UNSCANNED');
CREATE TABLE submission_revision_files (
 submission_id UUID NOT NULL REFERENCES submission_revisions(id), file_id UUID NOT NULL REFERENCES submission_files(id),
 PRIMARY KEY(submission_id,file_id)
);
