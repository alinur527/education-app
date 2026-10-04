package ent.kz.entbackend.platform.materials;

import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.content.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MaterialService {

  public record Download(byte[] bytes, String name, String mime) {}

  private final JdbcTemplate db;
  private final Actor actor;
  private final ContentRepository records;
  private final ContentPolicy policy;
  private final FileValidation validation;
  private final StorageService storage;
  private final MalwareScanner scanner;
  private final AuditService audit;

  public MaterialService(
    JdbcTemplate db,
    Actor actor,
    ContentRepository records,
    ContentPolicy policy,
    FileValidation validation,
    StorageService storage,
    MalwareScanner scanner,
    AuditService audit
  ) {
    this.db = db;
    this.actor = actor;
    this.records = records;
    this.policy = policy;
    this.validation = validation;
    this.storage = storage;
    this.scanner = scanner;
    this.audit = audit;
  }

  @Transactional
  public Map<String, Object> upload(
    UUID parent,
    String titleRu,
    String titleKz,
    MultipartFile file
  ) {
    ContentRecord c = records.get(parent, true);
    policy.edit(c);
    PlatformException.require(
      Set.of(
        ContentKind.TOPIC,
        ContentKind.THEORY,
        ContentKind.COURSE,
        ContentKind.LESSON,
        ContentKind.ASSIGNMENT
      ).contains(c.kind()),
      "MATERIAL_PARENT"
    );
    PlatformException.require(
      titleRu != null &&
        !titleRu.isBlank() &&
        titleRu.length() <= 300 &&
        titleKz != null &&
        titleKz.length() <= 300,
      "INVALID_TITLE"
    );
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (Exception e) {
      throw new PlatformException(400, "UPLOAD_FAILED");
    }
    String mime = validation.validate(
      file.getOriginalFilename(),
      file.getContentType(),
      bytes
    );
    scanner.scan(bytes, mime);
    UUID id = UUID.randomUUID();
    String key = UUID.randomUUID().toString();
    storage.put(key, bytes, mime);
    TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          if (status != STATUS_COMMITTED) storage.delete(key);
        }
      }
    );
    db.update(
      "INSERT INTO materials(id,content_id,title_ru,title_kz,storage_key,original_file_name,mime_type,size,created_by) VALUES (?,?,?,?,?,?,?,?,?)",
      id,
      parent,
      titleRu,
      titleKz,
      key,
      file.getOriginalFilename(),
      mime,
      bytes.length,
      actor.id()
    );
    audit.record(id, "MATERIAL", "UPLOAD", null);
    return metadata(id);
  }

  private Map<String, Object> metadata(UUID id) {
    return db.queryForMap(
      "SELECT id,content_id AS \"contentId\",title_ru AS \"titleRu\",title_kz AS \"titleKz\",original_file_name AS \"originalFileName\",mime_type AS \"mimeType\",size,published FROM materials WHERE id=?",
      id
    );
  }

  public List<Map<String, Object>> list(UUID parent) {
    ContentRecord c = records.get(parent, false);
    policy.read(c);
    if (!materialAccess(c)) return List.of();
    return db.queryForList(
      "SELECT id,content_id AS \"contentId\",title_ru AS \"titleRu\",title_kz AS \"titleKz\",original_file_name AS \"originalFileName\",mime_type AS \"mimeType\",size,published FROM materials WHERE content_id=? AND (? OR published) ORDER BY created_at LIMIT 100",
      parent,
      policy.canEdit(c)
    );
  }

  public Download download(UUID id) {
    var rows = db.queryForList("SELECT * FROM materials WHERE id=?", id);
    if (rows.isEmpty()) throw PlatformException.missing();
    var m = rows.getFirst();
    ContentRecord c = records.get((UUID) m.get("content_id"), false);
    policy.read(c);
    if (!materialAccess(c)) throw PlatformException.missing();
    if (
      !policy.canEdit(c) && !Boolean.TRUE.equals(m.get("published"))
    ) throw PlatformException.missing();
    return new Download(
      storage.get((String) m.get("storage_key")),
      (String) m.get("original_file_name"),
      (String) m.get("mime_type")
    );
  }

  private boolean materialAccess(ContentRecord content) {
    UUID course = policy.course(content);
    return policy.canEdit(content) || course == null || policy.enrolled(course);
  }
}
