package ent.kz.entbackend.platform.content;

import ent.kz.entbackend.platform.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ContentPolicy {

  private final Actor actor;
  private final ContentRepository records;
  private final JdbcTemplate db;

  public ContentPolicy(
    Actor actor,
    ContentRepository records,
    JdbcTemplate db
  ) {
    this.actor = actor;
    this.records = records;
    this.db = db;
  }

  public boolean canEdit(ContentRecord c) {
    return (
      actor.editor() ||
      (actor.staff() &&
        Objects.equals(c.ownerId(), actor.id()) &&
        !c.kind().ent())
    );
  }

  public void edit(ContentRecord c) {
    if (!canEdit(c)) throw PlatformException.forbidden();
  }

  public UUID course(ContentRecord c) {
    for (int i = 0; i < 5; i++) {
      if (c.kind() == ContentKind.COURSE) return c.id();
      if (c.parentId() == null) return null;
      c = records.get(c.parentId(), false);
    }
    throw PlatformException.missing();
  }

  public boolean enrolled(UUID course) {
    return Boolean.TRUE.equals(
      db.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM enrollments WHERE course_id=? AND user_id=? AND status IN ('ACTIVE','COMPLETED'))",
        Boolean.class,
        course,
        actor.id()
      )
    );
  }

  public boolean visible(ContentRecord c) {
    if (
      c.publishedPayload() == null || c.status().equals("ARCHIVED")
    ) return false;
    return c.parentId() == null || visible(records.get(c.parentId(), false));
  }

  public void read(ContentRecord c) {
    if (canEdit(c)) return;
    if (!visible(c)) throw PlatformException.missing();
    UUID course = course(c);
    if (course != null) {
      ContentRecord root = records.get(course, false);
      boolean member = enrolled(course);
      if (c.kind() == ContentKind.COURSE) {
        if (
          !member &&
          !root
            .publishedPayload()
            .path("visibility")
            .asText("PRIVATE")
            .equals("PUBLIC")
        ) throw PlatformException.missing();
      } else if (!member) throw PlatformException.missing();
    }
    if (
      c.kind() == ContentKind.ASSIGNMENT &&
      !Boolean.TRUE.equals(
        db.queryForObject(
          "SELECT EXISTS(SELECT 1 FROM assignment_groups a JOIN group_members g ON g.group_id=a.group_id WHERE a.assignment_id=? AND g.user_id=?)",
          Boolean.class,
          c.id(),
          actor.id()
        )
      )
    ) throw PlatformException.missing();
  }
}
