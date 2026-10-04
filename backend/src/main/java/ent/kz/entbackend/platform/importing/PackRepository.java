package ent.kz.entbackend.platform.importing;

import com.fasterxml.jackson.databind.JsonNode;
import ent.kz.entbackend.platform.PlatformException;
import ent.kz.entbackend.platform.content.ContentRepository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PackRepository {

  private final JdbcTemplate db;
  private final ContentRepository json;

  public PackRepository(JdbcTemplate db, ContentRepository json) {
    this.db = db;
    this.json = json;
  }

  public void lock(String namespace) {
    db.queryForList(
      "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
      "content-pack:" + namespace
    );
  }

  public Map<String, Object> mapping(String ns, String key) {
    var rows = db.queryForList(
      "SELECT * FROM content_external_refs WHERE namespace=? AND external_key=?",
      ns,
      key
    );
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public Map<String, Object> existing(String ns, String version, String key) {
    var rows = db.queryForList(
      "SELECT * FROM content_pack_batches WHERE namespace=? AND pack_version=? AND batch_key=?",
      ns,
      version,
      key
    );
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public UUID save(
    JsonNode req,
    UUID actor,
    String hash,
    JsonNode preview,
    boolean valid
  ) {
    UUID id = UUID.randomUUID();
    db.update(
      "INSERT INTO content_pack_batches(id,namespace,pack_version,batch_key,request_hash,created_by,request,preview,status) VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb,?)",
      id,
      req.path("namespace").asText(),
      req.path("packVersion").asText(),
      req.path("batchKey").asText(),
      hash,
      actor,
      req.toString(),
      preview.toString(),
      valid ? "VALID" : "INVALID"
    );
    return id;
  }

  public Map<String, Object> get(UUID id, boolean lock) {
    var rows = db.queryForList(
      "SELECT * FROM content_pack_batches WHERE id=?" +
        (lock ? " FOR UPDATE" : ""),
      id
    );
    if (rows.isEmpty()) throw PlatformException.missing();
    return rows.getFirst();
  }

  public JsonNode node(Object value) {
    return value == null ? null : json.parse(value.toString());
  }

  public Map<String, Object> candidate(UUID id, boolean lock) {
    var rows = db.queryForList(
      "SELECT * FROM content_update_candidates WHERE id=?" +
        (lock ? " FOR UPDATE" : ""),
      id
    );
    if (rows.isEmpty()) throw PlatformException.missing();
    return rows.getFirst();
  }

  public void resolve(UUID id, String status) {
    db.update(
      "UPDATE content_update_candidates SET status=? WHERE id=?",
      status,
      id
    );
  }

  public List<Map<String, Object>> mappings(String namespace, int page) {
    return db.queryForList(
      "SELECT external_key AS \"externalKey\",content_id AS \"contentId\",applied_checksum AS checksum,source_version AS \"sourceVersion\" FROM content_external_refs WHERE namespace=? ORDER BY external_key LIMIT 100 OFFSET ?",
      namespace,
      Math.clamp(page, 0, 100000) * 100
    );
  }

  public void map(
    String ns,
    String key,
    UUID content,
    String checksum,
    long version,
    String sourceVersion
  ) {
    db.update(
      "INSERT INTO content_external_refs(namespace,external_key,content_id,applied_checksum,applied_revision,source_version) VALUES (?,?,?,?,?,?) ON CONFLICT(namespace,external_key) DO UPDATE SET applied_checksum=excluded.applied_checksum,applied_revision=excluded.applied_revision,source_version=excluded.source_version",
      ns,
      key,
      content,
      checksum,
      version,
      sourceVersion
    );
  }

  public void conflict(
    UUID content,
    UUID batch,
    long revision,
    JsonNode incoming,
    String checksum
  ) {
    db.update(
      "INSERT INTO content_update_candidates(content_id,batch_id,base_revision,incoming_payload,checksum) VALUES (?,?,?,?::jsonb,?) ON CONFLICT(batch_id,content_id) DO NOTHING",
      content,
      batch,
      revision,
      incoming.toString(),
      checksum
    );
  }

  public void complete(UUID id, JsonNode result) {
    db.update(
      "UPDATE content_pack_batches SET status='APPLIED',result=?::jsonb,applied_at=now() WHERE id=?",
      result.toString(),
      id
    );
  }

  public List<Map<String, Object>> list(int page) {
    return db.queryForList(
      "SELECT id,namespace,pack_version AS \"packVersion\",batch_key AS \"batchKey\",status,created_at AS \"createdAt\",applied_at AS \"appliedAt\" FROM content_pack_batches ORDER BY created_at DESC LIMIT 25 OFFSET ?",
      Math.clamp(page, 0, 100000) * 25
    );
  }

  public List<Map<String, Object>> conflicts(int page) {
    return db.queryForList(
      "SELECT id,content_id AS \"contentId\",batch_id AS \"batchId\",base_revision AS \"baseRevision\",status,created_at AS \"createdAt\" FROM content_update_candidates WHERE status='CONFLICT' ORDER BY created_at DESC LIMIT 25 OFFSET ?",
      Math.clamp(page, 0, 100000) * 25
    );
  }
}
