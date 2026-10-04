package ent.kz.entbackend.platform.importing;

import static ent.kz.entbackend.platform.PlatformException.require;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.content.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ContentPackService {

  private final PackRepository packs;
  private final ContentRepository records;
  private final ContentValidation validation;
  private final ContentPolicy policy;
  private final ContentService content;
  private final Actor actor;
  private final ObjectMapper json;
  private final AuditService audit;

  public ContentPackService(
    PackRepository packs,
    ContentRepository records,
    ContentValidation validation,
    ContentPolicy policy,
    ContentService content,
    Actor actor,
    ObjectMapper json,
    AuditService audit
  ) {
    this.packs = packs;
    this.records = records;
    this.validation = validation;
    this.policy = policy;
    this.content = content;
    this.actor = actor;
    this.json = json;
    this.audit = audit;
  }

  public JsonNode preview(JsonNode req) {
    actor.editorOnly();
    require(
      req.isObject() &&
        req.toString().getBytes(StandardCharsets.UTF_8).length <=
          2 * 1024 * 1024,
      "PACK_SIZE"
    );
    require(
      req.path("schema").asText().equals("education-content-pack/v1"),
      "PACK_SCHEMA"
    );
    for (String field : List.of("namespace", "packVersion", "batchKey"))
      require(
        req.path(field).isTextual() &&
          req.path(field).asText().matches("[A-Za-z0-9_.-]{1,100}"),
        "PACK_ID"
      );
    require(
      req.path("rows").isArray() &&
        req.path("rows").size() > 0 &&
        req.path("rows").size() <= 500,
      "PACK_ROWS"
    );
    req
      .fieldNames()
      .forEachRemaining(k ->
        require(
          Set.of(
            "schema",
            "namespace",
            "packVersion",
            "batchKey",
            "rows"
          ).contains(k),
          "PACK_FIELD"
        )
      );
    String ns = req.path("namespace").asText();
    packs.lock(ns);
    String hash = checksum(req);
    var prior = packs.existing(
      ns,
      req.path("packVersion").asText(),
      req.path("batchKey").asText()
    );
    if (prior != null) {
      own(prior);
      if (!prior.get("request_hash").equals(hash)) throw new PlatformException(
        409,
        "BATCH_KEY_REUSED"
      );
      return view(prior);
    }
    ArrayNode decisions = decisions(req);
    boolean valid = true;
    for (JsonNode row : decisions) valid &= !row.has("error");
    UUID id = packs.save(req, actor.id(), hash, decisions, valid);
    return get(id);
  }

  private ArrayNode decisions(JsonNode req) {
    String ns = req.path("namespace").asText();
    ArrayNode out = json.createArrayNode();
    Map<String, ContentKind> prior = new HashMap<>();
    Set<String> seen = new HashSet<>();
    Set<UUID> existingTargets = new HashSet<>();
    int rowNo = 0;
    for (JsonNode row : req.path("rows")) {
      ObjectNode decision = out
        .addObject()
        .put("row", ++rowNo)
        .put("externalKey", row.path("externalKey").asText(""));
      try {
        require(row.isObject(), "INVALID_ROW");
        row
          .fieldNames()
          .forEachRemaining(k ->
            require(
              Set.of(
                "externalKey",
                "kind",
                "parentExternalKey",
                "parentId",
                "existingId",
                "sourceVersion",
                "payload"
              ).contains(k),
              "PACK_ROW_FIELD"
            )
          );
        String key = row.path("externalKey").asText();
        require(
          key.matches("[A-Za-z0-9_.:-]{1,160}") && seen.add(key),
          "DUPLICATE_OR_INVALID_KEY"
        );
        require(
          !row.hasNonNull("sourceVersion") ||
            (row.path("sourceVersion").isTextual() &&
              row.path("sourceVersion").asText().length() > 0 &&
              row.path("sourceVersion").asText().length() <= 100),
          "INVALID_SOURCE_VERSION"
        );
        ContentKind kind = ContentKind.valueOf(row.path("kind").asText());
        validation.validate(kind, row.path("payload"), false);
        require(
          !(row.hasNonNull("parentExternalKey") && row.hasNonNull("parentId")),
          "AMBIGUOUS_PARENT"
        );
        ContentKind parentKind = null;
        UUID parentId = null;
        if (row.hasNonNull("parentId")) {
          parentId = ContentValidation.uuid(row.path("parentId").asText());
          var parent = records.get(parentId, false);
          policy.edit(parent);
          parentKind = parent.kind();
        } else if (row.hasNonNull("parentExternalKey")) {
          String parentKey = row.path("parentExternalKey").asText();
          require(!parentKey.equals(key), "SELF_PARENT");
          var mapping = packs.mapping(ns, parentKey);
          if (mapping != null) {
            parentId = (UUID) mapping.get("content_id");
            var parent = records.get(parentId, false);
            policy.edit(parent);
            parentKind = parent.kind();
          } else {
            parentKind = prior.get(parentKey);
            require(parentKind != null, "PARENT_MUST_PRECEDE_CHILD");
          }
        }
        ContentKind expected = switch (kind) {
          case SUBJECT, COURSE -> null;
          case TOPIC -> ContentKind.SUBJECT;
          case THEORY, QUESTION, CONTEXT -> ContentKind.TOPIC;
          case MODULE -> ContentKind.COURSE;
          case LESSON -> ContentKind.MODULE;
          case QUIZ -> ContentKind.LESSON;
          case ASSIGNMENT -> parentKind == ContentKind.LESSON
            ? ContentKind.LESSON
            : ContentKind.COURSE;
        };
        require(parentKind == expected, "INVALID_PARENT");
        String incoming = checksum(row.path("payload"));
        decision.put("checksum", incoming).put("kind", kind.name());
        var mapping = packs.mapping(ns, key);
        UUID existing =
          mapping != null
            ? (UUID) mapping.get("content_id")
            : row.hasNonNull("existingId")
              ? ContentValidation.uuid(row.path("existingId").asText())
              : null;
        if (existing == null) decision.put("action", "CREATE");
        else {
          require(existingTargets.add(existing), "DUPLICATE_CONTENT_TARGET");
          var current = records.get(existing, false);
          policy.edit(current);
          require(
            current.kind() == kind &&
              Objects.equals(current.parentId(), parentId),
            "IMMUTABLE_PARENT"
          );
          decision
            .put("contentId", existing.toString())
            .put("expectedVersion", current.version());
          String currentHash = checksum(current.payload());
          decision.put("currentChecksum", currentHash);
          if (mapping == null) {
            require(
              currentHash.equals(incoming),
              "ADOPTION_REQUIRES_IDENTICAL_CONTENT"
            );
            decision.put("action", "ADOPT");
          } else if (
            mapping.get("applied_checksum").equals(incoming)
          ) decision.put("action", "UNCHANGED");
          else if (
            current.status().equals("ARCHIVED") ||
            !mapping.get("applied_checksum").equals(currentHash)
          ) decision.put("action", "CONFLICT");
          else decision.put("action", "UPDATE_DRAFT");
        }
        prior.put(key, kind);
      } catch (Exception e) {
        decision.put(
          "error",
          e instanceof PlatformException p ? p.code() : "INVALID_ROW"
        );
      }
    }
    return out;
  }

  public JsonNode confirm(UUID id) {
    actor.editorOnly();
    var first = packs.get(id, false);
    packs.lock(first.get("namespace").toString());
    var batch = packs.get(id, true);
    own(batch);
    if (batch.get("status").equals("APPLIED")) return view(batch);
    if (!batch.get("status").equals("VALID")) throw new PlatformException(
      409,
      "INVALID_BATCH"
    );
    JsonNode req = packs.node(batch.get("request")),
      preview = packs.node(batch.get("preview"));
    String ns = req.path("namespace").asText();
    // Include existing parents and lock in a global order across namespaces and bulk edits.
    Set<UUID> lockIds = new TreeSet<>();
    for (JsonNode d : preview)
      if (d.has("contentId")) lockIds.add(
        UUID.fromString(d.path("contentId").asText())
      );
    for (JsonNode row : req.path("rows")) {
      if (row.hasNonNull("parentId")) lockIds.add(
        ContentValidation.uuid(row.path("parentId").asText())
      );
      else if (row.hasNonNull("parentExternalKey")) {
        var mapping = packs.mapping(ns, row.path("parentExternalKey").asText());
        if (mapping != null) lockIds.add((UUID) mapping.get("content_id"));
      }
    }
    for (UUID contentId : lockIds) policy.edit(records.get(contentId, true));
    // A stale preview never partially applies.
    for (JsonNode d : preview)
      if (d.has("contentId")) {
        var current = records.get(
          UUID.fromString(d.path("contentId").asText()),
          true
        );
        policy.edit(current);
        if (
          current.version() != d.path("expectedVersion").asLong()
        ) throw new PlatformException(409, "PACK_PREVIEW_STALE");
      }
    JsonNode current = decisions(req);
    if (
      !checksum(current).equals(checksum(preview))
    ) throw new PlatformException(409, "PACK_PREVIEW_STALE");
    ObjectNode result = json.createObjectNode();
    ArrayNode items = result.putArray("items");
    int created = 0,
      updated = 0,
      unchanged = 0,
      conflicts = 0;
    for (int i = 0; i < preview.size(); i++) {
      JsonNode row = req.path("rows").get(i),
        d = preview.get(i);
      String key = row.path("externalKey").asText(),
        action = d.path("action").asText();
      UUID contentId = d.has("contentId")
        ? UUID.fromString(d.path("contentId").asText())
        : null;
      if (action.equals("CONFLICT")) {
        packs.conflict(
          contentId,
          id,
          d.path("expectedVersion").asLong(),
          row.path("payload"),
          d.path("checksum").asText()
        );
        conflicts++;
      } else if (action.equals("UNCHANGED")) {
        unchanged++;
      } else {
        UUID parent = null;
        if (row.hasNonNull("parentId")) parent = ContentValidation.uuid(
          row.path("parentId").asText()
        );
        else if (row.hasNonNull("parentExternalKey")) parent =
          (UUID) Objects.requireNonNull(
            packs.mapping(ns, row.path("parentExternalKey").asText())
          ).get("content_id");
        ContentKind kind = ContentKind.valueOf(row.path("kind").asText());
        long revision;
        if (action.equals("CREATE")) {
          var c = content.create(
            new ContentDtos.Write(kind, parent, row.path("payload"), null)
          );
          contentId = c.id();
          revision = c.version();
          created++;
        } else if (action.equals("UPDATE_DRAFT")) {
          var c = content.update(
            contentId,
            new ContentDtos.Write(
              kind,
              parent,
              row.path("payload"),
              d.path("expectedVersion").asLong()
            )
          );
          revision = c.version();
          updated++;
        } else {
          revision = d.path("expectedVersion").asLong();
          unchanged++;
        }
        packs.map(
          ns,
          key,
          contentId,
          d.path("checksum").asText(),
          revision,
          row.path("sourceVersion").asText(req.path("packVersion").asText())
        );
      }
      items
        .addObject()
        .put("externalKey", key)
        .put("contentId", contentId.toString())
        .put("action", action);
    }
    result
      .put("created", created)
      .put("updated", updated)
      .put("unchanged", unchanged)
      .put("conflicts", conflicts);
    packs.complete(id, result);
    audit.record(id, "CONTENT_PACK", "CONFIRM", null);
    return get(id);
  }

  public JsonNode get(UUID id) {
    actor.editorOnly();
    var r = packs.get(id, false);
    own(r);
    return view(r);
  }

  public List<Map<String, Object>> history(int page) {
    actor.editorOnly();
    return packs.list(page);
  }

  public List<Map<String, Object>> conflicts(int page) {
    actor.editorOnly();
    return packs.conflicts(page);
  }

  public List<Map<String, Object>> mappings(String namespace, int page) {
    actor.editorOnly();
    return packs.mappings(namespace, page);
  }

  public Object candidate(UUID id) {
    actor.editorOnly();
    var r = packs.candidate(id, false);
    var current = records.get((UUID) r.get("content_id"), false);
    policy.edit(current);
    return Map.of(
      "id",
      id,
      "status",
      r.get("status"),
      "baseRevision",
      r.get("base_revision"),
      "current",
      ContentDtos.View.of(current),
      "incoming",
      packs.node(r.get("incoming_payload"))
    );
  }

  public record Resolution(String decision, long version) {}

  public Object resolve(UUID id, Resolution req) {
    actor.editorOnly();
    require(
      Set.of("KEEP_LOCAL", "USE_INCOMING_DRAFT").contains(
        Objects.toString(req.decision(), "")
      ),
      "INVALID_RESOLUTION"
    );
    var first = packs.candidate(id, false);
    var batch = packs.get((UUID) first.get("batch_id"), false);
    String ns = batch.get("namespace").toString();
    packs.lock(ns);
    var r = packs.candidate(id, true);
    var c = records.get((UUID) r.get("content_id"), true);
    policy.edit(c);
    if (
      !r.get("status").equals("CONFLICT") || c.version() != req.version()
    ) throw new PlatformException(409, "REVISION_CONFLICT");
    if (req.decision().equals("USE_INCOMING_DRAFT")) {
      var updated = content.update(
        c.id(),
        new ContentDtos.Write(
          c.kind(),
          c.parentId(),
          packs.node(r.get("incoming_payload")),
          c.version()
        )
      );
      var request = packs.node(batch.get("request"));
      for (JsonNode row : request.path("rows")) {
        var mapping = packs.mapping(ns, row.path("externalKey").asText());
        if (
          mapping != null && mapping.get("content_id").equals(c.id())
        ) packs.map(
          ns,
          row.path("externalKey").asText(),
          c.id(),
          r.get("checksum").toString(),
          updated.version(),
          row.path("sourceVersion").asText(batch.get("pack_version").toString())
        );
      }
    }
    packs.resolve(
      id,
      req.decision().equals("KEEP_LOCAL") ? "REJECTED" : "RESOLVED"
    );
    audit.record(id, "PACK_CONFLICT", req.decision(), c.version());
    audit.record(
      c.id(),
      c.kind().name(),
      "PACK_" + req.decision(),
      c.version() + (req.decision().equals("USE_INCOMING_DRAFT") ? 1 : 0)
    );
    return candidate(id);
  }

  private void own(Map<String, Object> r) {
    if (
      !actor.admin() && !actor.id().equals(r.get("created_by"))
    ) throw PlatformException.missing();
  }

  private JsonNode view(Map<String, Object> r) {
    ObjectNode out = json.createObjectNode();
    out
      .put("id", r.get("id").toString())
      .put("status", r.get("status").toString());
    out.set("preview", packs.node(r.get("preview")));
    out.set("result", packs.node(r.get("result")));
    return out;
  }

  public static String checksum(JsonNode value) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          canonical(value).getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String canonical(JsonNode n) {
    if (n.isObject()) {
      List<String> keys = new ArrayList<>();
      n.fieldNames().forEachRemaining(keys::add);
      Collections.sort(keys);
      return (
        "{" +
        String.join(
          ",",
          keys
            .stream()
            .map(k -> new TextNode(k).toString() + ":" + canonical(n.get(k)))
            .toList()
        ) +
        "}"
      );
    }
    if (n.isArray()) {
      List<String> values = new ArrayList<>();
      n.forEach(v -> values.add(canonical(v)));
      return "[" + String.join(",", values) + "]";
    }
    return n.toString();
  }
}
