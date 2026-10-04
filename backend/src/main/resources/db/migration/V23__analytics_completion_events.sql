-- Preserve one completion timestamp through unrelated task edits, including note reviews.
ALTER TABLE study_tasks ADD COLUMN completed_at TIMESTAMPTZ;
UPDATE study_tasks SET completed_at=updated_at WHERE status='COMPLETED';
CREATE FUNCTION maintain_study_task_completion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.status='COMPLETED' THEN
    IF TG_OP='INSERT' OR OLD.status<>'COMPLETED' THEN
      NEW.completed_at := coalesce(NEW.completed_at,now());
    ELSE
      NEW.completed_at := OLD.completed_at;
    END IF;
  ELSE
    NEW.completed_at := NULL;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER study_task_completion BEFORE INSERT OR UPDATE ON study_tasks
FOR EACH ROW EXECUTE FUNCTION maintain_study_task_completion();
CREATE INDEX study_tasks_completed ON study_tasks(user_id,completed_at) WHERE status='COMPLETED';
CREATE INDEX theory_reads_analytics ON user_theory_progress(user_id,read_at) WHERE is_read;
CREATE INDEX lesson_completion_analytics ON lesson_progress(user_id,completed_at);

-- A projection over existing records, not a second event store.
CREATE VIEW statistics_activity_events AS
-- Legacy practice/theory timestamps are UTC wall times in the Docker baseline.
-- Normalise them explicitly; never use the database session timezone implicitly.
SELECT s.user_id,'TEST'::text AS kind,s.id::text AS event_id,s.completed_at AT TIME ZONE 'UTC' AS occurred_at,
 coalesce(b.name_ru,'Смешанная практика') AS title_ru,coalesce(b.name_kz,'Аралас жаттығу') AS title_kz,
 '/results/'||s.id AS href
FROM test_sessions s LEFT JOIN subjects b ON b.id=s.subject_id WHERE s.status='COMPLETED'
UNION ALL
SELECT p.user_id,'THEORY',p.theory_id::text,p.read_at AT TIME ZONE 'UTC',t.title_ru,t.title_kz,'/topics/'||t.topic_id
FROM user_theory_progress p JOIN theories t ON t.id=p.theory_id WHERE p.is_read AND p.read_at IS NOT NULL
UNION ALL
SELECT p.user_id,'LESSON',p.lesson_id::text,p.completed_at,l.title_ru,l.title_kz,'/lessons/'||l.id
FROM lesson_progress p JOIN lessons l ON l.id=p.lesson_id
UNION ALL
SELECT t.user_id,'PLANNER_TASK',t.id::text,t.completed_at,t.title_ru,t.title_kz,'/study'
FROM study_tasks t WHERE t.status='COMPLETED' AND t.completed_at IS NOT NULL
UNION ALL
SELECT r.user_id,'ASSIGNMENT',r.id::text,r.submitted_at,c.published_payload->>'titleRu',coalesce(c.published_payload->>'titleKz',''),'/assignments/'||a.id
FROM submission_revisions r JOIN assignments a ON a.id=r.assignment_id JOIN content_records c ON c.id=a.id
UNION ALL
SELECT a.user_id,'ERROR_RESOLVED',a.session_id::text||':'||a.question_id,a.completed_at AT TIME ZONE 'UTC',
 t.title_ru,t.title_kz,'/results/'||a.session_id
FROM (
 SELECT user_id,session_id,question_id,topic_id,completed_at,correct,
 lag(correct) OVER(PARTITION BY user_id,question_id ORDER BY completed_at,session_id) AS previous_correct
 FROM completed_question_activity
) a JOIN topics t ON t.id=a.topic_id WHERE a.correct AND a.previous_correct=false;
