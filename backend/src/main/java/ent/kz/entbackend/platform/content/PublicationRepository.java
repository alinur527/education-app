package ent.kz.entbackend.platform.content;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Writes published projections into the independent ENT and LMS models. */
@Repository
public class PublicationRepository {

  private final JdbcTemplate db;
  private final ContentPolicy policy;

  public PublicationRepository(JdbcTemplate db, ContentPolicy policy) {
    this.db = db;
    this.policy = policy;
  }

  public void project(ContentRecord c, boolean active) {
    JsonNode p = c.payload();
    UUID id = c.id(),
      parent = c.parentId();
    String ru = p.path("titleRu").asText(),
      kz = p.path("titleKz").asText("");
    switch (c.kind()) {
      case SUBJECT -> db.update(
        "INSERT INTO subjects(id,name_ru,name_kz,icon,color,question_count,duration_minutes,is_active) VALUES (?,?,?,?,?,0,?,?) ON CONFLICT(id) DO UPDATE SET name_ru=excluded.name_ru,name_kz=excluded.name_kz,icon=excluded.icon,color=excluded.color,duration_minutes=excluded.duration_minutes,is_active=excluded.is_active,updated_at=now()",
        id,
        ru,
        kz,
        p.path("icon").asText("book"),
        p.path("color").asText("#315ae8"),
        p.path("durationMinutes").asInt(90),
        active
      );
      case TOPIC -> {
        db.update(
          "INSERT INTO topics(id,subject_id,title_ru,title_kz,description_ru,description_kz,sort_order,is_active) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET title_ru=excluded.title_ru,title_kz=excluded.title_kz,description_ru=excluded.description_ru,description_kz=excluded.description_kz,sort_order=excluded.sort_order,is_active=excluded.is_active,updated_at=now()",
          id,
          parent,
          ru,
          kz,
          text(p, "descriptionRu"),
          text(p, "descriptionKz"),
          p.path("sortOrder").asInt(0),
          active
        );
        db.update(
          "UPDATE questions SET topic_ru=?,topic_kz=? WHERE topic_id=?",
          ru,
          kz,
          id
        );
      }
      case THEORY -> db.update(
        "INSERT INTO theories(id,subject_id,topic_id,title_ru,title_kz,content_ru,content_kz,sort_order,is_active) SELECT ?,subject_id,id,?,?,?,?,?,? FROM topics WHERE id=? ON CONFLICT(id) DO UPDATE SET title_ru=excluded.title_ru,title_kz=excluded.title_kz,content_ru=excluded.content_ru,content_kz=excluded.content_kz,sort_order=excluded.sort_order,is_active=excluded.is_active,updated_at=now()",
        id,
        ru,
        kz,
        text(p, "contentRu"),
        text(p, "contentKz"),
        p.path("sortOrder").asInt(0),
        active,
        parent
      );
      case QUESTION -> {
        db.update(
          "INSERT INTO questions(id,subject_id,topic_id,topic_ru,topic_kz,question_ru,question_kz,options,correct_option_id,explanation_ru,explanation_kz,difficulty,year,is_active) SELECT ?,subject_id,id,title_ru,title_kz,?,?,?::jsonb,?,?,?,?,?,? FROM topics WHERE id=? ON CONFLICT(id) DO UPDATE SET question_ru=excluded.question_ru,question_kz=excluded.question_kz,options=excluded.options,correct_option_id=excluded.correct_option_id,explanation_ru=excluded.explanation_ru,explanation_kz=excluded.explanation_kz,difficulty=excluded.difficulty,year=excluded.year,is_active=excluded.is_active,updated_at=now()",
          id,
          ru,
          kz,
          p.path("options").toString(),
          p.hasNonNull("correctOptionId") ? text(p, "correctOptionId") : null,
          text(p, "explanationRu"),
          text(p, "explanationKz"),
          p.path("difficulty").asText("medium"),
          p.hasNonNull("year") ? p.get("year").asInt() : null,
          active,
          parent
        );
        var assessment =
          ent.kz.entbackend.platform.assessment.Assessment.freeze(p);
        if (p.hasNonNull("contextId")) {
          UUID context = ContentValidation.uuid(p.path("contextId").asText());
          var versions = db.queryForList(
            "SELECT v.version FROM context_versions v JOIN content_records c ON c.id=v.content_id WHERE c.id=? AND c.parent_id=? AND c.published_payload IS NOT NULL AND c.status<>'ARCHIVED' AND (?=0 OR v.version=?) ORDER BY v.version DESC LIMIT 1",
            context,
            parent,
            p.path("contextVersion").asLong(0),
            p.path("contextVersion").asLong(0)
          );
          if (
            active && versions.isEmpty()
          ) throw new ent.kz.entbackend.platform.PlatformException(
            400,
            "CONTEXT_NOT_PUBLISHED"
          );
          if (!versions.isEmpty()) assessment.put(
            "contextKey",
            context + ":" + versions.getFirst().get("version")
          );
        }
        db.update(
          "UPDATE questions SET assessment=?::jsonb WHERE id=?",
          assessment.toString(),
          id
        );
      }
      case CONTEXT -> {
        if (active) db.update(
          "INSERT INTO context_versions(content_id,version,payload) VALUES (?,?,?::jsonb)",
          id,
          c.version() + 1,
          p.toString()
        );
      }
      case COURSE -> db.update(
        "INSERT INTO courses(id,teacher_id,title_ru,title_kz,description_ru,description_kz,visibility,self_enroll,icon) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET title_ru=excluded.title_ru,title_kz=excluded.title_kz,description_ru=excluded.description_ru,description_kz=excluded.description_kz,visibility=excluded.visibility,self_enroll=excluded.self_enroll,icon=excluded.icon",
        id,
        c.ownerId(),
        ru,
        kz,
        text(p, "descriptionRu"),
        text(p, "descriptionKz"),
        p.path("visibility").asText("PRIVATE"),
        p.path("selfEnroll").asBoolean(false),
        p.path("icon").asText("book")
      );
      case MODULE -> db.update(
        "INSERT INTO course_modules(id,course_id,title_ru,title_kz,sort_order) VALUES (?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET title_ru=excluded.title_ru,title_kz=excluded.title_kz,sort_order=excluded.sort_order",
        id,
        parent,
        ru,
        kz,
        p.path("sortOrder").asInt(0)
      );
      case LESSON -> db.update(
        "INSERT INTO lessons(id,module_id,title_ru,title_kz,sort_order) VALUES (?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET title_ru=excluded.title_ru,title_kz=excluded.title_kz,sort_order=excluded.sort_order",
        id,
        parent,
        ru,
        kz,
        p.path("sortOrder").asInt(0)
      );
      case QUIZ -> db.update(
        "INSERT INTO lesson_quizzes(id,lesson_id) VALUES (?,?) ON CONFLICT(id) DO NOTHING",
        id,
        parent
      );
      case ASSIGNMENT -> db.update(
        "INSERT INTO assignments(id,course_id,lesson_id,teacher_id,due_at,max_score) VALUES (?,?,(SELECT id FROM lessons WHERE id=?),?,?,?) ON CONFLICT(id) DO UPDATE SET due_at=excluded.due_at,max_score=excluded.max_score",
        id,
        policy.course(c),
        parent,
        c.ownerId(),
        text(p, "dueAt").isBlank()
          ? null
          : OffsetDateTime.parse(text(p, "dueAt")),
        p.path("maxScore").asInt(100)
      );
    }
  }

  public void archive(ContentRecord c) {
    String table = switch (c.kind()) {
      case SUBJECT -> "subjects";
      case TOPIC -> "topics";
      case THEORY -> "theories";
      case QUESTION -> "questions";
      default -> null;
    };
    if (table != null) db.update(
      "UPDATE " + table + " SET is_active=false,updated_at=now() WHERE id=?",
      c.id()
    );
  }

  private String text(JsonNode p, String k) {
    return p.path(k).asText("");
  }
}
