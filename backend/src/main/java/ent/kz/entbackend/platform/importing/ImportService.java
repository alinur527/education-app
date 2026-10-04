package ent.kz.entbackend.platform.importing;

import com.fasterxml.jackson.databind.*;
import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.content.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class ImportService {

  public record Problem(int row, String field, String code, String detail) {}

  public record Preview(
    UUID id,
    String fileName,
    String status,
    JsonNode rows,
    JsonNode errors,
    JsonNode result
  ) {}

  private final JdbcTemplate db;
  private final Actor actor;
  private final ImportParser parser;
  private final ContentValidation validation;
  private final ContentRepository records;
  private final ContentPolicy policy;
  private final ContentService content;
  private final AuditService audit;

  public ImportService(
    JdbcTemplate db,
    Actor actor,
    ImportParser parser,
    ContentValidation validation,
    ContentRepository records,
    ContentPolicy policy,
    ContentService content,
    AuditService audit
  ) {
    this.db = db;
    this.actor = actor;
    this.parser = parser;
    this.validation = validation;
    this.records = records;
    this.policy = policy;
    this.content = content;
    this.audit = audit;
  }

  public Preview preview(MultipartFile file) {
    actor.editorOnly();
    JsonNode rows;
    try {
      rows = parser.parse(
        Objects.requireNonNullElse(file.getOriginalFilename(), "import.json"),
        file.getBytes()
      );
    } catch (java.io.IOException e) {
      throw new PlatformException(400, "IMPORT_PARSE_ERROR");
    }
    List<Problem> errors = validate(rows);
    UUID id = UUID.randomUUID();
    String name = Objects.requireNonNullElse(
      file.getOriginalFilename(),
      "import"
    );
    PlatformException.require(name.length() <= 255, "INVALID_FILE_NAME");
    db.update(
      "INSERT INTO content_imports(id,created_by,file_name,rows,errors,status) VALUES (?,?,?,?::jsonb,?::jsonb,?)",
      id,
      actor.id(),
      name,
      rows.toString(),
      records.encode(errors),
      errors.isEmpty() ? "VALID" : "INVALID"
    );
    return get(id, false);
  }

  private List<Problem> validate(JsonNode rows) {
    var errors = new ArrayList<Problem>();
    var known = new HashMap<String, ContentKind>();
    int index = 0;
    for (JsonNode row : rows) {
      index++;
      try {
        PlatformException.require(row.isObject(), "INVALID_ROW");
        row
          .fieldNames()
          .forEachRemaining(k ->
            PlatformException.require(
              Set.of(
                "key",
                "kind",
                "parentKey",
                "parentId",
                "payload"
              ).contains(k),
              "UNKNOWN_FIELD"
            )
          );
        String key = row.path("key").asText();
        PlatformException.require(
          key.matches("[A-Za-z0-9_-]{1,80}") && !known.containsKey(key),
          "DUPLICATE_OR_INVALID_KEY"
        );
        ContentKind kind = ContentKind.valueOf(row.path("kind").asText());
        validation.validate(kind, row.path("payload"), false);
        ContentKind parent = null;
        PlatformException.require(
          !(row.hasNonNull("parentId") && row.hasNonNull("parentKey")),
          "AMBIGUOUS_PARENT"
        );
        if (row.hasNonNull("parentId")) {
          ContentRecord p = records.get(
            ContentValidation.uuid(row.path("parentId").asText()),
            false
          );
          policy.edit(p);
          parent = p.kind();
        } else if (row.hasNonNull("parentKey")) {
          parent = known.get(row.path("parentKey").asText());
          PlatformException.require(
            parent != null,
            "PARENT_MUST_PRECEDE_CHILD"
          );
        }
        ContentKind expected = switch (kind) {
          case SUBJECT, COURSE -> null;
          case TOPIC -> ContentKind.SUBJECT;
          case THEORY, QUESTION, CONTEXT -> ContentKind.TOPIC;
          case MODULE -> ContentKind.COURSE;
          case LESSON -> ContentKind.MODULE;
          case QUIZ -> ContentKind.LESSON;
          case ASSIGNMENT -> parent == ContentKind.LESSON
            ? ContentKind.LESSON
            : ContentKind.COURSE;
        };
        PlatformException.require(parent == expected, "INVALID_PARENT");
        known.put(key, kind);
      } catch (Exception e) {
        String code =
          e instanceof PlatformException p ? p.code() : "INVALID_ROW";
        String detail = code.equals("CORRECT_OPTION_MISSING")
          ? "correctOptionId=" +
            row.path("payload").path("correctOptionId").asText() +
            "; options=" +
            row.path("payload").path("options").findValuesAsText("id")
          : code;
        errors.add(
          new Problem(
            index,
            code.equals("CORRECT_OPTION_MISSING") ? "correctOptionId" : "row",
            code,
            detail
          )
        );
      }
    }
    return errors;
  }

  public Preview get(UUID id, boolean lock) {
    actor.editorOnly();
    var rows = db.queryForList(
      "SELECT * FROM content_imports WHERE id=? AND (? OR created_by=?)" +
        (lock ? " FOR UPDATE" : ""),
      id,
      actor.admin(),
      actor.id()
    );
    if (rows.isEmpty()) throw PlatformException.missing();
    var r = rows.getFirst();
    return new Preview(
      id,
      (String) r.get("file_name"),
      (String) r.get("status"),
      records.parse(r.get("rows").toString()),
      records.parse(r.get("errors").toString()),
      r.get("result") == null ? null : records.parse(r.get("result").toString())
    );
  }

  public Preview confirm(UUID id) {
    Preview p = get(id, true);
    if (p.status().equals("IMPORTED")) return p;
    if (!p.status().equals("VALID")) throw new PlatformException(
      409,
      "IMPORT_INVALID"
    );
    if (!validate(p.rows()).isEmpty()) throw new PlatformException(
      409,
      "IMPORT_REFERENCES_CHANGED"
    );
    Map<String, UUID> ids = new LinkedHashMap<>();
    for (JsonNode row : p.rows()) {
      UUID parent = row.hasNonNull("parentId")
        ? ContentValidation.uuid(row.path("parentId").asText())
        : ids.get(row.path("parentKey").asText());
      var imported = row.path("payload").deepCopy();
      if (!imported.hasNonNull("sourceType")) (
        (com.fasterxml.jackson.databind.node.ObjectNode) imported
      ).put("sourceType", "IMPORTED");
      var created = content.create(
        new ContentDtos.Write(
          ContentKind.valueOf(row.path("kind").asText()),
          parent,
          imported,
          null
        )
      );
      ids.put(row.path("key").asText(), created.id());
    }
    db.update(
      "UPDATE content_imports SET status='IMPORTED',result=?::jsonb,imported_at=now() WHERE id=?",
      records.encode(ids),
      id
    );
    audit.record(id, "IMPORT", "CONFIRM", null);
    return get(id, false);
  }

  public ContentDtos.Page<Map<String, Object>> history(int page) {
    actor.editorOnly();
    page = Math.max(0, page);
    var rows = db.queryForList(
      "SELECT id,file_name AS \"fileName\",status,created_at AS \"createdAt\" FROM content_imports WHERE (? OR created_by=?) ORDER BY created_at DESC LIMIT 25 OFFSET ?",
      actor.admin(),
      actor.id(),
      page * 25
    );
    return new ContentDtos.Page<>(
      rows,
      page,
      25,
      db.queryForObject(
        "SELECT count(*) FROM content_imports WHERE (? OR created_by=?)",
        Long.class,
        actor.admin(),
        actor.id()
      )
    );
  }
}
