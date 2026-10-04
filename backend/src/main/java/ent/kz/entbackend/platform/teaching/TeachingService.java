package ent.kz.entbackend.platform.teaching;

import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.content.*;
import ent.kz.entbackend.platform.courses.CourseService;
import ent.kz.entbackend.platform.submissions.SubmissionRevisionService;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TeachingService {

  public record NewGroup(
    @NotBlank @Size(max = 200) String name,
    @NotNull UUID courseId
  ) {}

  public record Member(@NotBlank @Email String email) {}

  public record Submission(
    @Size(max = 20000) String text,
    @Size(max = 5) List<@NotNull UUID> fileIds,
    UUID requestKey,
    @Min(0) Long revision
  ) {}

  public record Grade(
    @Min(0) int score,
    @Size(max = 10000) String feedback,
    @Min(1) long revision
  ) {}

  private final JdbcTemplate db;
  private final Actor actor;
  private final ContentRepository records;
  private final ContentPolicy policy;
  private final CourseService courses;
  private final AuditService audit;
  private final SubmissionRevisionService submissionRevisions;

  public TeachingService(
    JdbcTemplate db,
    Actor actor,
    ContentRepository records,
    ContentPolicy policy,
    CourseService courses,
    AuditService audit,
    SubmissionRevisionService submissionRevisions
  ) {
    this.db = db;
    this.actor = actor;
    this.records = records;
    this.policy = policy;
    this.courses = courses;
    this.audit = audit;
    this.submissionRevisions = submissionRevisions;
  }

  private void teacher() {
    if (
      !actor.admin() &&
      actor.user().getRole() != ent.kz.entbackend.entity.UserRole.TEACHER
    ) throw PlatformException.forbidden();
  }

  public ContentDtos.Page<Map<String, Object>> groups(String q, int page) {
    teacher();
    page = Math.max(page, 0);
    String where = " WHERE (? OR g.teacher_id=?) AND g.name ILIKE ?";
    var rows = db.queryForList(
      "SELECT g.id,g.name,g.course_id AS \"courseId\",c.title_ru AS \"courseRu\",c.title_kz AS \"courseKz\",(SELECT count(*) FROM group_members m WHERE m.group_id=g.id) AS students FROM learning_groups g JOIN courses c ON c.id=g.course_id" +
        where +
        " ORDER BY g.created_at DESC,g.id LIMIT 25 OFFSET ?",
      actor.admin(),
      actor.id(),
      "%" + q + "%",
      page * 25
    );
    return new ContentDtos.Page<>(
      rows,
      page,
      25,
      db.queryForObject(
        "SELECT count(*) FROM learning_groups g" + where,
        Long.class,
        actor.admin(),
        actor.id(),
        "%" + q + "%"
      )
    );
  }

  public UUID create(NewGroup req) {
    teacher();
    ContentRecord c = records.get(req.courseId(), true);
    policy.edit(c);
    PlatformException.require(
      c.kind() == ContentKind.COURSE,
      "COURSE_REQUIRED"
    );
    if (
      !actor.admin() && !Objects.equals(c.ownerId(), actor.id())
    ) throw PlatformException.forbidden();
    UUID id = UUID.randomUUID();
    db.update(
      "INSERT INTO learning_groups(id,name,teacher_id,course_id) VALUES (?,?,?,?)",
      id,
      req.name().trim(),
      actor.id(),
      req.courseId()
    );
    audit.record(id, "GROUP", "CREATE", null);
    return id;
  }

  private Map<String, Object> owned(UUID id, boolean lock) {
    teacher();
    var rows = db.queryForList(
      "SELECT * FROM learning_groups WHERE id=? AND (? OR teacher_id=?)" +
        (lock ? " FOR UPDATE" : ""),
      id,
      actor.admin(),
      actor.id()
    );
    if (rows.isEmpty()) throw PlatformException.missing();
    return rows.getFirst();
  }

  public Map<String, Object> detail(UUID id) {
    var group = owned(id, false);
    UUID course = (UUID) group.get("course_id");
    var result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("name", group.get("name"));
    result.put("courseId", course);
    var students = db.queryForList(
      "SELECT u.id,u.email,u.first_name AS \"firstName\",u.last_name AS \"lastName\",e.status AS enrollment,(SELECT count(*) FROM lesson_progress p JOIN lessons l ON l.id=p.lesson_id JOIN course_modules cm ON cm.id=l.module_id JOIN content_records lr ON lr.id=l.id JOIN content_records mr ON mr.id=cm.id WHERE p.user_id=u.id AND cm.course_id=? AND lr.published_payload IS NOT NULL AND mr.published_payload IS NOT NULL AND lr.status<>'ARCHIVED' AND mr.status<>'ARCHIVED') AS completed FROM group_members m JOIN users u ON u.id=m.user_id LEFT JOIN enrollments e ON e.user_id=u.id AND e.course_id=? WHERE m.group_id=? ORDER BY u.last_name,u.id",
      course,
      course,
      id
    );
    long lessons = db.queryForObject(
      "SELECT count(*) FROM lessons l JOIN course_modules cm ON cm.id=l.module_id JOIN content_records lr ON lr.id=l.id JOIN content_records mr ON mr.id=cm.id WHERE cm.course_id=? AND lr.published_payload IS NOT NULL AND mr.published_payload IS NOT NULL AND lr.status<>'ARCHIVED' AND mr.status<>'ARCHIVED'",
      Long.class,
      course
    );
    result.put("students", students);
    result.put("totalLessons", lessons);
    result.put(
      "averageProgress",
      lessons == 0 || students.isEmpty()
        ? 0
        : students
            .stream()
            .mapToDouble(
              s -> (((Number) s.get("completed")).doubleValue() * 100) / lessons
            )
            .average()
            .orElse(0)
    );
    result.put(
      "assignments",
      db.queryForList(
        "SELECT a.id,r.title_ru AS \"titleRu\",r.title_kz AS \"titleKz\",a.due_at AS \"dueAt\",(SELECT count(*) FROM assignment_submissions s JOIN group_members gm ON gm.user_id=s.user_id WHERE s.assignment_id=a.id AND gm.group_id=?) AS submitted FROM assignment_groups ag JOIN assignments a ON a.id=ag.assignment_id JOIN content_records r ON r.id=a.id WHERE ag.group_id=? ORDER BY a.due_at NULLS LAST",
        id,
        id
      )
    );
    result.put(
      "availableAssignments",
      db.queryForList(
        "SELECT a.id,r.title_ru AS \"titleRu\",r.title_kz AS \"titleKz\" FROM assignments a JOIN content_records r ON r.id=a.id WHERE a.course_id=? AND r.status<>'ARCHIVED' AND (? OR r.owner_id=?) ORDER BY r.title_ru,a.id",
        course,
        actor.admin(),
        actor.id()
      )
    );
    result.put(
      "weakTopics",
      db.queryForList(
        "SELECT t.id,t.title_ru AS \"titleRu\",t.title_kz AS \"titleKz\",round(100.0*sum(a.earned_points)/nullif(sum(a.max_points),0),1) AS accuracy FROM completed_question_activity a JOIN group_members m ON m.user_id=a.user_id JOIN topics t ON t.id=a.topic_id WHERE m.group_id=? GROUP BY t.id HAVING 100.0*sum(a.earned_points)/nullif(sum(a.max_points),0)<70 ORDER BY accuracy LIMIT 5",
        id
      )
    );
    return result;
  }

  public void add(UUID id, Member req) {
    var g = owned(id, true);
    UUID user = db
      .query(
        "SELECT id FROM users WHERE lower(email)=lower(?) AND is_active AND role='STUDENT'",
        (r, n) -> r.getObject(1, UUID.class),
        req.email().trim()
      )
      .stream()
      .findFirst()
      .orElseThrow(PlatformException::missing);
    db.update(
      "INSERT INTO group_members(group_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING",
      id,
      user
    );
    courses.enroll((UUID) g.get("course_id"), user, false);
    audit.record(id, "GROUP", "ADD_MEMBER", null);
  }

  public void remove(UUID id, UUID user) {
    var g = owned(id, true);
    UUID course = (UUID) g.get("course_id");
    courses.lockEnrollment(course, user);
    db.update(
      "DELETE FROM group_members WHERE group_id=? AND user_id=?",
      id,
      user
    );
    db.update(
      "UPDATE enrollments e SET status='CANCELLED',updated_at=now() WHERE e.course_id=? AND e.user_id=? AND NOT e.manual_access AND NOT EXISTS(SELECT 1 FROM group_members m JOIN learning_groups g ON g.id=m.group_id WHERE m.user_id=e.user_id AND g.course_id=e.course_id)",
      course,
      user
    );
    audit.record(id, "GROUP", "REMOVE_MEMBER", null);
  }

  public void assign(UUID group, UUID assignment) {
    var g = owned(group, true);
    ContentRecord c = records.get(assignment, false);
    policy.edit(c);
    PlatformException.require(
      c.kind() == ContentKind.ASSIGNMENT &&
        Objects.equals(policy.course(c), g.get("course_id")),
      "ASSIGNMENT_COURSE_MISMATCH"
    );
    db.update(
      "INSERT INTO assignment_groups(assignment_id,group_id) VALUES (?,?) ON CONFLICT DO NOTHING",
      assignment,
      group
    );
    audit.record(assignment, "ASSIGNMENT", "ASSIGN_GROUP", null);
  }

  public List<Map<String, Object>> studentAssignments() {
    return db.queryForList(
      "SELECT DISTINCT a.id,r.published_payload->>'titleRu' AS \"titleRu\",r.published_payload->>'titleKz' AS \"titleKz\",a.due_at AS \"dueAt\",a.max_score AS \"maxScore\",s.submitted_at AS \"submittedAt\",s.score FROM assignments a JOIN content_records r ON r.id=a.id JOIN assignment_groups ag ON ag.assignment_id=a.id JOIN group_members m ON m.group_id=ag.group_id JOIN enrollments e ON e.course_id=a.course_id AND e.user_id=m.user_id JOIN content_records cr ON cr.id=a.course_id LEFT JOIN assignment_submissions s ON s.assignment_id=a.id AND s.user_id=m.user_id WHERE m.user_id=? AND e.status IN ('ACTIVE','COMPLETED') AND r.published_payload IS NOT NULL AND r.status<>'ARCHIVED' AND cr.published_payload IS NOT NULL AND cr.status<>'ARCHIVED' AND (a.lesson_id IS NULL OR EXISTS(SELECT 1 FROM lessons l JOIN course_modules cm ON cm.id=l.module_id JOIN content_records lr ON lr.id=l.id JOIN content_records mr ON mr.id=cm.id WHERE l.id=a.lesson_id AND lr.published_payload IS NOT NULL AND mr.published_payload IS NOT NULL AND lr.status<>'ARCHIVED' AND mr.status<>'ARCHIVED')) ORDER BY \"dueAt\" NULLS LAST LIMIT 100",
      actor.id()
    );
  }

  public Map<String, Object> assignment(UUID id) {
    ContentRecord c = records.get(id, false);
    policy.read(c);
    if (
      c.kind() != ContentKind.ASSIGNMENT || !policy.visible(c)
    ) throw PlatformException.missing();
    var result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("content", PublicLearningPayload.of(c.publishedPayload()));
    result.put("submission", submissionRevisions.current(id, actor.id()));
    return result;
  }

  public Map<String, Object> submit(UUID id, Submission req) {
    return submissionRevisions.submit(
      id,
      req.text(),
      req.fileIds(),
      req.requestKey(),
      req.revision()
    );
  }

  public ContentDtos.Page<Map<String, Object>> submissions(UUID id, int page) {
    teacher();
    policy.edit(records.get(id, false));
    page = Math.max(0, page);
    var rows = db.queryForList(
      "SELECT s.user_id AS \"userId\",u.first_name AS \"firstName\",u.last_name AS \"lastName\",s.text,s.score,s.feedback,s.revision,s.content_revision AS \"contentRevision\",s.submitted_at AS \"submittedAt\" FROM assignment_submissions s JOIN users u ON u.id=s.user_id WHERE s.assignment_id=? AND EXISTS(SELECT 1 FROM assignment_groups ag JOIN group_members gm ON gm.group_id=ag.group_id JOIN learning_groups g ON g.id=gm.group_id WHERE ag.assignment_id=s.assignment_id AND gm.user_id=s.user_id AND (? OR g.teacher_id=?)) ORDER BY s.submitted_at DESC,s.user_id LIMIT 25 OFFSET ?",
      id,
      actor.admin(),
      actor.id(),
      page * 25
    );
    long total = db.queryForObject(
      "SELECT count(*) FROM assignment_submissions s WHERE s.assignment_id=? AND EXISTS(SELECT 1 FROM assignment_groups ag JOIN group_members gm ON gm.group_id=ag.group_id JOIN learning_groups g ON g.id=gm.group_id WHERE ag.assignment_id=s.assignment_id AND gm.user_id=s.user_id AND (? OR g.teacher_id=?))",
      Long.class,
      id,
      actor.admin(),
      actor.id()
    );
    return new ContentDtos.Page<>(rows, page, 25, total);
  }

  public void grade(UUID id, UUID user, Grade req) {
    teacher();
    ContentRecord c = records.get(id, true);
    policy.edit(c);
    if (
      c.kind() != ContentKind.ASSIGNMENT || c.publishedPayload() == null
    ) throw new PlatformException(409, "ASSIGNMENT_NOT_PUBLISHED");
    if (
      !Boolean.TRUE.equals(
        db.queryForObject(
          "SELECT EXISTS(SELECT 1 FROM assignment_submissions s JOIN assignment_groups ag ON ag.assignment_id=s.assignment_id JOIN group_members gm ON gm.group_id=ag.group_id AND gm.user_id=s.user_id JOIN learning_groups g ON g.id=gm.group_id WHERE s.assignment_id=? AND s.user_id=? AND (? OR g.teacher_id=?))",
          Boolean.class,
          id,
          user,
          actor.admin(),
          actor.id()
        )
      )
    ) throw PlatformException.missing();
    PlatformException.require(
      req.score() <= c.publishedPayload().path("maxScore").asInt(100),
      "SCORE_TOO_HIGH"
    );
    int saved = db.update(
      "UPDATE assignment_submissions SET score=?,feedback=?,graded_by=?,graded_at=now(),revision=revision+1 WHERE assignment_id=? AND user_id=? AND revision=?",
      req.score(),
      req.feedback(),
      actor.id(),
      id,
      user,
      req.revision()
    );
    if (saved == 0) throw new PlatformException(409, "REVISION_CONFLICT");
    submissionRevisions.recordGrade(
      id,
      user,
      req.score(),
      req.feedback(),
      c.publishedPayload().path("maxScore").asInt(100)
    );
    audit.record(id, "ASSIGNMENT", "GRADE", null);
  }
}
