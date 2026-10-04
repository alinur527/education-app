package ent.kz.entbackend.platform.study;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Explicit user argument also makes background checks independent of an HTTP principal. */
@Component
public class StudyAccess {

  private final JdbcTemplate db;

  public StudyAccess(JdbcTemplate db) {
    this.db = db;
  }

  public boolean available(UUID user, String kind, UUID id) {
    if (kind.equals("TASK")) return db
      .queryForList(
        "SELECT target_kind,target_id FROM study_tasks WHERE id=? AND user_id=?",
        id,
        user
      )
      .stream()
      .anyMatch(r ->
        available(
          user,
          (String) r.get("target_kind"),
          (UUID) r.get("target_id")
        )
      );
    if (kind.equals("NOTE")) return Boolean.TRUE.equals(
      db.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM study_notes WHERE id=? AND user_id=?)",
        Boolean.class,
        id,
        user
      )
    );
    if (
      !Set.of("TOPIC", "LESSON", "ASSIGNMENT", "COURSE").contains(kind)
    ) return false;
    var rows = db.queryForList(
      """
      WITH RECURSIVE ancestors AS (
        SELECT id,parent_id,kind,published_payload,status,ARRAY[id] AS seen FROM content_records WHERE id=? AND kind=?
        UNION ALL SELECT c.id,c.parent_id,c.kind,c.published_payload,c.status,a.seen||c.id
        FROM content_records c JOIN ancestors a ON c.id=a.parent_id WHERE NOT c.id=ANY(a.seen)
      ) SELECT id,kind,parent_id,published_payload IS NOT NULL AND status<>'ARCHIVED' AS visible FROM ancestors
      """,
      id,
      kind
    );
    if (
      rows.isEmpty() ||
      rows.stream().anyMatch(r -> !Boolean.TRUE.equals(r.get("visible")))
    ) return false;
    if (rows.stream().noneMatch(r -> r.get("parent_id") == null)) return false;
    UUID course = rows
      .stream()
      .filter(r -> r.get("kind").equals("COURSE"))
      .map(r -> (UUID) r.get("id"))
      .findFirst()
      .orElse(null);
    if (
      course != null &&
      !Boolean.TRUE.equals(
        db.queryForObject(
          "SELECT EXISTS(SELECT 1 FROM enrollments WHERE course_id=? AND user_id=? AND status IN ('ACTIVE','COMPLETED'))",
          Boolean.class,
          course,
          user
        )
      )
    ) return false;
    return (
      !kind.equals("ASSIGNMENT") ||
      Boolean.TRUE.equals(
        db.queryForObject(
          "SELECT EXISTS(SELECT 1 FROM assignment_groups ag JOIN group_members gm ON gm.group_id=ag.group_id WHERE ag.assignment_id=? AND gm.user_id=?)",
          Boolean.class,
          id,
          user
        )
      )
    );
  }

  public String url(String kind, UUID id) {
    return switch (kind) {
      case "TOPIC" -> "/topics/" + id;
      case "LESSON" -> "/lessons/" + id;
      case "ASSIGNMENT" -> "/assignments/" + id;
      case "COURSE" -> "/courses/" + id;
      case "NOTE" -> "/notes?review=true";
      case "TASK" -> "/study";
      default -> "/study";
    };
  }
}
