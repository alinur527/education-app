package ent.kz.entbackend.platform.courses;

import com.fasterxml.jackson.databind.JsonNode;
import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.content.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CourseService {

  public record LessonView(
    UUID id,
    String titleRu,
    String titleKz,
    boolean completed
  ) {}

  public record ModuleView(
    UUID id,
    String titleRu,
    String titleKz,
    List<LessonView> lessons
  ) {}

  public record Detail(
    UUID id,
    JsonNode content,
    String enrollment,
    int totalLessons,
    int completedLessons,
    List<ModuleView> modules
  ) {}

  private final JdbcTemplate db;
  private final Actor actor;
  private final ContentRepository records;
  private final ContentPolicy policy;
  private final AuditService audit;

  public CourseService(
    JdbcTemplate db,
    Actor actor,
    ContentRepository records,
    ContentPolicy policy,
    AuditService audit
  ) {
    this.db = db;
    this.actor = actor;
    this.records = records;
    this.policy = policy;
    this.audit = audit;
  }

  public ContentDtos.Page<Map<String, Object>> list(String q, int page) {
    page = Math.max(0, page);
    String where =
      " FROM courses c JOIN content_records r ON r.id=c.id LEFT JOIN enrollments e ON e.course_id=c.id AND e.user_id=? WHERE r.published_payload IS NOT NULL AND r.status<>'ARCHIVED' AND (c.visibility='PUBLIC' OR e.status IN ('ACTIVE','COMPLETED')) AND (c.title_ru ILIKE ? OR c.title_kz ILIKE ?)";
    String match = "%" + q + "%";
    var items = db.queryForList(
      "SELECT c.id,c.icon,c.title_ru AS \"titleRu\",c.title_kz AS \"titleKz\",c.description_ru AS \"descriptionRu\",c.description_kz AS \"descriptionKz\",c.visibility,c.self_enroll AS \"selfEnroll\",e.status AS enrollment" +
        where +
        " ORDER BY r.updated_at DESC,c.id LIMIT 25 OFFSET ?",
      actor.id(),
      match,
      match,
      page * 25
    );
    return new ContentDtos.Page<>(
      items,
      page,
      25,
      db.queryForObject(
        "SELECT count(*)" + where,
        Long.class,
        actor.id(),
        match,
        match
      )
    );
  }

  public Detail detail(UUID id) {
    ContentRecord c = records.get(id, false);
    if (c.kind() != ContentKind.COURSE) throw PlatformException.missing();
    policy.read(c);
    if (!policy.visible(c)) throw PlatformException.missing();
    List<UUID> done = db.queryForList(
      "SELECT p.lesson_id FROM lesson_progress p JOIN lessons l ON l.id=p.lesson_id JOIN course_modules m ON m.id=l.module_id WHERE p.user_id=? AND m.course_id=?",
      UUID.class,
      actor.id(),
      id
    );
    var modules = new ArrayList<ModuleView>();
    // One bounded tree query, not one query for each lesson.
    var rows = db.queryForList(
      "SELECT m.id AS module_id,m.title_ru AS module_ru,m.title_kz AS module_kz,l.id AS lesson_id,l.title_ru AS lesson_ru,l.title_kz AS lesson_kz FROM course_modules m JOIN content_records mr ON mr.id=m.id LEFT JOIN (lessons l JOIN content_records lr ON lr.id=l.id AND lr.published_payload IS NOT NULL AND lr.status<>'ARCHIVED') ON l.module_id=m.id WHERE m.course_id=? AND mr.published_payload IS NOT NULL AND mr.status<>'ARCHIVED' ORDER BY m.sort_order,m.id,l.sort_order,l.id",
      id
    );
    var grouped = new LinkedHashMap<UUID, List<LessonView>>();
    var names = new HashMap<UUID, String[]>();
    for (var r : rows) {
      UUID mid = (UUID) r.get("module_id");
      grouped.computeIfAbsent(mid, k -> new ArrayList<>());
      names.put(mid, new String[] {
        (String) r.get("module_ru"),
        (String) r.get("module_kz"),
      });
      if (r.get("lesson_id") != null) {
        UUID lid = (UUID) r.get("lesson_id");
        grouped
          .get(mid)
          .add(
            new LessonView(
              lid,
              (String) r.get("lesson_ru"),
              (String) r.get("lesson_kz"),
              done.contains(lid)
            )
          );
      }
    }
    grouped.forEach((mid, lessons) ->
      modules.add(
        new ModuleView(mid, names.get(mid)[0], names.get(mid)[1], lessons)
      )
    );
    var status = db.queryForList(
      "SELECT status FROM enrollments WHERE course_id=? AND user_id=?",
      String.class,
      id,
      actor.id()
    );
    int total = modules
        .stream()
        .mapToInt(m -> m.lessons().size())
        .sum(),
      completed = (int) modules
        .stream()
        .flatMap(m -> m.lessons().stream())
        .filter(LessonView::completed)
        .count();
    return new Detail(
      id,
      c.publishedPayload(),
      status.isEmpty() ? null : status.getFirst(),
      total,
      completed,
      modules
    );
  }

  public void selfEnroll(UUID id) {
    ContentRecord c = records.get(id, true);
    policy.read(c);
    if (
      c.kind() != ContentKind.COURSE ||
      !policy.visible(c) ||
      !c.publishedPayload().path("selfEnroll").asBoolean() ||
      !c.publishedPayload().path("visibility").asText().equals("PUBLIC")
    ) throw PlatformException.forbidden();
    enroll(id, actor.id(), true);
  }

  public void enroll(UUID course, UUID user, boolean manual) {
    if (
      !Boolean.TRUE.equals(
        db.queryForObject(
          "SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND is_active AND role='STUDENT')",
          Boolean.class,
          user
        )
      )
    ) throw new PlatformException(400, "STUDENT_REQUIRED");
    db.update(
      "INSERT INTO enrollments(course_id,user_id,manual_access) VALUES (?,?,?) ON CONFLICT(course_id,user_id) DO UPDATE SET status=CASE WHEN enrollments.status='COMPLETED' THEN 'COMPLETED' ELSE 'ACTIVE' END,manual_access=enrollments.manual_access OR excluded.manual_access,updated_at=now()",
      course,
      user,
      manual
    );
  }

  public void manageEnrollment(UUID course, String email, String status) {
    ContentRecord c = records.get(course, true);
    policy.edit(c);
    if (
      !actor.admin() && !Objects.equals(c.ownerId(), actor.id())
    ) throw PlatformException.forbidden();
    PlatformException.require(
      c.kind() == ContentKind.COURSE,
      "COURSE_REQUIRED"
    );
    UUID user = db
      .query(
        "SELECT id FROM users WHERE lower(email)=lower(?)",
        (r, n) -> r.getObject(1, UUID.class),
        email.trim()
      )
      .stream()
      .findFirst()
      .orElseThrow(PlatformException::missing);
    PlatformException.require(
      Set.of("ACTIVE", "CANCELLED").contains(status),
      "INVALID_ENROLLMENT"
    );
    if (status.equals("ACTIVE")) enroll(course, user, true);
    else db.update(
      "UPDATE enrollments SET status='CANCELLED',manual_access=false,updated_at=now() WHERE course_id=? AND user_id=?",
      course,
      user
    );
    audit.record(course, "ENROLLMENT", status, null);
  }

  public void lockEnrollment(UUID course, UUID user) {
    db.queryForList(
      "SELECT course_id FROM enrollments WHERE course_id=? AND user_id=? FOR UPDATE",
      course,
      user
    );
  }

  public void completeLesson(UUID id) {
    ContentRecord c = records.get(id, false);
    policy.read(c);
    if (
      c.kind() != ContentKind.LESSON ||
      !policy.visible(c) ||
      !policy.enrolled(policy.course(c))
    ) throw PlatformException.missing();
    lockEnrollment(policy.course(c), actor.id());
    if (!policy.enrolled(policy.course(c))) throw PlatformException.missing();
    db.update(
      "INSERT INTO lesson_progress(user_id,lesson_id) VALUES (?,?) ON CONFLICT DO NOTHING",
      actor.id(),
      id
    );
    Detail progress = detail(policy.course(c));
    if (
      progress.totalLessons() > 0 &&
      progress.totalLessons() == progress.completedLessons()
    ) db.update(
      "UPDATE enrollments SET status='COMPLETED',updated_at=now() WHERE course_id=? AND user_id=? AND status='ACTIVE'",
      policy.course(c),
      actor.id()
    );
  }

  public List<Map<String, Object>> lessonActivities(UUID id) {
    ContentRecord c = records.get(id, false);
    policy.read(c);
    var result = new ArrayList<Map<String, Object>>();
    for (ContentRecord child : records.children(id)) {
      if (!policy.visible(child)) continue;
      try {
        policy.read(child);
      } catch (PlatformException ignored) {
        continue;
      }
      result.add(
        Map.of(
          "id",
          child.id(),
          "kind",
          child.kind().name(),
          "titleRu",
          child.publishedPayload().path("titleRu").asText(),
          "titleKz",
          child.publishedPayload().path("titleKz").asText()
        )
      );
    }
    return result;
  }
}
