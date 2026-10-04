package ent.kz.entbackend.platform.submissions;

import ent.kz.entbackend.entity.UserRole;
import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.content.*;
import ent.kz.entbackend.platform.courses.CourseService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SubmissionAccess {

  private final Actor actor;
  private final JdbcTemplate db;
  private final ContentRepository records;
  private final ContentPolicy policy;
  private final CourseService courses;

  public SubmissionAccess(
    Actor actor,
    JdbcTemplate db,
    ContentRepository records,
    ContentPolicy policy,
    CourseService courses
  ) {
    this.actor = actor;
    this.db = db;
    this.records = records;
    this.policy = policy;
    this.courses = courses;
  }

  public void upload(UUID assignment) {
    if (
      actor.user().getRole() != UserRole.STUDENT
    ) throw PlatformException.forbidden();
    var content = records.get(assignment, true);
    UUID course = policy.course(content);
    if (course != null) courses.lockEnrollment(course, actor.id());
    policy.read(content);
    if (
      content.kind() != ContentKind.ASSIGNMENT || !policy.visible(content)
    ) throw PlatformException.missing();
  }

  public void read(UUID assignment, UUID owner) {
    if (actor.admin()) return;
    if (
      actor.id().equals(owner) && actor.user().getRole() == UserRole.STUDENT
    ) return;
    if (
      actor.user().getRole() != UserRole.TEACHER
    ) throw PlatformException.missing();
    if (
      !Boolean.TRUE.equals(
        db.queryForObject(
          """
          SELECT EXISTS(SELECT 1 FROM assignments a JOIN assignment_groups ag ON ag.assignment_id=a.id
          JOIN learning_groups g ON g.id=ag.group_id JOIN group_members gm ON gm.group_id=g.id
          WHERE a.id=? AND a.teacher_id=? AND g.teacher_id=? AND gm.user_id=?)
          """,
          Boolean.class,
          assignment,
          actor.id(),
          actor.id(),
          owner
        )
      )
    ) throw PlatformException.missing();
  }

  public void own(UUID owner) {
    if (
      !actor.id().equals(owner) || actor.user().getRole() != UserRole.STUDENT
    ) throw PlatformException.missing();
  }
}
