package ent.kz.entbackend.platform.courses;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.assessment.Assessment;
import ent.kz.entbackend.platform.content.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QuizService {

  private final JdbcTemplate db;
  private final Actor actor;
  private final ContentRepository records;
  private final ContentPolicy policy;

  public QuizService(
    JdbcTemplate db,
    Actor actor,
    ContentRepository records,
    ContentPolicy policy
  ) {
    this.db = db;
    this.actor = actor;
    this.records = records;
    this.policy = policy;
  }

  public Map<String, Object> start(UUID id) {
    ContentRecord c = records.get(id, false);
    policy.read(c);
    if (
      c.kind() != ContentKind.QUIZ || !policy.visible(c)
    ) throw PlatformException.missing();
    ObjectNode frozen = c.publishedPayload().deepCopy();
    for (JsonNode q : frozen.path("questions"))
      ((ObjectNode) q).setAll(Assessment.freeze(q));
    UUID attempt = UUID.randomUUID();
    db.update(
      "INSERT INTO quiz_attempts(id,quiz_id,user_id,snapshot) VALUES (?,?,?,?::jsonb)",
      attempt,
      id,
      actor.id(),
      frozen.toString()
    );
    return get(attempt);
  }

  public Map<String, Object> get(UUID id) {
    var r = owned(id, false);
    JsonNode snapshot = records.parse(r.get("snapshot").toString());
    ArrayNode questions = ((ArrayNode) snapshot.path("questions")).deepCopy();
    if (r.get("completed_at") == null) {
      ArrayNode safe = new ObjectMapper().createArrayNode();
      for (JsonNode q : questions) {
        ObjectNode item = Assessment.publicView(q);
        item.set("titleRu", q.path("titleRu"));
        item.set("titleKz", q.path("titleKz"));
        safe.add(item);
      }
      questions = safe;
    }
    var out = new LinkedHashMap<String, Object>();
    out.put("id", id);
    out.put("quizId", r.get("quiz_id"));
    out.put("questions", questions);
    out.put("score", r.get("score"));
    out.put(
      "answers",
      r.get("answers") == null
        ? null
        : records.parse(r.get("answers").toString())
    );
    return out;
  }

  public Map<String, Object> finish(UUID id, List<JsonNode> answers) {
    var r = owned(id, true);
    if (r.get("completed_at") != null) return get(id);
    JsonNode questions = records
      .parse(r.get("snapshot").toString())
      .path("questions");
    PlatformException.require(
      answers != null && answers.size() == questions.size(),
      "ANSWER_COUNT"
    );
    int earned = 0,
      maximum = 0;
    for (int i = 0; i < answers.size(); i++) {
      JsonNode supplied = answers.get(i);
      if (supplied != null && supplied.isTextual()) supplied =
        new ObjectMapper()
          .createObjectNode()
          .put("selectedOptionId", supplied.asText());
      Assessment.Grade grade = Assessment.grade(questions.get(i), supplied);
      earned += grade.earned();
      maximum += grade.maximum();
    }
    db.update(
      "UPDATE quiz_attempts SET answers=?::jsonb,score=?,completed_at=now() WHERE id=?",
      records.encode(answers),
      Math.round((earned * 10000.0) / maximum) / 100.0,
      id
    );
    return get(id);
  }

  private Map<String, Object> owned(UUID id, boolean lock) {
    var rows = db.queryForList(
      "SELECT * FROM quiz_attempts WHERE id=? AND user_id=?" +
        (lock ? " FOR UPDATE" : ""),
      id,
      actor.id()
    );
    if (rows.isEmpty()) throw PlatformException.missing();
    return rows.getFirst();
  }
}
