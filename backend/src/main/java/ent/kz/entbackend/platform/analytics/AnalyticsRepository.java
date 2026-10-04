package ent.kz.entbackend.platform.analytics;

import static ent.kz.entbackend.platform.analytics.AnalyticsDtos.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsRepository {

  private final JdbcTemplate db;

  public AnalyticsRepository(JdbcTemplate db) {
    this.db = db;
  }

  static String sqlZone(ZoneId zone) {
    if (zone.normalized() instanceof ZoneOffset offset) {
      int seconds = offset.getTotalSeconds();
      return seconds == 0
        ? "UTC"
        : String.format(
            Locale.ROOT,
            "UTC%s%02d:%02d:%02d",
            seconds > 0 ? "-" : "+",
            Math.abs(seconds) / 3600,
            (Math.abs(seconds) % 3600) / 60,
            Math.abs(seconds) % 60
          );
    }
    return zone.getId();
  }

  public String zone(UUID user) {
    var zones = db.queryForList(
      "SELECT time_zone FROM study_profiles WHERE user_id=?",
      String.class,
      user
    );
    return zones.isEmpty() ? "Asia/Almaty" : zones.getFirst();
  }

  public LocalDate firstDate(UUID user, ZoneId zone, Instant now) {
    Timestamp first = db.queryForObject(
      "SELECT min(occurred_at) FROM statistics_activity_events WHERE user_id=? AND kind<>'ERROR_RESOLVED' AND occurred_at<=?",
      Timestamp.class,
      user,
      Timestamp.from(now)
    );
    return (first == null ? now : first.toInstant()).atZone(zone).toLocalDate();
  }

  public Set<LocalDate> activeDates(UUID user, ZoneId zone, Instant now) {
    return new HashSet<>(
      db.query(
        "SELECT DISTINCT (occurred_at AT TIME ZONE ?)::date d FROM statistics_activity_events WHERE user_id=? AND kind<>'ERROR_RESOLVED' AND occurred_at<=?",
        (r, i) -> r.getDate("d").toLocalDate(),
        sqlZone(zone),
        user,
        Timestamp.from(now)
      )
    );
  }

  public boolean hasPractice(UUID user, Instant now) {
    return Boolean.TRUE.equals(
      db.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM completed_question_activity WHERE user_id=? AND (completed_at AT TIME ZONE 'UTC')<=?)",
        Boolean.class,
        user,
        Timestamp.from(now)
      )
    );
  }

  public List<Day> days(
    UUID user,
    ZoneId zone,
    LocalDate from,
    LocalDate to,
    Instant now
  ) {
    Timestamp start = Timestamp.from(from.atStartOfDay(zone).toInstant());
    Timestamp end = Timestamp.from(
      to.plusDays(1).atStartOfDay(zone).toInstant()
    );
    var rows = db.queryForList(
      """
      WITH q AS (
        SELECT ((completed_at AT TIME ZONE 'UTC') AT TIME ZONE ?)::date d,count(*) questions,
          count(*) FILTER(WHERE correct) correct,sum(earned_points) earned,sum(max_points) maximum
        FROM completed_question_activity WHERE user_id=? AND (completed_at AT TIME ZONE 'UTC')>=? AND (completed_at AT TIME ZONE 'UTC')<? AND (completed_at AT TIME ZONE 'UTC')<=? GROUP BY d
      ), e AS (
        SELECT (occurred_at AT TIME ZONE ?)::date d,
          count(*) FILTER(WHERE kind='TEST') tests,count(*) FILTER(WHERE kind='THEORY') theories,
          count(*) FILTER(WHERE kind='LESSON') lessons,count(*) FILTER(WHERE kind='PLANNER_TASK') tasks,
          count(*) FILTER(WHERE kind='ASSIGNMENT') assignments,count(*) FILTER(WHERE kind='ERROR_RESOLVED') errors,
          count(*) FILTER(WHERE kind<>'ERROR_RESOLVED') actions
        FROM statistics_activity_events WHERE user_id=? AND occurred_at>=? AND occurred_at<? AND occurred_at<=? GROUP BY d
      ), s AS (
        SELECT ((completed_at AT TIME ZONE 'UTC') AT TIME ZONE ?)::date d,sum(coalesce(time_taken_secs,0)) seconds
        FROM test_sessions WHERE user_id=? AND status='COMPLETED' AND (completed_at AT TIME ZONE 'UTC')>=? AND (completed_at AT TIME ZONE 'UTC')<? AND (completed_at AT TIME ZONE 'UTC')<=? GROUP BY d
      ) SELECT d,coalesce(questions,0) questions,coalesce(correct,0) correct,coalesce(earned,0) earned,
        coalesce(maximum,0) maximum,coalesce(tests,0) tests,coalesce(theories,0) theories,
        coalesce(lessons,0) lessons,coalesce(tasks,0) tasks,coalesce(assignments,0) assignments,
        coalesce(errors,0) errors,coalesce(actions,0) actions,coalesce(seconds,0) seconds
      FROM q FULL JOIN e USING(d) FULL JOIN s USING(d) ORDER BY d
      """,
      sqlZone(zone),
      user,
      start,
      end,
      Timestamp.from(now),
      sqlZone(zone),
      user,
      start,
      end,
      Timestamp.from(now),
      sqlZone(zone),
      user,
      start,
      end,
      Timestamp.from(now)
    );
    Map<LocalDate, Day> known = new HashMap<>();
    for (var r : rows) {
      LocalDate date = ((java.sql.Date) r.get("d")).toLocalDate();
      known.put(
        date,
        new Day(
          date,
          n(r, "questions"),
          n(r, "correct"),
          percent(n(r, "correct"), n(r, "questions")),
          n(r, "earned"),
          n(r, "maximum"),
          n(r, "tests"),
          n(r, "seconds"),
          n(r, "theories"),
          n(r, "lessons"),
          n(r, "tasks"),
          n(r, "assignments"),
          n(r, "errors"),
          n(r, "actions")
        )
      );
    }
    return from
      .datesUntil(to.plusDays(1))
      .map(d ->
        known.getOrDefault(
          d,
          new Day(d, 0, 0, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        )
      )
      .toList();
  }

  public List<Subject> subjects(
    UUID user,
    Instant start,
    Instant end,
    Instant previousStart
  ) {
    return db.query(
      """
      SELECT s.id,s.name_ru,s.name_kz,
        count(*) FILTER(WHERE (a.completed_at AT TIME ZONE 'UTC')>=?) questions,
        count(*) FILTER(WHERE (a.completed_at AT TIME ZONE 'UTC')>=? AND a.correct) correct,
        coalesce(sum(a.earned_points) FILTER(WHERE (a.completed_at AT TIME ZONE 'UTC')>=?),0) earned,
        coalesce(sum(a.max_points) FILTER(WHERE (a.completed_at AT TIME ZONE 'UTC')>=?),0) maximum,
        count(DISTINCT a.session_id) FILTER(WHERE (a.completed_at AT TIME ZONE 'UTC')>=?) attempts,
        count(*) FILTER(WHERE (a.completed_at AT TIME ZONE 'UTC')<?) previous_questions,
        count(*) FILTER(WHERE (a.completed_at AT TIME ZONE 'UTC')<? AND a.correct) previous_correct
      FROM completed_question_activity a JOIN subjects s ON s.id=a.subject_id
      WHERE a.user_id=? AND (a.completed_at AT TIME ZONE 'UTC')>=? AND (a.completed_at AT TIME ZONE 'UTC')<=?
      GROUP BY s.id HAVING count(*) FILTER(WHERE (a.completed_at AT TIME ZONE 'UTC')>=?)>0 ORDER BY questions DESC,s.id
      """,
      (r, i) -> {
        Double accuracy = percent(r.getLong("correct"), r.getLong("questions"));
        Double prior = percent(
          r.getLong("previous_correct"),
          r.getLong("previous_questions")
        );
        return new Subject(
          r.getObject("id", UUID.class),
          r.getString("name_ru"),
          r.getString("name_kz"),
          r.getLong("questions"),
          accuracy,
          percent(r.getLong("earned"), r.getLong("maximum")),
          r.getLong("attempts"),
          prior == null || previousStart == null
            ? null
            : round(accuracy - prior)
        );
      },
      Timestamp.from(start),
      Timestamp.from(start),
      Timestamp.from(start),
      Timestamp.from(start),
      Timestamp.from(start),
      Timestamp.from(start),
      Timestamp.from(start),
      user,
      Timestamp.from(previousStart == null ? start : previousStart),
      Timestamp.from(end),
      Timestamp.from(start)
    );
  }

  public Set<UUID> sufficientTopics(UUID user, Instant now) {
    return new HashSet<>(
      db.queryForList(
        "SELECT topic_id FROM completed_question_activity WHERE user_id=? AND (completed_at AT TIME ZONE 'UTC')<=? GROUP BY topic_id HAVING count(DISTINCT session_id)>=3 AND count(DISTINCT question_id)>=10",
        UUID.class,
        user,
        Timestamp.from(now)
      )
    );
  }

  public History history(
    UUID user,
    Instant from,
    Instant until,
    String kind,
    int page,
    int size
  ) {
    String predicate =
      "user_id=? AND occurred_at>=? AND occurred_at<=?" +
      (kind.equals("ALL") ? "" : " AND kind=?");
    List<Object> args = new ArrayList<>(
      List.of(user, Timestamp.from(from), Timestamp.from(until))
    );
    if (!kind.equals("ALL")) args.add(kind);
    long total = Objects.requireNonNull(
      db.queryForObject(
        "SELECT count(*) FROM statistics_activity_events WHERE " + predicate,
        Long.class,
        args.toArray()
      )
    );
    args.add(size);
    args.add((long) page * size);
    var events = db.query(
      "SELECT * FROM statistics_activity_events WHERE " +
        predicate +
        " ORDER BY occurred_at DESC,kind,event_id DESC LIMIT ? OFFSET ?",
      (r, i) ->
        new Event(
          r.getString("event_id"),
          r.getString("kind"),
          r.getTimestamp("occurred_at").toInstant(),
          r.getString("title_ru"),
          r.getString("title_kz"),
          r.getString("href")
        ),
      args.toArray()
    );
    return new History(events, total, page, size);
  }

  static long n(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  static Double percent(long numerator, long denominator) {
    return denominator == 0 ? null : round((100.0 * numerator) / denominator);
  }

  static double round(double value) {
    return Math.round(value * 100) / 100.0;
  }
}
