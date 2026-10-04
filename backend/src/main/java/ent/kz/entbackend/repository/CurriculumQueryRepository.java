package ent.kz.entbackend.repository;

import ent.kz.entbackend.dto.*;
import ent.kz.entbackend.platform.content.ContentRepository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Aggregates once per request; the official syllabus must not add two queries per topic. */
@Repository
public class CurriculumQueryRepository {

  private final JdbcTemplate db;
  private final ContentRepository json;

  public CurriculumQueryRepository(JdbcTemplate db, ContentRepository json) {
    this.db = db;
    this.json = json;
  }

  public List<TopicResponse> topics(UUID subject, UUID id) {
    return db.query(
      """
      SELECT t.*,coalesce(q.total,0) AS questions,coalesce(th.total,0) AS theories,
      r.published_payload->'curriculum' AS curriculum,r.published_payload->>'contentLanguage' AS document_language,
      r.published_payload->>'sourceUrl' AS source_url,
      CASE WHEN r.published_payload->'curriculum'->>'platformCode' LIKE 'syllabus:%' THEN 'SYLLABUS' ELSE 'LEARNING' END AS content_role
      FROM topics t JOIN subjects s ON s.id=t.subject_id LEFT JOIN content_records r ON r.id=t.id
      LEFT JOIN(SELECT topic_id,count(*) AS total FROM eligible_practice_questions GROUP BY topic_id) q ON q.topic_id=t.id
      LEFT JOIN(SELECT topic_id,count(*) AS total FROM theories WHERE is_active GROUP BY topic_id) th ON th.topic_id=t.id
      WHERE t.is_active AND s.is_active AND (?::uuid IS NULL OR t.subject_id=?) AND (?::uuid IS NULL OR t.id=?)
      ORDER BY t.sort_order,t.id
      """,
      (r, n) ->
        new TopicResponse(
          r.getObject("id", UUID.class),
          r.getObject("subject_id", UUID.class),
          r.getString("title_ru"),
          r.getString("title_kz"),
          r.getString("description_ru"),
          r.getString("description_kz"),
          r.getInt("sort_order"),
          r.getLong("questions"),
          r.getLong("theories"),
          r.getString("content_role"),
          r.getString("curriculum") == null
            ? null
            : json.parse(r.getString("curriculum")),
          r.getString("document_language"),
          r.getString("source_url")
        ),
      subject,
      subject,
      id,
      id
    );
  }

  public List<SubjectResponse> subjects() {
    return db.query(
      """
      SELECT s.*,coalesce(q.total,0) AS available,r.published_payload->>'category' AS category
      FROM subjects s LEFT JOIN content_records r ON r.id=s.id
      LEFT JOIN(SELECT subject_id,count(*) AS total FROM eligible_practice_questions GROUP BY subject_id) q ON q.subject_id=s.id
      WHERE s.is_active ORDER BY s.created_at,s.id
      """,
      (r, n) ->
        new SubjectResponse(
          r.getObject("id", UUID.class),
          r.getString("name_ru"),
          r.getString("name_kz"),
          r.getString("icon"),
          r.getString("color"),
          r.getInt("available"),
          r.getInt("duration_minutes"),
          r.getString("category")
        )
    );
  }
}
