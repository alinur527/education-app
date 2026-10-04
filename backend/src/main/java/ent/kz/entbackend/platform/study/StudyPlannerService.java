package ent.kz.entbackend.platform.study;

import static ent.kz.entbackend.platform.PlatformException.require;

import ent.kz.entbackend.platform.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StudyPlannerService {

  private final StudyRepository repo;
  private final Actor actor;
  private final StudyAccess access;
  private final StudyCandidates candidates;

  public StudyPlannerService(
    StudyRepository repo,
    Actor actor,
    StudyAccess access,
    StudyCandidates candidates
  ) {
    this.repo = repo;
    this.actor = actor;
    this.access = access;
    this.candidates = candidates;
  }

  public StudyDtos.Profile profile() {
    return repo.profile(actor.id());
  }

  public StudyDtos.Profile saveProfile(StudyDtos.Profile p) {
    UUID user = actor.id();
    repo.lock(user);
    var old = repo.profile(user);
    if (old.revision() != p.revision()) throw new PlatformException(
      409,
      "REVISION_CONFLICT"
    );
    ZoneId zone;
    try {
      zone = ZoneId.of(p.timeZone());
    } catch (DateTimeException e) {
      throw new PlatformException(400, "INVALID_TIME_ZONE");
    }
    LocalDate today = LocalDate.now(zone);
    require(
      !p.targetDate().isBefore(today) &&
        !p.targetDate().isAfter(today.plusDays(365)),
      "INVALID_TARGET_DATE"
    );
    require(
      new HashSet<>(p.availableDays()).size() == p.availableDays().size() &&
        new HashSet<>(p.selectedSubjects()).size() ==
          p.selectedSubjects().size(),
      "DUPLICATE_SELECTION"
    );
    for (UUID id : p.selectedSubjects())
      require(
        Boolean.TRUE.equals(
          repo
            .jdbc()
            .queryForObject(
              "SELECT EXISTS(SELECT 1 FROM subjects s JOIN content_records r ON r.id=s.id WHERE s.id=? AND s.is_active AND r.published_payload IS NOT NULL AND r.status<>'ARCHIVED')",
              Boolean.class,
              id
            )
        ),
        "SUBJECT_UNAVAILABLE"
      );
    repo.jdbc().update(
      """
      INSERT INTO study_profiles(user_id,goal,target_date,available_days,minutes_per_day,time_zone,selected_subjects)
      VALUES (?,?,?,?::jsonb,?,?,?::jsonb) ON CONFLICT(user_id) DO UPDATE SET goal=excluded.goal,target_date=excluded.target_date,
      available_days=excluded.available_days,minutes_per_day=excluded.minutes_per_day,time_zone=excluded.time_zone,
      selected_subjects=excluded.selected_subjects,revision=study_profiles.revision+1,updated_at=now()
      """,
      user,
      p.goal().trim(),
      p.targetDate(),
      repo.encode(p.availableDays()),
      p.minutesPerDay(),
      zone.getId(),
      repo.encode(p.selectedSubjects())
    );
    return repo.profile(user);
  }

  public StudyDtos.PlanResult recompute() {
    UUID user = actor.id();
    repo.lock(user);
    var p = repo.profile(user);
    require(p.revision() > 0, "PROFILE_REQUIRED");
    ZoneId zone = ZoneId.of(p.timeZone());
    LocalDate today = LocalDate.now(zone);
    require(!p.targetDate().isBefore(today), "TARGET_DATE_PASSED");
    var retained = repo
      .jdbc()
      .queryForList(
        "SELECT * FROM study_tasks WHERE user_id=? AND (status<>'PLANNED' OR pinned OR manually_moved)",
        user
      );
    Set<String> protectedKeys = new HashSet<>();
    Map<LocalDate, Integer> occupied = new HashMap<>();
    Map<LocalDate, List<Interval>> intervals = new HashMap<>();
    for (var r : retained) {
      protectedKeys.add((String) r.get("source_key"));
      var date = StudyRepository.date(r.get("scheduled_at"));
      if (date != null && !r.get("status").equals("SKIPPED")) {
        int duration = ((Number) r.get("duration_minutes")).intValue();
        LocalDate day = date.atZoneSameInstant(zone).toLocalDate();
        occupied.merge(day, duration, Integer::sum);
        intervals
          .computeIfAbsent(day, d -> new ArrayList<>())
          .add(new Interval(date, date.plusMinutes(duration)));
      }
    }
    LocalDate firstDay = LocalTime.now(zone).isBefore(LocalTime.of(18, 0))
      ? today
      : today.plusDays(1);
    List<LocalDate> days = firstDay
      .datesUntil(p.targetDate().plusDays(1))
      .filter(d -> p.availableDays().contains(d.getDayOfWeek().getValue()))
      .toList();
    int capacity = days.size() * p.minutesPerDay(),
      needed = occupied
        .entrySet()
        .stream()
        .filter(
          e ->
            !e.getKey().isBefore(today) && !e.getKey().isAfter(p.targetDate())
        )
        .mapToInt(Map.Entry::getValue)
        .sum();
    int planned = 0,
      unscheduled = (int) retained
        .stream()
        .filter(
          r ->
            r.get("status").equals("PLANNED") && r.get("scheduled_at") == null
        )
        .count();
    needed += retained
      .stream()
      .filter(
        r -> r.get("status").equals("PLANNED") && r.get("scheduled_at") == null
      )
      .mapToInt(r -> ((Number) r.get("duration_minutes")).intValue())
      .sum();
    boolean warning = occupied
      .entrySet()
      .stream()
      .anyMatch(
        e ->
          !e.getKey().isBefore(today) &&
          !e.getKey().isAfter(p.targetDate()) &&
          (!days.contains(e.getKey()) || e.getValue() > p.minutesPerDay())
      );
    UUID generation = UUID.randomUUID();
    for (var c : candidates.forUser(user, p)) {
      if (protectedKeys.contains(c.key())) continue;
      needed += c.minutes();
      OffsetDateTime at = null;
      for (LocalDate day : days) {
        int used = occupied.getOrDefault(day, 0);
        if (used + c.minutes() > p.minutesPerDay()) continue;
        var candidate = day.atTime(18, 0).atZone(zone).toOffsetDateTime();
        for (var interval : intervals
          .getOrDefault(day, List.of())
          .stream()
          .sorted(Comparator.comparing(Interval::from))
          .toList()) {
          if (
            candidate.isBefore(interval.to()) &&
            candidate.plusMinutes(c.minutes()).isAfter(interval.from())
          ) candidate = interval.to();
        }
        if (
          !candidate
            .plusMinutes(c.minutes())
            .atZoneSameInstant(zone)
            .toLocalDate()
            .equals(day)
        ) continue;
        if (
          c.due() != null &&
          candidate
            .plusMinutes(c.minutes())
            .toInstant()
            .isAfter(c.due().toInstant())
        ) continue;
        at = candidate;
        occupied.put(day, used + c.minutes());
        intervals
          .computeIfAbsent(day, d -> new ArrayList<>())
          .add(new Interval(at, at.plusMinutes(c.minutes())));
        break;
      }
      if (at == null) unscheduled++;
      else planned++;
      repo.jdbc().update(
        """
        INSERT INTO study_tasks(id,user_id,source_key,kind,target_kind,target_id,title_ru,title_kz,reason,scheduled_at,duration_minutes,generation)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id,source_key) DO UPDATE SET scheduled_at=excluded.scheduled_at,
        title_ru=excluded.title_ru,title_kz=excluded.title_kz,reason=excluded.reason,generation=excluded.generation,
        revision=CASE WHEN study_tasks.scheduled_at IS DISTINCT FROM excluded.scheduled_at THEN study_tasks.revision+1 ELSE study_tasks.revision END,updated_at=now()
        WHERE study_tasks.status='PLANNED' AND NOT study_tasks.pinned AND NOT study_tasks.manually_moved
        """,
        UUID.randomUUID(),
        user,
        c.key(),
        c.kind(),
        c.targetKind(),
        c.targetId(),
        c.ru(),
        c.kz(),
        c.reason(),
        at,
        c.minutes(),
        generation
      );
    }
    repo
      .jdbc()
      .update(
        "DELETE FROM study_tasks WHERE user_id=? AND status='PLANNED' AND NOT pinned AND NOT manually_moved AND generation IS DISTINCT FROM ?",
        user,
        generation
      );
    return new StudyDtos.PlanResult(
      planned,
      retained.size(),
      unscheduled,
      needed,
      capacity,
      warning || unscheduled > 0 || needed > capacity
    );
  }

  private record Interval(OffsetDateTime from, OffsetDateTime to) {}

  public StudyDtos.Calendar calendar(
    LocalDate from,
    LocalDate to,
    int page,
    boolean unscheduled
  ) {
    require(
      !to.isBefore(from) && ChronoUnit.DAYS.between(from, to) <= 93,
      "INVALID_DATE_WINDOW"
    );
    require(page >= 0 && page <= 10000, "INVALID_PAGE");
    UUID user = actor.id();
    ZoneId zone = ZoneId.of(repo.profile(user).timeZone());
    String filter = unscheduled
      ? "scheduled_at IS NULL AND status='PLANNED'"
      : "scheduled_at>=? AND scheduled_at<?";
    var args = new ArrayList<Object>();
    args.add(user);
    if (!unscheduled) {
      args.add(from.atStartOfDay(zone).toOffsetDateTime());
      args.add(to.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
    }
    long total = repo
      .jdbc()
      .queryForObject(
        "SELECT count(*) FROM study_tasks WHERE user_id=? AND " + filter,
        Long.class,
        args.toArray()
      );
    args.add(page * 50);
    var items = repo
      .jdbc()
      .queryForList(
        "SELECT * FROM study_tasks WHERE user_id=? AND " +
          filter +
          " ORDER BY scheduled_at NULLS LAST,id LIMIT 50 OFFSET ?",
        args.toArray()
      )
      .stream()
      .map(r -> task(r, user))
      .toList();
    long waiting = repo
      .jdbc()
      .queryForObject(
        "SELECT count(*) FROM study_tasks WHERE user_id=? AND scheduled_at IS NULL AND status='PLANNED'",
        Long.class,
        user
      );
    return new StudyDtos.Calendar(items, page, 50, total, waiting);
  }

  private StudyDtos.Task task(Map<String, Object> r, UUID user) {
    String kind = (String) r.get("target_kind");
    UUID target = (UUID) r.get("target_id");
    boolean available = access.available(user, kind, target);
    return new StudyDtos.Task(
      (UUID) r.get("id"),
      (String) r.get("kind"),
      kind,
      target,
      (String) r.get("title_ru"),
      (String) r.get("title_kz"),
      (String) r.get("reason"),
      StudyRepository.date(r.get("scheduled_at")),
      ((Number) r.get("duration_minutes")).intValue(),
      (String) r.get("status"),
      (Boolean) r.get("pinned"),
      (Boolean) r.get("manually_moved"),
      StudyRepository.revision(r),
      available,
      available ? access.url(kind, target) : null
    );
  }

  public StudyDtos.Task update(UUID id, StudyDtos.TaskChange change) {
    UUID user = actor.id();
    repo.lock(user);
    var r = repo.owned("study_tasks", id, user, true);
    StudyRepository.revision(r, change.revision());
    require(
      change.scheduledAt() != null ||
        change.pinned() != null ||
        change.status() != null,
      "EMPTY_CHANGE"
    );
    if (change.scheduledAt() != null) require(
      Math.abs(
        ChronoUnit.DAYS.between(Instant.now(), change.scheduledAt().toInstant())
      ) <= 730,
      "INVALID_DATE"
    );
    repo
      .jdbc()
      .update(
        "UPDATE study_tasks SET scheduled_at=coalesce(?,scheduled_at),manually_moved=manually_moved OR ?,pinned=coalesce(?,pinned),status=coalesce(?,status),revision=revision+1,updated_at=now() WHERE id=? AND user_id=?",
        change.scheduledAt(),
        change.scheduledAt() != null,
        change.pinned(),
        change.status(),
        id,
        user
      );
    return task(repo.owned("study_tasks", id, user, false), user);
  }

  public StudyDtos.Open open(UUID id) {
    var r = repo.owned("study_tasks", id, actor.id(), false);
    if (
      !access.available(
        actor.id(),
        (String) r.get("target_kind"),
        (UUID) r.get("target_id")
      )
    ) throw PlatformException.missing();
    return new StudyDtos.Open(
      r.get("kind").equals("ERROR_REVIEW")
        ? "/learning/errors"
        : access.url((String) r.get("target_kind"), (UUID) r.get("target_id"))
    );
  }
}
