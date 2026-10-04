package ent.kz.entbackend.platform.content;

import static ent.kz.entbackend.platform.PlatformException.require;

import ent.kz.entbackend.platform.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ContentService {

  private final ContentRepository records;
  private final ContentValidation validation;
  private final ContentPolicy policy;
  private final PublicationRepository publication;
  private final Actor actor;
  private final AuditService audit;
  private final JdbcTemplate db;

  public ContentService(
    ContentRepository records,
    ContentValidation validation,
    ContentPolicy policy,
    PublicationRepository publication,
    Actor actor,
    AuditService audit,
    JdbcTemplate db
  ) {
    this.records = records;
    this.validation = validation;
    this.policy = policy;
    this.publication = publication;
    this.actor = actor;
    this.audit = audit;
    this.db = db;
  }

  public ContentDtos.Page<ContentDtos.Summary> list(
    String kind,
    String status,
    String q,
    UUID parentId,
    boolean missingTranslation,
    int page,
    int size
  ) {
    actor.staffOnly();
    return records.list(
      kind,
      status,
      q,
      parentId,
      missingTranslation,
      actor.editor() ? null : actor.id(),
      Math.max(0, page),
      Math.clamp(size, 1, 100)
    );
  }

  public ContentDtos.View get(UUID id) {
    ContentRecord c = records.get(id, false);
    policy.edit(c);
    return ContentDtos.View.of(c);
  }

  public ContentDtos.View create(ContentDtos.Write req) {
    actor.staffOnly();
    validation.validate(req.kind(), req.payload(), false);
    UUID owner = actor.id();
    if (req.kind().ent()) actor.editorOnly();
    if (req.parentId() != null) {
      ContentRecord parent = records.get(req.parentId(), true);
      policy.edit(parent);
      owner = parent.ownerId() == null ? actor.id() : parent.ownerId();
      checkParent(req.kind(), parent.kind());
    } else require(
      req.kind() == ContentKind.SUBJECT || req.kind() == ContentKind.COURSE,
      "PARENT_REQUIRED"
    );
    UUID id = UUID.randomUUID();
    records.insert(
      id,
      req.kind(),
      req.parentId(),
      owner,
      req.payload(),
      actor.id()
    );
    ContentRecord c = records.get(id, false);
    publication.project(c, false);
    audit.record(id, c.kind().name(), "CREATE_DRAFT", 1L);
    return ContentDtos.View.of(c);
  }

  public ContentDtos.View update(UUID id, ContentDtos.Write req) {
    ContentRecord c = records.get(id, true);
    policy.edit(c);
    revision(c, req.version());
    if (c.status().equals("ARCHIVED")) throw new PlatformException(
      409,
      "ARCHIVED_CONTENT"
    );
    require(
      c.kind() == req.kind() && Objects.equals(c.parentId(), req.parentId()),
      "IMMUTABLE_PARENT"
    );
    validation.validate(c.kind(), req.payload(), false);
    records.save(id, req.payload(), actor.id());
    audit.record(id, c.kind().name(), "SAVE_DRAFT", c.version() + 1);
    return get(id);
  }

  public ContentDtos.View transition(UUID id, ContentDtos.Transition req) {
    ContentRecord c = records.get(id, true);
    policy.edit(c);
    revision(c, req.version());
    String next = req.status();
    if (
      next.equals("ARCHIVED") || c.status().equals("ARCHIVED")
    ) actor.adminOnly();
    boolean allowed = switch (next) {
      case "DRAFT" -> !c.status().equals("DRAFT");
      case "REVIEW" -> c.status().equals("DRAFT");
      case "PUBLISHED" -> c.status().equals("REVIEW");
      case "ARCHIVED" -> !c.status().equals("ARCHIVED");
      default -> false;
    };
    if (!allowed) throw new PlatformException(409, "INVALID_TRANSITION");
    if (next.equals("REVIEW") || next.equals("PUBLISHED")) {
      validation.validate(c.kind(), c.payload(), true);
      for (var b : c.payload().path("blocks"))
        if (b.hasNonNull("materialId")) {
          UUID material = ContentValidation.uuid(b.path("materialId").asText());
          require(
            Boolean.TRUE.equals(
              db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM materials WHERE id=? AND content_id=? AND (?<>'IMAGE' OR mime_type LIKE 'image/%'))",
                Boolean.class,
                material,
                id,
                b.path("type").asText()
              )
            ),
            "MATERIAL_PARENT_MISMATCH"
          );
        }
    }
    if (
      next.equals("PUBLISHED") &&
      c.kind() == ContentKind.ASSIGNMENT &&
      c.payload().path("maxScore").asInt(100) !=
        db.queryForObject(
          "SELECT max_score FROM assignments WHERE id=?",
          Integer.class,
          id
        ) &&
      Boolean.TRUE.equals(
        db.queryForObject(
          "SELECT EXISTS(SELECT 1 FROM assignment_submissions WHERE assignment_id=?)",
          Boolean.class,
          id
        )
      )
    ) throw new PlatformException(409, "ASSIGNMENT_SCALE_LOCKED");
    if (next.equals("PUBLISHED")) {
      publication.project(c, true);
      db.update("UPDATE materials SET published=true WHERE content_id=?", id);
    }
    if (next.equals("ARCHIVED")) publication.archive(c);
    records.transition(id, next, actor.id());
    audit.record(id, c.kind().name(), next, c.version() + 1);
    return get(id);
  }

  public ContentDtos.Published published(UUID id) {
    ContentRecord c = records.get(id, false);
    policy.read(c);
    if (
      c.publishedPayload() == null || !policy.visible(c)
    ) throw PlatformException.missing();
    if (
      c.kind() == ContentKind.QUESTION || c.kind() == ContentKind.QUIZ
    ) throw PlatformException.forbidden();
    return new ContentDtos.Published(
      id,
      c.kind(),
      c.parentId(),
      PublicLearningPayload.of(c.publishedPayload())
    );
  }

  public List<Map<String, Object>> history(UUID id) {
    get(id);
    return db.queryForList(
      "SELECT a.actor_id AS \"actorId\",concat_ws(' ',u.first_name,u.last_name) AS \"actorName\",a.operation,a.revision,a.created_at AS \"createdAt\" FROM audit_events a LEFT JOIN users u ON u.id=a.actor_id WHERE a.entity_id=? ORDER BY a.created_at DESC LIMIT 100",
      id
    );
  }

  private void revision(ContentRecord c, Long version) {
    if (version == null || version != c.version()) throw new PlatformException(
      409,
      "REVISION_CONFLICT"
    );
  }

  private void checkParent(ContentKind child, ContentKind parent) {
    require(
      switch (child) {
        case TOPIC -> parent == ContentKind.SUBJECT;
        case THEORY, QUESTION, CONTEXT -> parent == ContentKind.TOPIC;
        case MODULE -> parent == ContentKind.COURSE;
        case LESSON -> parent == ContentKind.MODULE;
        case QUIZ -> parent == ContentKind.LESSON;
        case ASSIGNMENT -> parent == ContentKind.COURSE ||
          parent == ContentKind.LESSON;
        default -> false;
      },
      "INVALID_PARENT"
    );
  }
}
