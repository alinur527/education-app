package ent.kz.entbackend.platform.content;

import static ent.kz.entbackend.platform.PlatformException.require;

import com.fasterxml.jackson.databind.*;
import ent.kz.entbackend.platform.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SourceRegistryService {

  private final JdbcTemplate db;
  private final Actor actor;
  private final ContentRepository records;
  private final AuditService audit;

  public SourceRegistryService(
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

  public record Source(String sourceId, JsonNode metadata, Long revision) {}

  public List<Source> save(List<Source> sources) {
    actor.editorOnly();
    require(
      sources != null && sources.size() > 0 && sources.size() <= 100,
      "SOURCE_BATCH_SIZE"
    );
    Set<String> seen = new HashSet<>();
    for (Source s : sources) {
      require(
        s != null &&
          s.sourceId() != null &&
          s.sourceId().matches("[A-Za-z0-9_.:-]{1,100}") &&
          seen.add(s.sourceId()),
        "INVALID_SOURCE_ID"
      );
      validate(s.metadata());
    }
    List<Source> result = new ArrayList<>();
    seen
      .stream()
      .sorted()
      .forEach(id ->
        db.queryForList(
          "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
          "source:" + id
        )
      );
    for (Source s : sources) {
      var rows = db.queryForList(
        "SELECT * FROM source_registry WHERE external_key=? FOR UPDATE",
        s.sourceId()
      );
      if (rows.isEmpty()) {
        UUID id = UUID.randomUUID();
        db.update(
          "INSERT INTO source_registry(id,external_key,metadata,created_by,updated_by) VALUES (?,?,?::jsonb,?,?)",
          id,
          s.sourceId(),
          s.metadata().toString(),
          actor.id(),
          actor.id()
        );
        audit.record(id, "SOURCE", "CREATE", 1L);
        result.add(new Source(s.sourceId(), s.metadata(), 1L));
      } else {
        var old = rows.getFirst();
        long version = ((Number) old.get("revision")).longValue();
        if (
          !records.parse(old.get("metadata").toString()).equals(s.metadata())
        ) {
          if (
            s.revision() == null || s.revision() != version
          ) throw new PlatformException(409, "SOURCE_REVISION_CONFLICT");
          db.update(
            "UPDATE source_registry SET metadata=?::jsonb,revision=revision+1,updated_by=?,updated_at=now() WHERE external_key=?",
            s.metadata().toString(),
            actor.id(),
            s.sourceId()
          );
          audit.record((UUID) old.get("id"), "SOURCE", "UPDATE", ++version);
        }
        result.add(new Source(s.sourceId(), s.metadata(), version));
      }
    }
    return result;
  }

  public List<Source> list(String query, int page) {
    actor.editorOnly();
    String q = "%" + Objects.requireNonNullElse(query, "") + "%";
    return db.query(
      "SELECT external_key,metadata::text,revision FROM source_registry WHERE external_key ILIKE ? OR metadata->>'sourceName' ILIKE ? ORDER BY external_key LIMIT 50 OFFSET ?",
      (r, n) ->
        new Source(r.getString(1), records.parse(r.getString(2)), r.getLong(3)),
      q,
      q,
      Math.clamp(page, 0, 100000) * 50
    );
  }

  private void validate(JsonNode m) {
    require(
      m != null && m.isObject() && m.toString().length() <= 16000,
      "INVALID_SOURCE"
    );
    Set<String> keys = Set.of(
      "url",
      "resolvedUrl",
      "retrievedAt",
      "contentType",
      "byteSize",
      "sha256",
      "subject",
      "variant",
      "sourceKind",
      "appliesFrom",
      "accessStatus",
      "reuseMode",
      "reviewStatus",
      "sourceName",
      "licenseUrl",
      "attribution",
      "materialId"
    );
    m.fieldNames().forEachRemaining(k ->
      require(keys.contains(k), "SOURCE_FIELD")
    );
    for (String key : keys)
      if (
        m.hasNonNull(key) && !Set.of("byteSize", "appliesFrom").contains(key)
      ) require(
        m.get(key).isTextual() && m.get(key).asText().length() <= 2000,
        "SOURCE_FIELD_TYPE"
      );
    ContentValidation.safeUrl(m.path("url").asText());
    for (String key : List.of("resolvedUrl", "licenseUrl"))
      if (
        m.hasNonNull(key) && !m.path(key).asText().isBlank()
      ) ContentValidation.safeUrl(m.path(key).asText());
    if (m.hasNonNull("sha256")) require(
      m.path("sha256").asText().matches("[a-fA-F0-9]{64}"),
      "SOURCE_HASH"
    );
    if (m.hasNonNull("byteSize")) require(
      m.path("byteSize").isIntegralNumber() &&
        m.path("byteSize").asLong() >= 0 &&
        m.path("byteSize").asLong() <= 100000000,
      "SOURCE_SIZE"
    );
    if (m.hasNonNull("appliesFrom")) require(
      m.path("appliesFrom").isIntegralNumber() &&
        m.path("appliesFrom").asInt() >= 2000 &&
        m.path("appliesFrom").asInt() <= 2200,
      "SOURCE_YEAR"
    );
    if (m.hasNonNull("retrievedAt")) try {
      java.time.OffsetDateTime.parse(m.path("retrievedAt").asText());
    } catch (Exception e) {
      throw new PlatformException(400, "SOURCE_DATE");
    }
    if (m.hasNonNull("reviewStatus")) require(
      Set.of(
        "NOT_REVIEWED",
        "SOURCE_READ",
        "SOURCE_RESEARCH_NOT_CONTENT_APPROVAL",
        "AUTOMATED_CHECKS_ONLY"
      ).contains(m.path("reviewStatus").asText()),
      "SOURCE_REVIEW_STATUS"
    );
    // This registry never performs requests to its stored URLs.
  }
}
