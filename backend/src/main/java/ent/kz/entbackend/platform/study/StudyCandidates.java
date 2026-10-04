package ent.kz.entbackend.platform.study;

import ent.kz.entbackend.platform.learning.LearningRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class StudyCandidates {

  public record Candidate(
    String key,
    String kind,
    String targetKind,
    UUID targetId,
    String ru,
    String kz,
    String reason,
    int minutes,
    int priority,
    OffsetDateTime due
  ) {}

  private final StudyRepository repo;
  private final StudyAccess access;
  private final LearningRepository learning;

  public StudyCandidates(
    StudyRepository repo,
    StudyAccess access,
    LearningRepository learning
  ) {
    this.repo = repo;
    this.access = access;
    this.learning = learning;
  }

  public List<Candidate> forUser(UUID user, StudyDtos.Profile profile) {
    var result = new ArrayList<Candidate>();
    var selected = new HashSet<>(profile.selectedSubjects());
    var errors = new HashSet<UUID>();
    learning.errors(user).forEach(r -> errors.add((UUID) r.get("topicId")));
    for (var t : learning.topics(user)) {
      UUID id = (UUID) t.get("topicId");
      if (!selected.contains((UUID) t.get("subjectId"))) continue;
      String ru = (String) t.get("titleRu"),
        kz = Objects.toString(t.get("titleKz"), "");
      if (errors.contains(id)) result.add(
        new Candidate(
          "ERROR_REVIEW:" + id,
          "ERROR_REVIEW",
          "TOPIC",
          id,
          ru,
          kz,
          "RECENT_ERRORS",
          15,
          1,
          null
        )
      );
      if (
        ((Number) t.get("theoryRead")).longValue() <
        ((Number) t.get("theoryTotal")).longValue()
      ) result.add(
        new Candidate(
          "READ:" + id,
          "READ",
          "TOPIC",
          id,
          ru,
          kz,
          "UNREAD_THEORY",
          20,
          3,
          null
        )
      );
      boolean questions = ((Number) t.get("questionCount")).longValue() > 0;
      if (questions) {
        boolean weak =
          ((Number) t.get("attempts")).longValue() > 0 &&
          ((Number) t.get("mastery")).doubleValue() < 70;
        result.add(
          new Candidate(
            "PRACTICE:" + id,
            "PRACTICE",
            "TOPIC",
            id,
            ru,
            kz,
            weak ? "WEAK_TOPIC" : "TOPIC_PRACTICE",
            20,
            weak ? 2 : 4,
            null
          )
        );
      }
    }
    for (var r : repo.jdbc().queryForList(
      """
      SELECT l.id,r.published_payload->>'titleRu' AS ru,r.published_payload->>'titleKz' AS kz
      FROM lessons l JOIN course_modules m ON m.id=l.module_id JOIN enrollments e ON e.course_id=m.course_id
      JOIN content_records r ON r.id=l.id WHERE e.user_id=? AND e.status IN ('ACTIVE','COMPLETED')
      AND NOT EXISTS(SELECT 1 FROM lesson_progress p WHERE p.user_id=e.user_id AND p.lesson_id=l.id)
      ORDER BY m.sort_order,l.sort_order,l.id
      """,
      user
    )) {
      UUID id = (UUID) r.get("id");
      if (access.available(user, "LESSON", id)) result.add(
        new Candidate(
          "LESSON:" + id,
          "LESSON",
          "LESSON",
          id,
          (String) r.get("ru"),
          Objects.toString(r.get("kz"), ""),
          "UNFINISHED_LESSON",
          25,
          3,
          null
        )
      );
    }
    for (var r : repo.jdbc().queryForList(
      """
      SELECT DISTINCT a.id,a.due_at,r.published_payload->>'titleRu' AS ru,r.published_payload->>'titleKz' AS kz
      FROM assignments a JOIN content_records r ON r.id=a.id JOIN assignment_groups ag ON ag.assignment_id=a.id
      JOIN group_members gm ON gm.group_id=ag.group_id WHERE gm.user_id=?
      AND NOT EXISTS(SELECT 1 FROM assignment_submissions s WHERE s.assignment_id=a.id AND s.user_id=gm.user_id)
      """,
      user
    )) {
      UUID id = (UUID) r.get("id");
      if (access.available(user, "ASSIGNMENT", id)) result.add(
        new Candidate(
          "ASSIGNMENT:" + id,
          "ASSIGNMENT",
          "ASSIGNMENT",
          id,
          (String) r.get("ru"),
          Objects.toString(r.get("kz"), ""),
          "ASSIGNMENT_DUE",
          30,
          0,
          StudyRepository.date(r.get("due_at"))
        )
      );
    }
    for (var r : repo
      .jdbc()
      .queryForList(
        "SELECT id,title FROM study_notes WHERE user_id=? AND card_front<>'' AND card_back<>'' AND (next_review_at IS NULL OR next_review_at<=now()) ORDER BY id",
        user
      )) {
      UUID id = (UUID) r.get("id");
      result.add(
        new Candidate(
          "REPETITION:" +
            id +
            ":" +
            LocalDate.now(ZoneId.of(profile.timeZone())),
          "REPETITION",
          "NOTE",
          id,
          (String) r.get("title"),
          (String) r.get("title"),
          "PERSONAL_CARD",
          10,
          2,
          null
        )
      );
    }
    result.sort(
      Comparator.comparingInt(Candidate::priority)
        .thenComparing(c -> c.due() == null ? Instant.MAX : c.due().toInstant())
        .thenComparing(Candidate::key)
    );
    return result;
  }
}
