package ent.kz.entbackend.platform.submissions;

import ent.kz.entbackend.platform.PlatformException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SubmissionFileRepository {

  private final JdbcTemplate db;

  public SubmissionFileRepository(JdbcTemplate db) {
    this.db = db;
  }

  public JdbcTemplate jdbc() {
    return db;
  }

  public void lockUser(UUID user) {
    db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", user);
  }

  public Map<String, Object> get(UUID id, boolean lock) {
    var rows = db.queryForList(
      "SELECT * FROM submission_files WHERE id=? AND deleted_at IS NULL" +
        (lock ? " FOR UPDATE" : ""),
      id
    );
    if (rows.isEmpty()) throw PlatformException.missing();
    return rows.getFirst();
  }

  public Map<String, Object> metadata(UUID id) {
    return db.queryForMap(
      """
      SELECT f.id,f.assignment_id AS "assignmentId",f.original_file_name AS "originalFileName",f.mime_type AS "mimeType",f.size,f.sha256,
      f.scan_status AS "scanStatus",f.scan_attempts AS "scanAttempts",f.scan_message AS "scanMessage",f.scanned_at AS "scannedAt",f.created_at AS "createdAt",
      EXISTS(SELECT 1 FROM submission_revision_files r WHERE r.file_id=f.id) AS bound
      FROM submission_files f WHERE f.id=?
      """,
      id
    );
  }

  public List<Map<String, Object>> files(UUID revision) {
    return db
      .queryForList(
        "SELECT file_id FROM submission_revision_files WHERE submission_id=? ORDER BY file_id",
        revision
      )
      .stream()
      .map(r -> metadata((UUID) r.get("file_id")))
      .toList();
  }
}
