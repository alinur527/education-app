package ent.kz.entbackend.platform.materials;

import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.content.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
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
  private final boolean scanRequired;

  public MaterialService(
    JdbcTemplate db,
    Actor actor,
    ContentRepository records,
    ContentPolicy policy,
    FileValidation validation,
    StorageService storage,
    MalwareScanner scanner,
    AuditService audit,
    @Value("${app.scan.required:false}") boolean scanRequired
  ) {
    this.db = db;
    this.actor = actor;
    this.records = records;
    this.policy = policy;
    this.validation = validation;
    this.storage = storage;
    this.scanner = scanner;
    this.audit = audit;
    this.scanRequired = scanRequired;
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
    var scan = scanner.scan(bytes, mime);
    if (
      scan.status() == MalwareScanner.Status.INFECTED
    ) throw new PlatformException(400, "MALWARE_DETECTED");
    if (
      scan.status() == MalwareScanner.Status.SCAN_FAILED ||
      (scanRequired && scan.status() != MalwareScanner.Status.CLEAN)
    ) throw new PlatformException(503, "SCANNER_UNAVAILABLE");
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
      "INSERT INTO materials(id,content_id,title_ru,title_kz,storage_key,original_file_name,mime_type,size,created_by,scan_status,sha256,scanned_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,CASE WHEN ? THEN now() ELSE NULL END)",
      id,
      parent,
      titleRu,
      titleKz,
      key,
      file.getOriginalFilename(),
      mime,
      bytes.length,
      actor.id(),
      scan.status().name(),
      FileDigests.sha256(bytes),
      scan.status() == MalwareScanner.Status.CLEAN
    );
    audit.record(id, "MATERIAL", "UPLOAD", null);
    return metadata(id);
  }

  private Map<String, Object> metadata(UUID id) {
    return db.queryForMap(
      "SELECT id,content_id AS \"contentId\",title_ru AS \"titleRu\",title_kz AS \"titleKz\",original_file_name AS \"originalFileName\",mime_type AS \"mimeType\",size,published,scan_status AS \"scanStatus\" FROM materials WHERE id=?",
      id
    );
  }

  public List<Map<String, Object>> list(UUID parent) {
    ContentRecord c = records.get(parent, false);
    policy.read(c);
    if (!materialAccess(c)) return List.of();
    return db.queryForList(
      "SELECT id,content_id AS \"contentId\",title_ru AS \"titleRu\",title_kz AS \"titleKz\",original_file_name AS \"originalFileName\",mime_type AS \"mimeType\",size,published,scan_status AS \"scanStatus\" FROM materials WHERE content_id=? AND (? OR published) ORDER BY created_at LIMIT 100",
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
    String scanStatus = m.get("scan_status").toString();
    if (
      !scanStatus.equals("CLEAN") &&
      !scanStatus.equals("UNSCANNED_LEGACY") &&
      (!scanStatus.equals("UNSCANNED") || scanRequired)
    ) throw new PlatformException(409, "FILE_NOT_CLEAN");
    byte[] bytes = storage.get((String) m.get("storage_key"));
    if (
      m.get("sha256") != null &&
      !m.get("sha256").equals(FileDigests.sha256(bytes))
    ) throw new PlatformException(409, "FILE_INTEGRITY_ERROR");
    return new Download(
      bytes,
      (String) m.get("original_file_name"),
      (String) m.get("mime_type")
    );
  }

  public ContentDtos.Page<Map<String, Object>> library(String q, int page) {
    actor.staffOnly();
    PlatformException.require(
      q != null && q.length() <= 200 && page >= 0 && page <= 100000,
      "INVALID_FILTER"
    );
    String where =
      " FROM materials m JOIN content_records c ON c.id=m.content_id WHERE (? OR (c.owner_id=? AND c.kind IN ('COURSE','MODULE','LESSON','ASSIGNMENT','QUIZ'))) AND (lower(m.title_ru||' '||coalesce(m.title_kz,'')||' '||m.original_file_name||' '||c.title_ru||' '||c.title_kz) LIKE ?)";
    Object[] args = {
      actor.editor(),
      actor.id(),
      "%" + q.toLowerCase(Locale.ROOT) + "%",
    };
    long total = db.queryForObject("SELECT count(*)" + where, Long.class, args);
    List<Object> paged = new ArrayList<>(Arrays.asList(args));
    paged.add(page * 25);
    var items = db.queryForList(
      "SELECT m.id,m.content_id AS \"contentId\",m.title_ru AS \"titleRu\",m.title_kz AS \"titleKz\",m.original_file_name AS \"originalFileName\",m.mime_type AS \"mimeType\",m.size,m.published,m.scan_status AS \"scanStatus\",c.title_ru AS \"contentTitleRu\",c.title_kz AS \"contentTitleKz\",c.kind AS \"contentKind\"" +
        where +
        " ORDER BY m.created_at DESC,m.id LIMIT 25 OFFSET ?",
      paged.toArray()
    );
    return new ContentDtos.Page<>(items, page, 25, total);
  }

  private boolean materialAccess(ContentRecord content) {
    UUID course = policy.course(content);
    return policy.canEdit(content) || course == null || policy.enrolled(course);
  }
}
