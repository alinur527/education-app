package ent.kz.entbackend.platform.importing;

import static ent.kz.entbackend.platform.PlatformException.require;

import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.content.*;
import ent.kz.entbackend.platform.materials.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class MaterialPackService {

  private final JdbcTemplate db;
  private final Actor actor;
  private final ContentRepository records;
  private final ContentPolicy policy;
  private final MaterialService materials;

  public MaterialPackService(
    JdbcTemplate db,
    Actor actor,
    ContentRepository records,
    ContentPolicy policy,
    MaterialService materials
  ) {
    this.db = db;
    this.actor = actor;
    this.records = records;
    this.policy = policy;
    this.materials = materials;
  }

  public Object upload(
    String namespace,
    String key,
    UUID parent,
    String titleRu,
    String titleKz,
    MultipartFile file
  ) {
    actor.editorOnly();
    policy.edit(records.get(parent, false));
    require(
      namespace.matches("[A-Za-z0-9_.-]{1,100}") &&
        key.matches("[A-Za-z0-9_.:-]{1,160}"),
      "MATERIAL_PACK_KEY"
    );
    require(
      file.getSize() > 0 && file.getSize() <= 20 * 1024 * 1024,
      "FILE_TOO_LARGE"
    );
    String sha;
    try {
      sha = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(file.getBytes())
      );
    } catch (Exception e) {
      throw new PlatformException(400, "UPLOAD_FAILED");
    }
    db.queryForList(
      "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
      "material-pack:" + namespace + ":" + key
    );
    var prior = db.queryForList(
      "SELECT m.id,m.content_id,r.sha256 FROM material_external_refs r JOIN materials m ON m.id=r.material_id WHERE r.namespace=? AND r.external_key=?",
      namespace,
      key
    );
    if (!prior.isEmpty()) {
      var old = prior.getFirst();
      if (
        !old.get("content_id").equals(parent) || !old.get("sha256").equals(sha)
      ) throw new PlatformException(
        409,
        "MATERIAL_KEY_CONFLICT_USE_NEW_VERSION"
      );
      return Map.of("id", old.get("id"), "sha256", sha, "action", "UNCHANGED");
    }
    var saved = materials.upload(parent, titleRu, titleKz, file);
    UUID id = (UUID) saved.get("id");
    db.update(
      "INSERT INTO material_external_refs(namespace,external_key,sha256,material_id) VALUES (?,?,?,?)",
      namespace,
      key,
      sha,
      id
    );
    return Map.of("id", id, "sha256", sha, "action", "CREATED");
  }
}
