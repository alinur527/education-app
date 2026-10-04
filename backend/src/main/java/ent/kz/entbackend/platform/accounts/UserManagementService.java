package ent.kz.entbackend.platform.accounts;

import ent.kz.entbackend.entity.UserRole;
import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.content.ContentDtos.Page;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserManagementService {

  public record Change(
    @NotNull UserRole role,
    @NotNull Boolean active,
    @Min(0) long revision
  ) {}

  private final JdbcTemplate db;
  private final Actor actor;
  private final AuditService audit;

  public UserManagementService(
    JdbcTemplate db,
    Actor actor,
    AuditService audit
  ) {
    this.db = db;
    this.actor = actor;
    this.audit = audit;
  }

  public Page<Map<String, Object>> list(String q, String role, int page) {
    actor.adminOnly();
    page = Math.max(0, page);
    String match = "%" + q + "%";
    String where =
      " WHERE (email ILIKE ? OR first_name ILIKE ? OR last_name ILIKE ?) AND (?='' OR role=?)";
    var items = db.queryForList(
      "SELECT id,email,first_name AS \"firstName\",last_name AS \"lastName\",role,is_active AS active,revision FROM users" +
        where +
        " ORDER BY created_at DESC,id LIMIT 25 OFFSET ?",
      match,
      match,
      match,
      role,
      role,
      page * 25
    );
    return new Page<>(
      items,
      page,
      25,
      db.queryForObject(
        "SELECT count(*) FROM users" + where,
        Long.class,
        match,
        match,
        match,
        role,
        role
      )
    );
  }

  public void update(UUID id, Change req) {
    actor.adminOnly();
    // One lock serializes role administration, including last-admin checks.
    db.execute("SELECT pg_advisory_xact_lock(72346911)");
    var rows = db.queryForList(
      "SELECT role,is_active,revision FROM users WHERE id=? FOR UPDATE",
      id
    );
    if (rows.isEmpty()) throw PlatformException.missing();
    var row = rows.getFirst();
    if (
      ((Number) row.get("revision")).longValue() != req.revision()
    ) throw new PlatformException(409, "REVISION_CONFLICT");
    if (
      id.equals(actor.id()) && (req.role() != UserRole.ADMIN || !req.active())
    ) throw new PlatformException(409, "SELF_ADMIN_CHANGE");
    if (
      Boolean.TRUE.equals(row.get("is_active")) &&
      row.get("role").equals("ADMIN") &&
      (!req.active() || req.role() != UserRole.ADMIN) &&
      db.queryForObject(
        "SELECT count(*) FROM users WHERE role='ADMIN' AND is_active",
        Long.class
      ) <= 1
    ) throw new PlatformException(409, "LAST_ADMIN");
    db.update(
      "UPDATE users SET role=?,is_active=?,revision=revision+1,updated_at=now() WHERE id=?",
      req.role().name(),
      req.active(),
      id
    );
    audit.record(id, "USER", "ROLE_OR_STATUS", req.revision() + 1);
  }
}
