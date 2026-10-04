package ent.kz.entbackend.platform.content;

import com.fasterxml.jackson.databind.*;
import ent.kz.entbackend.platform.PlatformException;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class ContentRepository {

  private final JdbcTemplate db;
  private final ObjectMapper json;

  public ContentRepository(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public JsonNode parse(String value) {
    try {
      return value == null ? null : json.readTree(value);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid stored JSON", e);
    }
  }

  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot serialize", e);
    }
  }

  private ContentRecord map(ResultSet r, int row) throws SQLException {
    return new ContentRecord(
      r.getObject("id", UUID.class),
      ContentKind.valueOf(r.getString("kind")),
      r.getObject("parent_id", UUID.class),
      r.getObject("owner_id", UUID.class),
      r.getString("title_ru"),
      r.getString("title_kz"),
      parse(r.getString("payload")),
      parse(r.getString("published_payload")),
      r.getString("status"),
      r.getLong("version"),
      r.getObject("published_version", Long.class),
      r.getObject("created_by", UUID.class),
      r.getObject("updated_by", UUID.class),
      r.getObject("created_at", OffsetDateTime.class),
      r.getObject("updated_at", OffsetDateTime.class)
    );
  }

  public ContentRecord get(UUID id, boolean lock) {
    return db
      .query(
        "SELECT * FROM content_records WHERE id=?" +
          (lock ? " FOR UPDATE" : ""),
        this::map,
        id
      )
      .stream()
      .findFirst()
      .orElseThrow(PlatformException::missing);
  }

  public List<ContentRecord> children(UUID id) {
    return db.query(
      "SELECT * FROM content_records WHERE parent_id=? ORDER BY coalesce((published_payload->>'sortOrder')::int,0),created_at,id",
      this::map,
      id
    );
  }

  public ContentDtos.Page<ContentDtos.Summary> list(
    String kind,
    String status,
    String query,
    UUID parentId,
    boolean missingTranslation,
    UUID owner,
    int page,
    int size
  ) {
    String where =
      " WHERE (?='' OR kind=?) AND (?='' OR status=?) AND (title_ru ILIKE ? OR title_kz ILIKE ?) AND (?::uuid IS NULL OR parent_id=?::uuid) AND (NOT ? OR btrim(coalesce(title_kz,''))='') AND (?::uuid IS NULL OR (owner_id=?::uuid AND kind NOT IN ('SUBJECT','TOPIC','THEORY','QUESTION','CONTEXT')))";
    Object[] values = {
      kind,
      kind,
      status,
      status,
      "%" + query + "%",
      "%" + query + "%",
      parentId,
      parentId,
      missingTranslation,
      owner,
      owner,
    };
    Long total = db.queryForObject(
      "SELECT count(*) FROM content_records" + where,
      Long.class,
      values
    );
    var args = new ArrayList<>(Arrays.asList(values));
    args.add(size);
    args.add(page * size);
    var items = db.query(
      "SELECT id,kind,parent_id,owner_id,title_ru,title_kz,status,version,published_version,created_by,updated_by,created_at,updated_at FROM content_records" +
        where +
        " ORDER BY updated_at DESC,id LIMIT ? OFFSET ?",
      (r, n) ->
        new ContentDtos.Summary(
          r.getObject("id", UUID.class),
          ContentKind.valueOf(r.getString("kind")),
          r.getObject("parent_id", UUID.class),
          r.getObject("owner_id", UUID.class),
          r.getString("title_ru"),
          r.getString("title_kz"),
          r.getString("status"),
          r.getLong("version"),
          r.getObject("published_version", Long.class),
          r.getObject("created_by", UUID.class),
          r.getObject("updated_by", UUID.class),
          r.getObject("created_at", OffsetDateTime.class),
          r.getObject("updated_at", OffsetDateTime.class)
        ),
      args.toArray()
    );
    return new ContentDtos.Page<>(items, page, size, total == null ? 0 : total);
  }

  public void insert(
    UUID id,
    ContentKind kind,
    UUID parent,
    UUID owner,
    JsonNode payload,
    UUID actor
  ) {
    db.update(
      "INSERT INTO content_records(id,kind,parent_id,owner_id,title_ru,title_kz,payload,created_by,updated_by) VALUES (?,?,?,?,?,?,?::jsonb,?,?)",
      id,
      kind.name(),
      parent,
      owner,
      payload
        .path("titleRu")
        .asText()
        .substring(0, Math.min(300, payload.path("titleRu").asText().length())),
      payload
        .path("titleKz")
        .asText()
        .substring(0, Math.min(300, payload.path("titleKz").asText().length())),
      payload.toString(),
      actor,
      actor
    );
  }

  public void save(UUID id, JsonNode payload, UUID actor) {
    db.update(
      "UPDATE content_records SET payload=?::jsonb,title_ru=?,title_kz=?,status='DRAFT',version=version+1,updated_by=?,updated_at=now() WHERE id=?",
      payload.toString(),
      payload
        .path("titleRu")
        .asText()
        .substring(0, Math.min(300, payload.path("titleRu").asText().length())),
      payload
        .path("titleKz")
        .asText()
        .substring(0, Math.min(300, payload.path("titleKz").asText().length())),
      actor,
      id
    );
  }

  public void transition(UUID id, String status, UUID actor) {
    db.update(
      "UPDATE content_records SET status=?,published_payload=CASE WHEN ?='PUBLISHED' THEN payload WHEN ?='ARCHIVED' THEN NULL ELSE published_payload END,published_version=CASE WHEN ?='PUBLISHED' THEN version+1 WHEN ?='ARCHIVED' THEN NULL ELSE published_version END,version=version+1,updated_by=?,updated_at=now() WHERE id=?",
      status,
      status,
      status,
      status,
      status,
      actor,
      id
    );
  }
}
