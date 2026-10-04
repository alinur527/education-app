package ent.kz.entbackend.platform.study;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ent.kz.entbackend.platform.PlatformException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudyRepository {

  private final JdbcTemplate db;
  private final ObjectMapper json;

  public StudyRepository(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  JdbcTemplate jdbc() {
    return db;
  }

  String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  <T> T decode(Object value, TypeReference<T> type) {
    try {
      return json.readValue(value.toString(), type);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public StudyDtos.Profile profile(UUID user) {
    var rows = db.queryForList(
      "SELECT * FROM study_profiles WHERE user_id=?",
      user
    );
    if (rows.isEmpty()) return new StudyDtos.Profile(
      "",
      LocalDate.now(ZoneId.of("Asia/Almaty")).plusDays(30),
      List.of(1, 2, 3, 4, 5),
      30,
      "Asia/Almaty",
      List.of(),
      0
    );
    var r = rows.getFirst();
    return new StudyDtos.Profile(
      (String) r.get("goal"),
      ((java.sql.Date) r.get("target_date")).toLocalDate(),
      decode(r.get("available_days"), new TypeReference<List<Integer>>() {}),
      ((Number) r.get("minutes_per_day")).intValue(),
      (String) r.get("time_zone"),
      decode(r.get("selected_subjects"), new TypeReference<List<UUID>>() {}),
      ((Number) r.get("revision")).longValue()
    );
  }

  /** Locks a stable user row, including the first save/recompute with no profile yet. */
  void lock(UUID user) {
    db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", user);
  }

  Map<String, Object> owned(String table, UUID id, UUID user, boolean lock) {
    if (
      !Set.of("study_tasks", "study_notes", "study_notifications").contains(
        table
      )
    ) throw new IllegalArgumentException();
    var rows = db.queryForList(
      "SELECT * FROM " +
        table +
        " WHERE id=? AND user_id=?" +
        (lock ? " FOR UPDATE" : ""),
      id,
      user
    );
    if (rows.isEmpty()) throw PlatformException.missing();
    return rows.getFirst();
  }

  static OffsetDateTime date(Object value) {
    return value == null
      ? null
      : value instanceof OffsetDateTime d
        ? d
        : ((java.sql.Timestamp) value).toInstant().atOffset(ZoneOffset.UTC);
  }

  static long revision(Map<String, Object> row) {
    return ((Number) row.get("revision")).longValue();
  }

  static void revision(Map<String, Object> row, long expected) {
    if (revision(row) != expected) throw new PlatformException(
      409,
      "REVISION_CONFLICT"
    );
  }
}
