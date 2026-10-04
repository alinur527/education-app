package ent.kz.entbackend.platform.offline;

import static ent.kz.entbackend.platform.PlatformException.require;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.content.*;
import ent.kz.entbackend.platform.materials.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit export of public, reusable ENT content. Never exports course or student data. */
@Service
@Transactional(readOnly = true)
public class OfflineService {

  @Value("${app.scan.required:false}")
  private boolean scanRequired;

  private final ContentRepository records;
  private final ContentPolicy policy;
  private final Actor actor;
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final MaterialService materials;
  private final AuditService audit;

  public OfflineService(
    ContentRepository records,
    ContentPolicy policy,
    Actor actor,
    JdbcTemplate db,
    ObjectMapper json,
    MaterialService materials,
    AuditService audit
  ) {
    this.records = records;
    this.policy = policy;
    this.actor = actor;
    this.db = db;
    this.json = json;
    this.materials = materials;
    this.audit = audit;
  }

  private ContentRecord publicTheory(UUID id) {
    var c = records.get(id, false);
    if (
      c.kind() != ContentKind.THEORY ||
      !policy.visible(c) ||
      policy.course(c) != null ||
      !c.publishedPayload().path("offlineAllowed").asBoolean(false)
    ) throw PlatformException.missing();
    return c;
  }

  public JsonNode export(UUID id) {
    var c = publicTheory(id);
    ObjectNode out = json
      .createObjectNode()
      .put("id", id.toString())
      .put("version", c.publishedVersion())
      .put("policy", "PUBLIC_THEORY_V1");
    ObjectNode payload = out.putObject("content");
    for (String key : List.of(
      "titleRu",
      "titleKz",
      "contentRu",
      "contentKz",
      "descriptionRu",
      "descriptionKz"
    ))
      if (c.publishedPayload().hasNonNull(key)) payload.set(
        key,
        c.publishedPayload().get(key)
      );
    var files = db.queryForList(
      "SELECT id,title_ru AS \"titleRu\",title_kz AS \"titleKz\",original_file_name AS name,mime_type AS mime,size FROM materials WHERE content_id=? AND published AND offline_allowed AND (scan_status IN ('CLEAN','UNSCANNED_LEGACY') OR (scan_status='UNSCANNED' AND NOT ?)) ORDER BY id LIMIT 20",
      id,
      scanRequired
    );
    Set<String> allowedFiles = new HashSet<>();
    files.forEach(f -> allowedFiles.add(f.get("id").toString()));
    ArrayNode blocks = payload.putArray("blocks");
    for (JsonNode block : c.publishedPayload().path("blocks")) {
      String type = block.path("type").asText();
      if (
        Set.of("FILE", "IMAGE").contains(type) &&
        !allowedFiles.contains(block.path("materialId").asText())
      ) continue;
      if (
        !Set.of(
          "TEXT",
          "HEADING",
          "QUOTE",
          "FORMULA",
          "CALLOUT",
          "CODE",
          "TABLE",
          "FILE",
          "IMAGE",
          "VIDEO"
        ).contains(type)
      ) continue;
      ObjectNode b = blocks.addObject();
      for (String key : List.of(
        "type",
        "textRu",
        "textKz",
        "materialId",
        "url"
      ))
        if (block.hasNonNull(key)) b.set(key, block.get(key));
    }
    out.set("files", json.valueToTree(files));
    return out;
  }

  public MaterialService.Download file(UUID id) {
    var rows = db.queryForList(
      "SELECT content_id FROM materials WHERE id=? AND published AND offline_allowed AND scan_status IN ('CLEAN','UNSCANNED','UNSCANNED_LEGACY')",
      id
    );
    if (rows.isEmpty()) throw PlatformException.missing();
    publicTheory((UUID) rows.getFirst().get("content_id"));
    return materials.download(id);
  }

  public record Rights(boolean allowed, String basis) {}

  @Transactional
  public Object rights(UUID id, Rights req) {
    actor.editorOnly();
    var rows = db.queryForList(
      "SELECT content_id FROM materials WHERE id=? FOR UPDATE",
      id
    );
    if (rows.isEmpty()) throw PlatformException.missing();
    var c = records.get((UUID) rows.getFirst().get("content_id"), false);
    policy.edit(c);
    require(
      c.kind() == ContentKind.THEORY && policy.course(c) == null,
      "OFFLINE_PUBLIC_THEORY_ONLY"
    );
    require(
      req.basis() != null &&
        req.basis().length() <= 1000 &&
        (!req.allowed() || !req.basis().isBlank()),
      "RIGHTS_BASIS_REQUIRED"
    );
    db.update(
      "UPDATE materials SET offline_allowed=?,offline_rights_basis=? WHERE id=?",
      req.allowed(),
      req.basis(),
      id
    );
    audit.record(id, "MATERIAL", "OFFLINE_RIGHTS", null);
    return Map.of("saved", true);
  }
}
