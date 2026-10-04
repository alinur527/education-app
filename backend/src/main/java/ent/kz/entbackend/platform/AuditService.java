package ent.kz.entbackend.platform;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

  private final JdbcTemplate db;
  private final Actor actor;

  public AuditService(JdbcTemplate db, Actor actor) {
    this.db = db;
    this.actor = actor;
  }

  public void record(UUID id, String type, String operation, Long revision) {
    db.update(
      "INSERT INTO audit_events(actor_id,entity_id,entity_type,operation,revision) VALUES (?,?,?,?,?)",
      actor.id(),
      id,
      type,
      operation,
      revision
    );
  }
}
