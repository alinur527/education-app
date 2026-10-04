package ent.kz.entbackend.platform.study;

import static ent.kz.entbackend.platform.PlatformException.require;

import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.content.ContentDtos.Page;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StudyDeadlineService {

  private final StudyRepository repo;
  private final Actor actor;
  private final StudyAccess access;

  public StudyDeadlineService(
    StudyRepository repo,
    Actor actor,
    StudyAccess access
  ) {
    this.repo = repo;
    this.actor = actor;
    this.access = access;
  }

  public Page<StudyDtos.Deadline> list(LocalDate from, LocalDate to, int page) {
    require(
      !to.isBefore(from) && ChronoUnit.DAYS.between(from, to) <= 93,
      "INVALID_DATE_WINDOW"
    );
    require(page >= 0 && page <= 10000, "INVALID_PAGE");
    UUID user = actor.id();
    ZoneId zone = ZoneId.of(repo.profile(user).timeZone());
    var items = repo
      .jdbc()
      .queryForList(
        """
        SELECT DISTINCT a.id,a.due_at,r.published_payload->>'titleRu' AS ru,r.published_payload->>'titleKz' AS kz,
        EXISTS(SELECT 1 FROM assignment_submissions s WHERE s.assignment_id=a.id AND s.user_id=gm.user_id) AS submitted
        FROM assignments a JOIN content_records r ON r.id=a.id JOIN assignment_groups ag ON ag.assignment_id=a.id
        JOIN group_members gm ON gm.group_id=ag.group_id WHERE gm.user_id=? AND a.due_at>=? AND a.due_at<?
        ORDER BY a.due_at,a.id
        """,
        user,
        from.atStartOfDay(zone).toOffsetDateTime(),
        to.plusDays(1).atStartOfDay(zone).toOffsetDateTime()
      )
      .stream()
      .filter(r -> access.available(user, "ASSIGNMENT", (UUID) r.get("id")))
      .map(r ->
        new StudyDtos.Deadline(
          (UUID) r.get("id"),
          (String) r.get("ru"),
          Objects.toString(r.get("kz"), ""),
          StudyRepository.date(r.get("due_at")),
          (Boolean) r.get("submitted"),
          access.url("ASSIGNMENT", (UUID) r.get("id"))
        )
      )
      .toList();
    return new Page<>(
      items
        .stream()
        .skip((long) page * 25)
        .limit(25)
        .toList(),
      page,
      25,
      items.size()
    );
  }
}
