package ent.kz.entbackend.platform.content;

import static ent.kz.entbackend.platform.PlatformException.require;

import ent.kz.entbackend.platform.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EditorialReviewService {

  private final JdbcTemplate db;
  private final Actor actor;
  private final ContentRepository records;
  private final AuditService audit;

  public EditorialReviewService(
    JdbcTemplate db,
    Actor actor,
    ContentRepository records,
    AuditService audit
  ) {
    this.db = db;
    this.actor = actor;
    this.records = records;
    this.audit = audit;
  }

  public record Review(
    long version,
    String decision,
    String note,
    boolean confirmedHumanReview
  ) {}

  public List<Map<String, Object>> list(UUID id) {
    actor.editorOnly();
    records.get(id, false);
    return db.queryForList(
      "SELECT r.id,r.revision,r.decision,r.note,r.created_at AS \"createdAt\",u.first_name AS \"reviewerName\",r.reviewed_payload=(SELECT payload FROM content_records WHERE id=?) AS current FROM editorial_reviews r JOIN users u ON u.id=r.reviewer_id WHERE r.content_id=? ORDER BY r.created_at DESC LIMIT 25",
      id,
      id
    );
  }

  public Object save(UUID id, Review req) {
    actor.editorOnly();
    require(
      req.confirmedHumanReview() &&
        Set.of("APPROVED", "CHANGES_REQUIRED").contains(
          Objects.toString(req.decision(), "")
        ),
      "EXPLICIT_EDITORIAL_REVIEW_REQUIRED"
    );
    require(
      req.note() != null &&
        !req.note().isBlank() &&
        req.note().length() <= 4000,
      "REVIEW_NOTE_REQUIRED"
    );
    var c = records.get(id, true);
    if (c.version() != req.version()) throw new PlatformException(
      409,
      "REVISION_CONFLICT"
    );
    if (c.status().equals("ARCHIVED")) throw new PlatformException(
      409,
      "ARCHIVED_CONTENT"
    );
    db.update(
      "INSERT INTO editorial_reviews(content_id,revision,reviewed_payload,reviewer_id,decision,note) VALUES (?,?,?::jsonb,?,?,?)",
      id,
      c.version(),
      c.payload().toString(),
      actor.id(),
      req.decision(),
      req.note()
    );
    audit.record(
      id,
      c.kind().name(),
      "EDITORIAL_" + req.decision(),
      c.version()
    );
    return list(id);
  }
}
