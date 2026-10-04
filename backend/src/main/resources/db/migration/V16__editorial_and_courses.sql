ALTER TABLE users ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD CONSTRAINT users_role_valid CHECK (role IN ('STUDENT','TEACHER','CONTENT_EDITOR','ADMIN'));

CREATE TABLE content_records (
 id UUID PRIMARY KEY, kind VARCHAR(20) NOT NULL CHECK(kind IN ('SUBJECT','TOPIC','THEORY','QUESTION','COURSE','MODULE','LESSON','QUIZ','ASSIGNMENT')),
 parent_id UUID REFERENCES content_records(id), owner_id UUID REFERENCES users(id),
 title_ru VARCHAR(300) NOT NULL, title_kz VARCHAR(300) NOT NULL DEFAULT '',
 payload JSONB NOT NULL, published_payload JSONB,
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','REVIEW','PUBLISHED','ARCHIVED')),
 version BIGINT NOT NULL DEFAULT 1, published_version BIGINT,
 created_by UUID REFERENCES users(id), updated_by UUID REFERENCES users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX content_parent ON content_records(parent_id);
CREATE INDEX content_owner_kind ON content_records(owner_id,kind,updated_at DESC);
CREATE INDEX content_status ON content_records(status,kind);
CREATE TABLE audit_events (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), actor_id UUID REFERENCES users(id),
 entity_id UUID NOT NULL, entity_type VARCHAR(30) NOT NULL, operation VARCHAR(40) NOT NULL,
 revision BIGINT, details JSONB NOT NULL DEFAULT '{}', created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_entity ON audit_events(entity_id,created_at DESC);

-- Legacy seed authors remain unknown; no privileged user is invented.
INSERT INTO content_records(id,kind,title_ru,title_kz,payload,status)
 SELECT id,'SUBJECT',name_ru,name_kz,jsonb_build_object('titleRu',name_ru,'titleKz',name_kz,'icon',icon,'color',color,'durationMinutes',duration_minutes),
 CASE WHEN is_active THEN 'PUBLISHED' ELSE 'ARCHIVED' END FROM subjects;
INSERT INTO content_records(id,kind,parent_id,title_ru,title_kz,payload,status)
 SELECT id,'TOPIC',subject_id,title_ru,coalesce(title_kz,''),jsonb_build_object('titleRu',title_ru,'titleKz',coalesce(title_kz,''),'descriptionRu',description_ru,'descriptionKz',description_kz,'sortOrder',sort_order),
 CASE WHEN is_active THEN 'PUBLISHED' ELSE 'ARCHIVED' END FROM topics;
INSERT INTO content_records(id,kind,parent_id,title_ru,title_kz,payload,status)
 SELECT id,'THEORY',topic_id,title_ru,coalesce(title_kz,''),jsonb_build_object('titleRu',title_ru,'titleKz',coalesce(title_kz,''),'contentRu',content_ru,'contentKz',content_kz,'sortOrder',sort_order,'blocks','[]'::jsonb),
 CASE WHEN is_active THEN 'PUBLISHED' ELSE 'ARCHIVED' END FROM theories;
INSERT INTO content_records(id,kind,parent_id,title_ru,title_kz,payload,status)
 SELECT id,'QUESTION',topic_id,left(question_ru,300),left(coalesce(question_kz,''),300),jsonb_build_object('titleRu',question_ru,'titleKz',coalesce(question_kz,''),'options',options,'correctOptionId',correct_option_id,'explanationRu',explanation_ru,'explanationKz',explanation_kz,'difficulty',difficulty,'year',year,'sourceType','IMPORTED','verified',false),
 CASE WHEN is_active THEN 'PUBLISHED' ELSE 'ARCHIVED' END FROM questions;
UPDATE content_records SET published_payload=payload,published_version=1 WHERE status='PUBLISHED';

CREATE TABLE courses (
 id UUID PRIMARY KEY REFERENCES content_records(id), teacher_id UUID NOT NULL REFERENCES users(id),
 title_ru VARCHAR(300) NOT NULL, title_kz VARCHAR(300) NOT NULL DEFAULT '',
 description_ru TEXT NOT NULL DEFAULT '', description_kz TEXT NOT NULL DEFAULT '',
 visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE' CHECK(visibility IN ('PUBLIC','PRIVATE')),
 self_enroll BOOLEAN NOT NULL DEFAULT false, icon VARCHAR(50) NOT NULL DEFAULT 'book'
);
CREATE TABLE course_modules (
 id UUID PRIMARY KEY REFERENCES content_records(id), course_id UUID NOT NULL REFERENCES courses(id),
 title_ru VARCHAR(300) NOT NULL, title_kz VARCHAR(300) NOT NULL DEFAULT '', sort_order INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX modules_course ON course_modules(course_id,sort_order);
CREATE TABLE lessons (
 id UUID PRIMARY KEY REFERENCES content_records(id), module_id UUID NOT NULL REFERENCES course_modules(id),
 title_ru VARCHAR(300) NOT NULL, title_kz VARCHAR(300) NOT NULL DEFAULT '', sort_order INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX lessons_module ON lessons(module_id,sort_order);
CREATE TABLE enrollments (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), course_id UUID NOT NULL REFERENCES courses(id), user_id UUID NOT NULL REFERENCES users(id),
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','COMPLETED','CANCELLED')),
 manual_access BOOLEAN NOT NULL DEFAULT false, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(course_id,user_id)
);
CREATE INDEX enrollments_user ON enrollments(user_id,status);
CREATE TABLE lesson_progress (
 user_id UUID NOT NULL REFERENCES users(id), lesson_id UUID NOT NULL REFERENCES lessons(id),
 completed_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(user_id,lesson_id)
);
CREATE TABLE learning_groups (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), name VARCHAR(200) NOT NULL,
 teacher_id UUID NOT NULL REFERENCES users(id), course_id UUID NOT NULL REFERENCES courses(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX groups_teacher ON learning_groups(teacher_id);
CREATE TABLE group_members (
 group_id UUID NOT NULL REFERENCES learning_groups(id), user_id UUID NOT NULL REFERENCES users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(group_id,user_id)
);
CREATE INDEX group_members_user ON group_members(user_id);
CREATE TABLE assignments (
 id UUID PRIMARY KEY REFERENCES content_records(id), course_id UUID NOT NULL REFERENCES courses(id),
 lesson_id UUID REFERENCES lessons(id), teacher_id UUID NOT NULL REFERENCES users(id), due_at TIMESTAMPTZ,
 max_score INTEGER NOT NULL DEFAULT 100 CHECK(max_score BETWEEN 1 AND 10000)
);
CREATE TABLE assignment_groups (
 assignment_id UUID NOT NULL REFERENCES assignments(id), group_id UUID NOT NULL REFERENCES learning_groups(id),
 PRIMARY KEY(assignment_id,group_id)
);
CREATE TABLE assignment_submissions (
 assignment_id UUID NOT NULL REFERENCES assignments(id), user_id UUID NOT NULL REFERENCES users(id),
 text TEXT NOT NULL DEFAULT '', submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 score INTEGER, feedback TEXT, graded_by UUID REFERENCES users(id), graded_at TIMESTAMPTZ,
 PRIMARY KEY(assignment_id,user_id)
);
CREATE TABLE lesson_quizzes (
 id UUID PRIMARY KEY REFERENCES content_records(id), lesson_id UUID NOT NULL REFERENCES lessons(id)
);
CREATE TABLE quiz_attempts (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), quiz_id UUID NOT NULL REFERENCES lesson_quizzes(id), user_id UUID NOT NULL REFERENCES users(id),
 snapshot JSONB NOT NULL, answers JSONB, score NUMERIC(5,2), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), completed_at TIMESTAMPTZ
);
CREATE INDEX quiz_user ON quiz_attempts(user_id,quiz_id);
CREATE TABLE materials (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), content_id UUID NOT NULL REFERENCES content_records(id),
 title_ru VARCHAR(300) NOT NULL, title_kz VARCHAR(300) NOT NULL DEFAULT '',
 storage_key VARCHAR(200) NOT NULL UNIQUE, original_file_name VARCHAR(255) NOT NULL,
 mime_type VARCHAR(150) NOT NULL, size BIGINT NOT NULL CHECK(size > 0),
 created_by UUID NOT NULL REFERENCES users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 published BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX materials_content ON materials(content_id);
CREATE TABLE content_imports (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), created_by UUID NOT NULL REFERENCES users(id),
 file_name VARCHAR(255) NOT NULL, rows JSONB NOT NULL, errors JSONB NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('VALID','INVALID','IMPORTED')),
 result JSONB, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), imported_at TIMESTAMPTZ
);
CREATE INDEX imports_creator ON content_imports(created_by,created_at DESC);
