package ent.kz.entbackend.platform.assessment;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PracticeRepository {

  private final JdbcTemplate db;

  public PracticeRepository(JdbcTemplate db) {
    this.db = db;
  }

  private static final String ELIGIBLE = """
  FROM eligible_practice_questions q JOIN topics t ON t.id=q.topic_id JOIN subjects s ON s.id=q.subject_id
  LEFT JOIN content_records c ON c.id=q.id
  WHERE q.is_active AND t.is_active AND s.is_active
    AND btrim(coalesce(q.question_ru,''))<>'' AND btrim(coalesce(q.question_kz,''))<>''
  """;

  public List<Map<String, Object>> subjects() {
    return db.queryForList(
      "SELECT s.id,s.name_ru AS \"titleRu\",s.name_kz AS \"titleKz\",count(q.id) AS available FROM subjects s LEFT JOIN topics t ON t.subject_id=s.id AND t.is_active LEFT JOIN eligible_practice_questions q ON q.topic_id=t.id AND q.is_active AND btrim(coalesce(q.question_kz,''))<>'' WHERE s.is_active GROUP BY s.id ORDER BY s.name_ru,s.id"
    );
  }

  public List<Map<String, Object>> bank(UUID subject) {
    return db.queryForList(
      "SELECT coalesce(q.assessment->>'questionType','SINGLE_CHOICE') AS type,coalesce(q.assessment->>'contextKey','')<>'' AS context,q.difficulty,count(*) AS available,count(*) FILTER(WHERE EXISTS(SELECT 1 FROM editorial_reviews e WHERE e.content_id=q.id AND e.reviewed_payload=c.published_payload AND e.decision='APPROVED' AND NOT EXISTS(SELECT 1 FROM editorial_reviews newer WHERE newer.content_id=e.content_id AND newer.created_at>e.created_at))) AS reviewed " +
        ELIGIBLE +
        " AND q.subject_id=? GROUP BY 1,2,3",
      subject
    );
  }

  public List<Map<String, Object>> topics() {
    return db.queryForList(
      "SELECT t.id,t.subject_id AS \"subjectId\",t.title_ru AS \"titleRu\",t.title_kz AS \"titleKz\",coalesce(q.difficulty,'medium') AS difficulty,count(*) AS available " +
        ELIGIBLE +
        " GROUP BY t.id,q.difficulty ORDER BY t.title_ru,t.id LIMIT 3000"
    );
  }

  public List<UUID> choose(
    List<UUID> subjects,
    List<UUID> topics,
    String difficulty,
    int limit
  ) {
    List<Object> args = new ArrayList<>(subjects);
    String filter =
      " AND q.subject_id IN (" +
      String.join(",", Collections.nCopies(subjects.size(), "?")) +
      ")";
    if (!topics.isEmpty()) {
      filter +=
        " AND q.topic_id IN (" +
        String.join(",", Collections.nCopies(topics.size(), "?")) +
        ")";
      args.addAll(topics);
    }
    if (!difficulty.isEmpty()) {
      filter += " AND q.difficulty=?";
      args.add(difficulty);
    }
    args.add(limit);
    return db.query(
      "SELECT q.id " + ELIGIBLE + filter + " ORDER BY random() LIMIT ?",
      (r, n) -> r.getObject(1, UUID.class),
      args.toArray()
    );
  }
}
