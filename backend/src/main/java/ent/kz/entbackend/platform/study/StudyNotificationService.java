package ent.kz.entbackend.platform.study;

import static ent.kz.entbackend.platform.PlatformException.require;

import ent.kz.entbackend.platform.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StudyNotificationService {

  private final StudyRepository repo;
  private final StudyAccess access;
  private final Actor actor;

  public StudyNotificationService(
    StudyRepository repo,
    StudyAccess access,
    Actor actor
  ) {
    this.repo = repo;
    this.access = access;
    this.actor = actor;
  }

  public StudyDtos.Preferences preferences() {
    return preferences(actor.id());
  }

  StudyDtos.Preferences preferences(UUID user) {
    var rows = repo
      .jdbc()
      .queryForList(
        "SELECT * FROM study_notification_preferences WHERE user_id=?",
        user
      );
    if (rows.isEmpty()) return new StudyDtos.Preferences(
      true,
      true,
      true,
      true,
      true,
      true,
      LocalTime.of(22, 0),
      LocalTime.of(8, 0),
      0
    );
    var r = rows.getFirst();
    return new StudyDtos.Preferences(
      (Boolean) r.get("enabled"),
      (Boolean) r.get("plans"),
      (Boolean) r.get("deadlines"),
      (Boolean) r.get("grades"),
      (Boolean) r.get("materials"),
      (Boolean) r.get("quiet_enabled"),
      ((java.sql.Time) r.get("quiet_start")).toLocalTime(),
      ((java.sql.Time) r.get("quiet_end")).toLocalTime(),
      StudyRepository.revision(r)
    );
  }

  public StudyDtos.Preferences savePreferences(StudyDtos.Preferences p) {
    UUID user = actor.id();
    repo.lock(user);
    if (
      preferences(user).revision() != p.revision()
    ) throw new PlatformException(409, "REVISION_CONFLICT");
    repo.jdbc().update(
      """
      INSERT INTO study_notification_preferences(user_id,enabled,plans,deadlines,grades,materials,quiet_enabled,quiet_start,quiet_end)
      VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET enabled=excluded.enabled,plans=excluded.plans,deadlines=excluded.deadlines,
      grades=excluded.grades,materials=excluded.materials,quiet_enabled=excluded.quiet_enabled,quiet_start=excluded.quiet_start,quiet_end=excluded.quiet_end,
      revision=study_notification_preferences.revision+1,updated_at=now()
      """,
      user,
      p.enabled(),
      p.plans(),
      p.deadlines(),
      p.grades(),
      p.materials(),
      p.quietEnabled(),
      p.quietStart(),
      p.quietEnd()
    );
    return preferences(user);
  }

  public StudyDtos.Notifications list(int page, boolean unread) {
    require(page >= 0 && page <= 10000, "INVALID_PAGE");
    UUID user = actor.id();
    var items = repo
      .jdbc()
      .queryForList(
        "SELECT * FROM study_notifications WHERE user_id=? AND (NOT ? OR NOT is_read) ORDER BY created_at DESC,id LIMIT 25 OFFSET ?",
        user,
        unread,
        page * 25
      )
      .stream()
      .map(r ->
        new StudyDtos.Notification(
          (UUID) r.get("id"),
          (String) r.get("kind"),
          (String) r.get("title_ru"),
          (String) r.get("title_kz"),
          (Boolean) r.get("is_read"),
          StudyRepository.date(r.get("created_at")),
          access.available(
            user,
            (String) r.get("target_kind"),
            (UUID) r.get("target_id")
          )
        )
      )
      .toList();
    return new StudyDtos.Notifications(
      items,
      page,
      25,
      repo
        .jdbc()
        .queryForObject(
          "SELECT count(*) FROM study_notifications WHERE user_id=? AND (NOT ? OR NOT is_read)",
          Long.class,
          user,
          unread
        ),
      repo
        .jdbc()
        .queryForObject(
          "SELECT count(*) FROM study_notifications WHERE user_id=? AND NOT is_read",
          Long.class,
          user
        )
    );
  }

  public void read(UUID id, boolean read) {
    repo.owned("study_notifications", id, actor.id(), false);
    repo
      .jdbc()
      .update(
        "UPDATE study_notifications SET is_read=? WHERE id=? AND user_id=?",
        read,
        id,
        actor.id()
      );
  }

  public StudyDtos.Open open(UUID id) {
    var r = repo.owned("study_notifications", id, actor.id(), true);
    if (
      !access.available(
        actor.id(),
        (String) r.get("target_kind"),
        (UUID) r.get("target_id")
      )
    ) throw PlatformException.missing();
    read(id, true);
    return new StudyDtos.Open(
      access.url((String) r.get("target_kind"), (UUID) r.get("target_id"))
    );
  }

  public static boolean quiet(StudyDtos.Preferences p, LocalTime now) {
    if (!p.quietEnabled() || p.quietStart().equals(p.quietEnd())) return false;
    return p.quietStart().isBefore(p.quietEnd())
      ? !now.isBefore(p.quietStart()) && now.isBefore(p.quietEnd())
      : !now.isBefore(p.quietStart()) || now.isBefore(p.quietEnd());
  }

  /** Safe for retries and multiple workers: the user/event key is unique in PostgreSQL. */
  public void process(UUID user, Instant now) {
    if (
      !Boolean.TRUE.equals(
        repo
          .jdbc()
          .queryForObject(
            "SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND is_active)",
            Boolean.class,
            user
          )
      )
    ) return;
    var pref = preferences(user);
    if (
      !pref.enabled() ||
      quiet(
        pref,
        now.atZone(ZoneId.of(repo.profile(user).timeZone())).toLocalTime()
      )
    ) return;
    OffsetDateTime at = now.atOffset(ZoneOffset.UTC);
    if (pref.plans()) for (var r : repo
      .jdbc()
      .queryForList(
        "SELECT id,title_ru,title_kz,scheduled_at FROM study_tasks WHERE user_id=? AND status='PLANNED' AND scheduled_at BETWEEN ? AND ? ORDER BY scheduled_at,id",
        user,
        at.minusDays(1),
        at.plusDays(1)
      ))
      emit(
        user,
        "PLAN:" + r.get("id") + ":" + r.get("scheduled_at"),
        "PLAN",
        "TASK",
        (UUID) r.get("id"),
        (String) r.get("title_ru"),
        (String) r.get("title_kz")
      );
    if (pref.deadlines()) for (var r : repo.jdbc().queryForList(
      """
      SELECT DISTINCT a.id,a.due_at,r.published_payload->>'titleRu' AS ru,r.published_payload->>'titleKz' AS kz
      FROM assignments a JOIN content_records r ON r.id=a.id JOIN assignment_groups ag ON ag.assignment_id=a.id
      JOIN group_members gm ON gm.group_id=ag.group_id WHERE gm.user_id=? AND a.due_at BETWEEN ? AND ?
      AND NOT EXISTS(SELECT 1 FROM assignment_submissions s WHERE s.assignment_id=a.id AND s.user_id=gm.user_id)
      ORDER BY a.due_at,a.id
      """,
      user,
      at.minusDays(1),
      at.plusDays(1)
    ))
      emit(
        user,
        "DEADLINE:" + r.get("id") + ":" + r.get("due_at"),
        "DEADLINE",
        "ASSIGNMENT",
        (UUID) r.get("id"),
        (String) r.get("ru"),
        (String) r.get("kz")
      );
    if (pref.grades()) for (var r : repo.jdbc().queryForList(
      """
      SELECT s.assignment_id AS id,s.revision,r.published_payload->>'titleRu' AS ru,r.published_payload->>'titleKz' AS kz
      FROM assignment_submissions s JOIN content_records r ON r.id=s.assignment_id
      WHERE s.user_id=? AND s.graded_at>=? AND s.score IS NOT NULL ORDER BY s.graded_at DESC,s.assignment_id
      """,
      user,
      at.minusDays(7)
    ))
      emit(
        user,
        "GRADE:" + r.get("id") + ":" + r.get("revision"),
        "GRADE",
        "ASSIGNMENT",
        (UUID) r.get("id"),
        (String) r.get("ru"),
        (String) r.get("kz")
      );
    if (pref.materials()) for (var r : repo.jdbc().queryForList(
      """
      SELECT DISTINCT l.id,r.published_version,r.published_payload->>'titleRu' AS ru,r.published_payload->>'titleKz' AS kz
      FROM lessons l JOIN course_modules m ON m.id=l.module_id JOIN content_records r ON r.id=l.id
      JOIN learning_groups g ON g.course_id=m.course_id JOIN group_members gm ON gm.group_id=g.id
      WHERE gm.user_id=? AND (r.updated_at>=? OR gm.created_at>=?) AND r.published_payload IS NOT NULL
      ORDER BY l.id
      """,
      user,
      at.minusDays(7),
      at.minusDays(7)
    ))
      emit(
        user,
        "MATERIAL:" + r.get("id") + ":" + r.get("published_version"),
        "MATERIAL",
        "LESSON",
        (UUID) r.get("id"),
        (String) r.get("ru"),
        (String) r.get("kz")
      );
  }

  private void emit(
    UUID user,
    String key,
    String kind,
    String targetKind,
    UUID target,
    String ru,
    String kz
  ) {
    if (!access.available(user, targetKind, target)) return;
    repo
      .jdbc()
      .update(
        "INSERT INTO study_notifications(id,user_id,event_key,kind,target_kind,target_id,title_ru,title_kz) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(user_id,event_key) DO NOTHING",
        UUID.randomUUID(),
        user,
        key,
        kind,
        targetKind,
        target,
        Objects.toString(ru, ""),
        Objects.toString(kz, "")
      );
  }
}
