package ent.kz.entbackend.platform.study;

import static ent.kz.entbackend.platform.PlatformException.require;

import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.content.ContentDtos.Page;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StudyNotesService {

  private final StudyRepository repo;
  private final Actor actor;
  private final StudyAccess access;

  public StudyNotesService(
    StudyRepository repo,
    Actor actor,
    StudyAccess access
  ) {
    this.repo = repo;
    this.actor = actor;
    this.access = access;
  }

  public Page<StudyDtos.Note> list(
    String q,
    boolean bookmarked,
    boolean due,
    int page
  ) {
    require(q.length() <= 200 && page >= 0 && page <= 10000, "INVALID_QUERY");
    UUID user = actor.id();
    String where =
      " WHERE user_id=? AND (title ILIKE ? OR body ILIKE ?) AND (NOT ? OR bookmarked) AND (NOT ? OR (card_front<>'' AND card_back<>'' AND (next_review_at IS NULL OR next_review_at<=now())))";
    Object[] args = { user, "%" + q + "%", "%" + q + "%", bookmarked, due };
    long total = repo
      .jdbc()
      .queryForObject(
        "SELECT count(*) FROM study_notes" + where,
        Long.class,
        args
      );
    var values = new ArrayList<>(Arrays.asList(args));
    values.add(page * 25);
    var items = repo
      .jdbc()
      .queryForList(
        "SELECT * FROM study_notes" +
          where +
          " ORDER BY next_review_at NULLS FIRST,updated_at DESC,id LIMIT 25 OFFSET ?",
        values.toArray()
      )
      .stream()
      .map(r -> view(r, user))
      .toList();
    return new Page<>(items, page, 25, total);
  }

  public StudyDtos.Note get(UUID id) {
    return view(repo.owned("study_notes", id, actor.id(), false), actor.id());
  }

  public StudyDtos.Note save(UUID id, StudyDtos.NoteWrite n) {
    UUID user = actor.id();
    repo.lock(user);
    require(
      n.cardFront().isBlank() == n.cardBack().isBlank(),
      "CARD_BOTH_SIDES_REQUIRED"
    );
    var rows = repo
      .jdbc()
      .queryForList("SELECT * FROM study_notes WHERE id=?", id);
    if (rows.isEmpty()) {
      require(n.revision() == 0, "INVALID_REVISION");
      if (
        !access.available(user, n.targetKind(), n.targetId())
      ) throw PlatformException.missing();
      repo
        .jdbc()
        .update(
          "INSERT INTO study_notes(id,user_id,target_kind,target_id,title,body,bookmarked,card_front,card_back,next_review_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
          id,
          user,
          n.targetKind(),
          n.targetId(),
          n.title().trim(),
          n.body(),
          n.bookmarked(),
          n.cardFront(),
          n.cardBack(),
          n.cardFront().isBlank() ? null : OffsetDateTime.now(ZoneOffset.UTC)
        );
    } else {
      var r = rows.getFirst();
      if (!r.get("user_id").equals(user)) throw PlatformException.missing();
      StudyRepository.revision(r, n.revision());
      require(
        r.get("target_kind").equals(n.targetKind()) &&
          r.get("target_id").equals(n.targetId()),
        "IMMUTABLE_NOTE_TARGET"
      );
      boolean changed =
        !r.get("card_front").equals(n.cardFront()) ||
        !r.get("card_back").equals(n.cardBack());
      repo
        .jdbc()
        .update(
          "UPDATE study_notes SET title=?,body=?,bookmarked=?,card_front=?,card_back=?,next_review_at=CASE WHEN ? THEN ? ELSE next_review_at END,interval_days=CASE WHEN ? THEN 0 ELSE interval_days END,review_count=CASE WHEN ? THEN 0 ELSE review_count END,revision=revision+1,updated_at=now() WHERE id=? AND user_id=?",
          n.title().trim(),
          n.body(),
          n.bookmarked(),
          n.cardFront(),
          n.cardBack(),
          changed,
          n.cardFront().isBlank() ? null : OffsetDateTime.now(ZoneOffset.UTC),
          changed,
          changed,
          id,
          user
        );
    }
    return get(id);
  }

  public void delete(UUID id, long revision) {
    repo.lock(actor.id());
    var r = repo.owned("study_notes", id, actor.id(), true);
    StudyRepository.revision(r, revision);
    repo
      .jdbc()
      .update(
        "DELETE FROM study_tasks WHERE user_id=? AND target_kind='NOTE' AND target_id=?",
        actor.id(),
        id
      );
    repo
      .jdbc()
      .update(
        "DELETE FROM study_notes WHERE id=? AND user_id=?",
        id,
        actor.id()
      );
  }

  public StudyDtos.Note review(UUID id, StudyDtos.Review req) {
    UUID user = actor.id();
    repo.lock(user);
    var r = repo.owned("study_notes", id, user, true);
    StudyRepository.revision(r, req.revision());
    require(
      !r.get("card_front").toString().isBlank() &&
        !r.get("card_back").toString().isBlank(),
      "CARD_REQUIRED"
    );
    int old = ((Number) r.get("interval_days")).intValue();
    int days = switch (req.rating()) {
      case "AGAIN" -> 1;
      case "GOOD" -> Math.min(60, Math.max(3, old * 2));
      case "EASY" -> Math.min(90, Math.max(7, old * 3));
      default -> throw new PlatformException(400, "INVALID_RATING");
    };
    ZoneId zone = ZoneId.of(repo.profile(user).timeZone());
    var due = LocalDate.now(zone)
      .plusDays(days)
      .atTime(18, 0)
      .atZone(zone)
      .toOffsetDateTime();
    repo
      .jdbc()
      .update(
        "UPDATE study_notes SET interval_days=?,next_review_at=?,review_count=review_count+1,revision=revision+1,updated_at=now() WHERE id=? AND user_id=?",
        days,
        due,
        id,
        user
      );
    repo
      .jdbc()
      .update(
        "UPDATE study_tasks SET status='COMPLETED',revision=revision+1,updated_at=now() WHERE user_id=? AND target_kind='NOTE' AND target_id=? AND status='PLANNED'",
        user,
        id
      );
    return get(id);
  }

  private StudyDtos.Note view(Map<String, Object> r, UUID user) {
    String kind = (String) r.get("target_kind");
    UUID target = (UUID) r.get("target_id");
    boolean available = access.available(user, kind, target);
    return new StudyDtos.Note(
      (UUID) r.get("id"),
      kind,
      target,
      (String) r.get("title"),
      (String) r.get("body"),
      (Boolean) r.get("bookmarked"),
      (String) r.get("card_front"),
      (String) r.get("card_back"),
      StudyRepository.date(r.get("next_review_at")),
      ((Number) r.get("interval_days")).intValue(),
      ((Number) r.get("review_count")).intValue(),
      StudyRepository.revision(r),
      available,
      available ? access.url(kind, target) : null,
      StudyRepository.date(r.get("updated_at"))
    );
  }
}
