package ent.kz.entbackend.platform.content;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ent.kz.entbackend.platform.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the administrator-only baseline CRUD contract represented in the CMS. */
@Service
@Transactional
public class LegacyContentBridge {

  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final EntityManager em;
  private final Actor actor;
  private final AuditService audit;

  public LegacyContentBridge(
    JdbcTemplate db,
    ObjectMapper json,
    EntityManager em,
    Actor actor,
    AuditService audit
  ) {
    this.db = db;
    this.json = json;
    this.em = em;
    this.actor = actor;
    this.audit = audit;
  }

  public void lockExisting(UUID id) {
    db.queryForList("SELECT id FROM content_records WHERE id=? FOR UPDATE", id);
  }

  public void sync(ContentKind kind, UUID id) {
    actor.adminOnly();
    em.flush();
    String table = switch (kind) {
      case SUBJECT -> "subjects";
      case TOPIC -> "topics";
      case THEORY -> "theories";
      case QUESTION -> "questions";
      default -> throw new IllegalArgumentException();
    };
    Map<String, Object> row = db.queryForMap(
      "SELECT * FROM " + table + " WHERE id=?",
      id
    );
    ObjectNode p = json.createObjectNode();
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(
      "titleRu",
      kind == ContentKind.SUBJECT
        ? "name_ru"
        : kind == ContentKind.QUESTION
          ? "question_ru"
          : "title_ru"
    );
    fields.put(
      "titleKz",
      kind == ContentKind.SUBJECT
        ? "name_kz"
        : kind == ContentKind.QUESTION
          ? "question_kz"
          : "title_kz"
    );
    for (String name : List.of("description", "content", "explanation")) {
      fields.put(name + "Ru", name + "_ru");
      fields.put(name + "Kz", name + "_kz");
    }
    fields.putAll(
      Map.of(
        "sortOrder",
        "sort_order",
        "correctOptionId",
        "correct_option_id",
        "difficulty",
        "difficulty",
        "year",
        "year",
        "icon",
        "icon",
        "color",
        "color",
        "durationMinutes",
        "duration_minutes"
      )
    );
    fields.forEach((k, v) -> {
      if (row.containsKey(v)) p.set(k, json.valueToTree(row.get(v)));
    });
    if (kind == ContentKind.QUESTION) try {
      p.set("options", json.readTree(row.get("options").toString()));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    UUID parent =
      kind == ContentKind.SUBJECT
        ? null
        : (UUID) row.get(kind == ContentKind.TOPIC ? "subject_id" : "topic_id");
    String status = Boolean.TRUE.equals(row.get("is_active"))
      ? "PUBLISHED"
      : "ARCHIVED";
    db.update(
      """
      INSERT INTO content_records(id,kind,parent_id,owner_id,title_ru,title_kz,payload,published_payload,status,published_version,created_by,updated_by)
      VALUES (?,?,?,?,?,?,?::jsonb,CASE WHEN ?='PUBLISHED' THEN ?::jsonb ELSE NULL END,?,1,?,?)
      ON CONFLICT(id) DO UPDATE SET parent_id=excluded.parent_id,title_ru=excluded.title_ru,title_kz=excluded.title_kz,
      payload=content_records.payload||excluded.payload,
      published_payload=CASE WHEN excluded.status='PUBLISHED' THEN coalesce(content_records.published_payload,'{}'::jsonb)||excluded.payload ELSE NULL END,
      status=CASE WHEN excluded.status='ARCHIVED' THEN 'ARCHIVED' WHEN content_records.status IN ('DRAFT','REVIEW') THEN 'DRAFT' ELSE excluded.status END,version=content_records.version+1,published_version=CASE WHEN excluded.status='PUBLISHED' THEN content_records.version+1 ELSE NULL END,
      updated_by=excluded.updated_by,updated_at=now()
      """,
      id,
      kind.name(),
      parent,
      actor.id(),
      p
        .path("titleRu")
        .asText()
        .substring(0, Math.min(300, p.path("titleRu").asText().length())),
      p
        .path("titleKz")
        .asText("")
        .substring(0, Math.min(300, p.path("titleKz").asText("").length())),
      p.toString(),
      status,
      p.toString(),
      status,
      actor.id(),
      actor.id()
    );
    audit.record(id, kind.name(), "LEGACY_ADMIN_WRITE", null);
  }
}
