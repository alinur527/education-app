package ent.kz.entbackend.platform.analytics;

import ent.kz.entbackend.entity.UserRole;
import ent.kz.entbackend.platform.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StaffAnalyticsService {

  private final JdbcTemplate db;
  private final Actor actor;
  private final Clock clock;
  private final AnalyticsRepository repo;

  public StaffAnalyticsService(
    JdbcTemplate db,
    Actor actor,
    @Qualifier("analyticsClock") Clock clock,
    AnalyticsRepository repo
  ) {
    this.db = db;
    this.actor = actor;
    this.clock = clock;
    this.repo = repo;
  }

  public record Subject(
    String titleRu,
    String titleKz,
    long questionsAnswered,
    long testsCompleted,
    Double accuracyPercent
  ) {}

  public record Coverage(
    String kind,
    long published,
    long draft,
    long review,
    long archived
  ) {}

  public record Overview(
    String period,
    String timeZone,
    LocalDate from,
    LocalDate to,
    boolean global,
    long studentsInScope,
    long activeStudents,
    long testsCompleted,
    long questionsAnswered,
    Double accuracyPercent,
    long courseEnrollments,
    long assignmentRecipients,
    long assignmentSubmitters,
    Double assignmentSubmissionRate,
    List<Subject> subjects,
    List<Coverage> coverage
  ) {}

  public Overview overview(String period) {
    PlatformException.require(
      Set.of("7d", "30d").contains(period),
      "INVALID_PERIOD"
    );
    if (
      !actor.admin() && actor.user().getRole() != UserRole.TEACHER
    ) throw PlatformException.forbidden();
    UUID id = actor.id();
    boolean admin = actor.admin();
    ZoneId zone = ZoneId.of(repo.zone(id));
    Instant now = clock.instant();
    LocalDate today = now.atZone(zone).toLocalDate(),
      from = today.minusDays(period.equals("7d") ? 6 : 29);
    Timestamp start = Timestamp.from(from.atStartOfDay(zone).toInstant()),
      end = Timestamp.from(now);
    String scope = """
    WITH roster AS (
      SELECT u.id FROM users u WHERE u.is_active AND u.role='STUDENT' AND (? OR EXISTS(
        SELECT 1 FROM group_members m JOIN learning_groups g ON g.id=m.group_id WHERE m.user_id=u.id AND g.teacher_id=?
      ) OR EXISTS(SELECT 1 FROM enrollments e JOIN courses c ON c.id=e.course_id WHERE e.user_id=u.id AND e.status IN ('ACTIVE','COMPLETED') AND c.teacher_id=?))
    )
    """;
    var activity = db.queryForMap(
      scope +
        """
          SELECT (SELECT count(*) FROM roster) students,
           count(DISTINCT e.user_id) active,count(*) FILTER(WHERE e.kind='TEST') tests
          FROM statistics_activity_events e JOIN roster r ON r.id=e.user_id
        WHERE e.kind<>'ERROR_RESOLVED' AND e.occurred_at>=? AND e.occurred_at<=?
        AND (? OR e.kind IN ('TEST','THEORY') OR EXISTS(
          SELECT 1 FROM lessons l JOIN course_modules m ON m.id=l.module_id JOIN courses c ON c.id=m.course_id
          WHERE e.kind='LESSON' AND l.id::text=e.event_id AND c.teacher_id=?
        ) OR EXISTS(
          SELECT 1 FROM submission_revisions s JOIN assignments a ON a.id=s.assignment_id JOIN courses c ON c.id=a.course_id
          WHERE e.kind='ASSIGNMENT' AND s.id::text=e.event_id AND a.teacher_id=? AND c.teacher_id=?
        ))
          """,
      admin,
      id,
      id,
      start,
      end,
      admin,
      id,
      id,
      id
    );
    var questions = db.queryForMap(
      scope +
        """
        SELECT count(*) questions,count(*) FILTER(WHERE a.correct) correct
        FROM completed_question_activity a JOIN roster r ON r.id=a.user_id
        WHERE (a.completed_at AT TIME ZONE 'UTC')>=? AND (a.completed_at AT TIME ZONE 'UTC')<=?
        """,
      admin,
      id,
      id,
      start,
      end
    );
    long enrollments = Objects.requireNonNull(
      db.queryForObject(
        scope +
          """
          SELECT count(*) FROM enrollments e JOIN roster r ON r.id=e.user_id JOIN courses c ON c.id=e.course_id
          WHERE (? OR c.teacher_id=?) AND e.status IN ('ACTIVE','COMPLETED') AND e.created_at>=? AND e.created_at<=?
          """,
        Long.class,
        admin,
        id,
        id,
        admin,
        id,
        start,
        end
      )
    );
    var assignments = db.queryForMap(
      scope +
        """
        , recipients AS (
          SELECT DISTINCT a.id assignment_id,m.user_id FROM assignments a JOIN content_records c ON c.id=a.id
          JOIN assignment_groups ag ON ag.assignment_id=a.id JOIN learning_groups g ON g.id=ag.group_id
        JOIN group_members m ON m.group_id=g.id JOIN roster r ON r.id=m.user_id
        JOIN enrollments en ON en.course_id=a.course_id AND en.user_id=m.user_id AND en.status IN ('ACTIVE','COMPLETED')
        JOIN content_records course ON course.id=a.course_id AND course.published_payload IS NOT NULL AND course.status<>'ARCHIVED'
        WHERE c.published_payload IS NOT NULL AND c.status<>'ARCHIVED' AND c.created_at<=?
          AND (? OR (a.teacher_id=? AND g.teacher_id=?))
          AND (a.lesson_id IS NULL OR EXISTS (
            SELECT 1 FROM lessons l JOIN course_modules mo ON mo.id=l.module_id
            JOIN content_records lr ON lr.id=l.id JOIN content_records mr ON mr.id=mo.id
            WHERE l.id=a.lesson_id AND lr.published_payload IS NOT NULL AND lr.status<>'ARCHIVED'
              AND mr.published_payload IS NOT NULL AND mr.status<>'ARCHIVED'
          ))
        ) SELECT count(*) recipients,count(*) FILTER(WHERE EXISTS(
          SELECT 1 FROM submission_revisions s WHERE s.assignment_id=r.assignment_id AND s.user_id=r.user_id AND s.submitted_at>=? AND s.submitted_at<=?
        )) submitters FROM recipients r
        """,
      admin,
      id,
      id,
      end,
      admin,
      id,
      id,
      start,
      end
    );
    var subjects = db.query(
      scope +
        """
        SELECT s.name_ru,s.name_kz,count(*) questions,count(DISTINCT a.session_id) tests,count(*) FILTER(WHERE a.correct) correct
        FROM completed_question_activity a JOIN roster r ON r.id=a.user_id JOIN subjects s ON s.id=a.subject_id
        WHERE (a.completed_at AT TIME ZONE 'UTC')>=? AND (a.completed_at AT TIME ZONE 'UTC')<=?
        GROUP BY s.id ORDER BY questions DESC,s.id LIMIT 8
        """,
      (r, i) ->
        new Subject(
          r.getString("name_ru"),
          r.getString("name_kz"),
          r.getLong("questions"),
          r.getLong("tests"),
          AnalyticsRepository.percent(
            r.getLong("correct"),
            r.getLong("questions")
          )
        ),
      admin,
      id,
      id,
      start,
      end
    );
    var coverage = db.query(
      """
      SELECT kind,count(*) FILTER(WHERE published_payload IS NOT NULL AND status<>'ARCHIVED') published,
        count(*) FILTER(WHERE status='DRAFT') draft,count(*) FILTER(WHERE status='REVIEW') review,count(*) FILTER(WHERE status='ARCHIVED') archived
      FROM content_records WHERE (? OR owner_id=?) GROUP BY kind ORDER BY kind
      """,
      (r, i) ->
        new Coverage(
          r.getString("kind"),
          r.getLong("published"),
          r.getLong("draft"),
          r.getLong("review"),
          r.getLong("archived")
        ),
      admin,
      id
    );
    return new Overview(
      period,
      zone.getId(),
      from,
      today,
      admin,
      AnalyticsRepository.n(activity, "students"),
      AnalyticsRepository.n(activity, "active"),
      AnalyticsRepository.n(activity, "tests"),
      AnalyticsRepository.n(questions, "questions"),
      AnalyticsRepository.percent(
        AnalyticsRepository.n(questions, "correct"),
        AnalyticsRepository.n(questions, "questions")
      ),
      enrollments,
      AnalyticsRepository.n(assignments, "recipients"),
      AnalyticsRepository.n(assignments, "submitters"),
      AnalyticsRepository.percent(
        AnalyticsRepository.n(assignments, "submitters"),
        AnalyticsRepository.n(assignments, "recipients")
      ),
      subjects,
      coverage
    );
  }
}
