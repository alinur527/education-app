CREATE TABLE study_profiles (
 user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
 goal VARCHAR(300) NOT NULL DEFAULT '', target_date DATE NOT NULL,
 available_days JSONB NOT NULL, minutes_per_day INTEGER NOT NULL CHECK(minutes_per_day BETWEEN 10 AND 360),
 time_zone VARCHAR(80) NOT NULL DEFAULT 'Asia/Almaty', selected_subjects JSONB NOT NULL DEFAULT '[]',
 revision BIGINT NOT NULL DEFAULT 1, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE study_tasks (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 source_key VARCHAR(160) NOT NULL, kind VARCHAR(30) NOT NULL,
 target_kind VARCHAR(20) NOT NULL, target_id UUID NOT NULL,
 title_ru VARCHAR(300) NOT NULL, title_kz VARCHAR(300) NOT NULL, reason VARCHAR(40) NOT NULL,
 scheduled_at TIMESTAMPTZ, duration_minutes INTEGER NOT NULL CHECK(duration_minutes BETWEEN 5 AND 360),
 status VARCHAR(20) NOT NULL DEFAULT 'PLANNED' CHECK(status IN ('PLANNED','COMPLETED','SKIPPED')),
 pinned BOOLEAN NOT NULL DEFAULT false, manually_moved BOOLEAN NOT NULL DEFAULT false,
 revision BIGINT NOT NULL DEFAULT 1, generation UUID,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(user_id,source_key)
);
CREATE INDEX study_tasks_calendar ON study_tasks(user_id,scheduled_at,id);
CREATE TABLE study_notes (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 target_kind VARCHAR(20) NOT NULL CHECK(target_kind IN ('TOPIC','LESSON')), target_id UUID NOT NULL,
 title VARCHAR(300) NOT NULL, body TEXT NOT NULL DEFAULT '', bookmarked BOOLEAN NOT NULL DEFAULT false,
 card_front VARCHAR(4000) NOT NULL DEFAULT '', card_back VARCHAR(4000) NOT NULL DEFAULT '',
 next_review_at TIMESTAMPTZ, interval_days INTEGER NOT NULL DEFAULT 0, review_count INTEGER NOT NULL DEFAULT 0,
 revision BIGINT NOT NULL DEFAULT 1, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX study_notes_user ON study_notes(user_id,updated_at DESC,id);
CREATE INDEX study_notes_due ON study_notes(user_id,next_review_at);
CREATE TABLE study_notification_preferences (
 user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
 enabled BOOLEAN NOT NULL DEFAULT true, plans BOOLEAN NOT NULL DEFAULT true, deadlines BOOLEAN NOT NULL DEFAULT true,
 grades BOOLEAN NOT NULL DEFAULT true, materials BOOLEAN NOT NULL DEFAULT true,
 quiet_enabled BOOLEAN NOT NULL DEFAULT true, quiet_start TIME NOT NULL DEFAULT '22:00', quiet_end TIME NOT NULL DEFAULT '08:00',
 revision BIGINT NOT NULL DEFAULT 1, updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE study_notifications (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 event_key VARCHAR(250) NOT NULL, kind VARCHAR(20) NOT NULL, target_kind VARCHAR(20) NOT NULL, target_id UUID NOT NULL,
 title_ru VARCHAR(300) NOT NULL, title_kz VARCHAR(300) NOT NULL, is_read BOOLEAN NOT NULL DEFAULT false,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(user_id,event_key)
);
CREATE INDEX study_notifications_user ON study_notifications(user_id,created_at DESC,id);
