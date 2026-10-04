package ent.kz.entbackend.platform.content;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SearchRepository {

  private final JdbcTemplate db;

  public SearchRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> search(UUID user, String q) {
    return db.queryForList(
      """
      WITH RECURSIVE visible AS (
        SELECT id,kind,parent_id,published_payload,CASE WHEN kind='COURSE' THEN id ELSE NULL::uuid END AS course_id
        FROM content_records WHERE parent_id IS NULL AND published_payload IS NOT NULL AND status<>'ARCHIVED'
        UNION ALL
        SELECT c.id,c.kind,c.parent_id,c.published_payload,p.course_id
        FROM content_records c JOIN visible p ON p.id=c.parent_id
        WHERE c.published_payload IS NOT NULL AND c.status<>'ARCHIVED'
      )
      SELECT id,kind,published_payload->>'titleRu' AS "titleRu",published_payload->>'titleKz' AS "titleKz"
      FROM visible v WHERE kind IN ('SUBJECT','TOPIC','COURSE','LESSON')
        AND (course_id IS NULL OR EXISTS(SELECT 1 FROM enrollments e WHERE e.course_id=v.course_id AND e.user_id=? AND e.status IN ('ACTIVE','COMPLETED'))
        OR (kind='COURSE' AND published_payload->>'visibility'='PUBLIC'))
        AND (published_payload->>'titleRu' ILIKE ? OR published_payload->>'titleKz' ILIKE ?)
      ORDER BY kind,published_payload->>'titleRu',id LIMIT 30
      """,
      user,
      "%" + q + "%",
      "%" + q + "%"
    );
  }
}
