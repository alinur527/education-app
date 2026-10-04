package ent.kz.entbackend.platform.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import ent.kz.entbackend.dto.QuestionSnapshot;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LearningRepository {

  private final JdbcTemplate db;
  private final ObjectMapper json;

  public LearningRepository(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  private static final String LATEST =
    "SELECT DISTINCT ON(question_id) * FROM completed_question_activity WHERE user_id=? ORDER BY question_id,completed_at DESC,session_id DESC";

  private static final String AVAILABLE_CONTEXT = """
  AND (coalesce(e.snapshot->'assessment'->>'contextKey','')='' OR EXISTS(
    SELECT 1 FROM content_records c JOIN context_versions v ON v.content_id=c.id
    WHERE c.id::text=split_part(e.snapshot->'assessment'->>'contextKey',':',1)
    AND v.version::text=split_part(e.snapshot->'assessment'->>'contextKey',':',2)
    AND c.kind='CONTEXT' AND c.parent_id=e.topic_id AND c.published_payload IS NOT NULL AND c.status<>'ARCHIVED'))
  """;

  public List<Map<String, Object>> errors(UUID user) {
    return db.queryForList(
      "SELECT e.topic_id AS \"topicId\",min(e.snapshot->>'topicRu') AS \"titleRu\",min(e.snapshot->>'topicKz') AS \"titleKz\",count(*) AS count FROM (" +
        LATEST +
        ") e WHERE NOT correct AND topic_id IS NOT NULL " +
        AVAILABLE_CONTEXT +
        " AND EXISTS(SELECT 1 FROM topics t JOIN subjects s ON s.id=t.subject_id WHERE t.id=e.topic_id AND t.is_active AND s.is_active) GROUP BY e.topic_id ORDER BY count(*) DESC,e.topic_id",
      user
    );
  }

  public List<QuestionSnapshot> review(UUID user, UUID topic) {
    return db.query(
      "SELECT snapshot::text FROM (" +
        LATEST +
        ") e WHERE NOT correct AND topic_id=? " +
        AVAILABLE_CONTEXT +
        " ORDER BY completed_at DESC,question_id LIMIT 50",
      (r, n) -> {
        try {
          return json.readValue(r.getString(1), QuestionSnapshot.class);
        } catch (Exception ex) {
          throw new IllegalStateException("Invalid frozen question", ex);
        }
      },
      user,
      topic
    );
  }

  public Map<String, Object> overview(UUID user) {
    return db.queryForMap(
      "SELECT count(*) AS \"questionsAnswered\",count(DISTINCT session_id) AS \"testsCompleted\",coalesce(round(100.0*count(*) FILTER(WHERE correct)/nullif(count(*),0),1),0) AS accuracy FROM completed_question_activity WHERE user_id=?",
      user
    );
  }

  public List<Map<String, Object>> topics(UUID user) {
    return topics(user, null);
  }

  public List<Map<String, Object>> topics(UUID user, java.time.Instant asOf) {
    java.sql.Timestamp cutoff =
      asOf == null ? null : java.sql.Timestamp.from(asOf);
    return db.queryForList(
      """
      WITH attempts AS (
        SELECT topic_id,100.0*sum(earned_points)/nullif(sum(max_points),0) AS score,count(*) AS total_questions,count(*) FILTER(WHERE correct) AS correct_answers,completed_at,
        row_number() OVER(PARTITION BY topic_id ORDER BY completed_at DESC,session_id DESC) AS rn
        FROM completed_question_activity WHERE user_id=? AND (?::timestamptz IS NULL OR (completed_at AT TIME ZONE 'UTC')<=?) GROUP BY topic_id,session_id,completed_at
      ), scores AS (
        SELECT topic_id,count(*) AS attempts,max(score) AS best,max(score) FILTER(WHERE rn=1) AS last,
        100.0*sum(correct_answers) FILTER(WHERE rn<=3)/nullif(sum(total_questions) FILTER(WHERE rn<=3),0) AS accuracy
        FROM attempts GROUP BY topic_id
      ), reading AS (
        SELECT t.topic_id,count(*) AS total,count(*) FILTER(WHERE p.is_read AND (?::timestamptz IS NULL OR (p.read_at AT TIME ZONE 'UTC')<=?)) AS done
        FROM theories t LEFT JOIN user_theory_progress p ON p.theory_id=t.id AND p.user_id=?
        WHERE t.is_active GROUP BY t.topic_id
      )
      SELECT t.id AS "topicId",s.id AS "subjectId",t.title_ru AS "titleRu",t.title_kz AS "titleKz",
        s.name_ru AS "subjectRu",s.name_kz AS "subjectKz",coalesce(a.attempts,0) AS attempts,
        coalesce(a.best,0) AS "bestScore",coalesce(a.last,0) AS "lastScore",coalesce(round(a.accuracy,1),0) AS "recentAccuracy",
        coalesce(qb.total,0) AS "questionCount",coalesce(r.total,0) AS "theoryTotal",coalesce(r.done,0) AS "theoryRead",
        round(CASE WHEN coalesce(r.total,0)=0 THEN coalesce(a.accuracy,0)
        ELSE 20.0*r.done/r.total+0.8*coalesce(a.accuracy,0) END,1) AS mastery
      FROM topics t JOIN subjects s ON s.id=t.subject_id LEFT JOIN scores a ON a.topic_id=t.id LEFT JOIN reading r ON r.topic_id=t.id LEFT JOIN(SELECT topic_id,count(*) AS total FROM eligible_practice_questions GROUP BY topic_id) qb ON qb.topic_id=t.id
      WHERE t.is_active AND s.is_active AND (coalesce(qb.total,0)>0 OR coalesce(r.total,0)>0) ORDER BY mastery,t.sort_order,t.id
      """,
      user,
      cutoff,
      cutoff,
      cutoff,
      cutoff,
      user
    );
  }

  public Map<String, Object> continueTopic(UUID user) {
    var rows = db.queryForList(
      """
      SELECT t.id AS "topicId",t.title_ru AS "titleRu",t.title_kz AS "titleKz" FROM (
        SELECT topic_id,coalesce(completed_at,started_at) AS at FROM test_sessions WHERE user_id=?
        UNION ALL SELECT th.topic_id,coalesce(p.last_read_at,p.read_at) AS at FROM user_theory_progress p JOIN theories th ON th.id=p.theory_id WHERE p.user_id=? AND p.is_read
      ) a JOIN topics t ON t.id=a.topic_id JOIN subjects s ON s.id=t.subject_id
      WHERE t.is_active AND s.is_active ORDER BY a.at DESC NULLS LAST,t.id LIMIT 1
      """,
      user,
      user
    );
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public void read(UUID user, UUID theory) {
    int count = db.update(
      "INSERT INTO user_theory_progress(user_id,theory_id,is_read,read_at,last_read_at) SELECT ?,th.id,true,now(),now() FROM theories th JOIN topics t ON t.id=th.topic_id JOIN subjects s ON s.id=t.subject_id WHERE th.id=? AND th.is_active AND t.is_active AND s.is_active ON CONFLICT(user_id,theory_id) DO UPDATE SET is_read=true,read_at=coalesce(user_theory_progress.read_at,excluded.read_at),last_read_at=excluded.last_read_at",
      user,
      theory
    );
    if (
      count == 0
    ) throw ent.kz.entbackend.platform.PlatformException.missing();
  }
}
