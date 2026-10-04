package ent.kz.entbackend.platform.submissions;

import static ent.kz.entbackend.platform.PlatformException.require;

import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.materials.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class SubmissionFileService {

  private final SubmissionFileRepository repo;
  private final SubmissionAccess access;
  private final Actor actor;
  private final FileValidation validation;
  private final StorageService storage;
  private final SubmissionFileScanning scanning;
  private final long totalLimit, dailyLimit;

  public SubmissionFileService(
    SubmissionFileRepository repo,
    SubmissionAccess access,
    Actor actor,
    FileValidation validation,
    StorageService storage,
    SubmissionFileScanning scanning,
    @Value("${app.submission-files.total-bytes:524288000}") long totalLimit,
    @Value("${app.submission-files.daily-bytes:209715200}") long dailyLimit
  ) {
    this.repo = repo;
    this.access = access;
    this.actor = actor;
    this.validation = validation;
    this.storage = storage;
    this.scanning = scanning;
    this.totalLimit = totalLimit;
    this.dailyLimit = dailyLimit;
  }

  public Map<String, Object> upload(
    UUID assignment,
    UUID requestKey,
    MultipartFile file
  ) {
    access.upload(assignment);
    UUID user = actor.id();
    repo.lockUser(user);
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
      ),
      hash = FileDigests.sha256(bytes);
    var previous = repo
      .jdbc()
      .queryForList(
        "SELECT * FROM submission_files WHERE user_id=? AND request_key=?",
        user,
        requestKey
      );
    if (!previous.isEmpty()) {
      var row = previous.getFirst();
      if (
        row.get("deleted_at") != null ||
        !row.get("sha256").equals(hash) ||
        !row.get("assignment_id").equals(assignment) ||
        !row.get("original_file_name").equals(file.getOriginalFilename()) ||
        !row.get("mime_type").equals(mime)
      ) throw new PlatformException(409, "REQUEST_KEY_REUSED");
      return repo.metadata((UUID) row.get("id"));
    }
    var quota = repo
      .jdbc()
      .queryForMap(
        "SELECT coalesce(sum(size) FILTER(WHERE deleted_at IS NULL),0) AS total,coalesce(sum(size) FILTER(WHERE created_at>now()-interval '1 day'),0) AS daily,count(*) FILTER(WHERE created_at>now()-interval '1 day') AS count FROM submission_files WHERE user_id=?",
        user
      );
    if (
      ((Number) quota.get("total")).longValue() + bytes.length > totalLimit ||
      ((Number) quota.get("daily")).longValue() + bytes.length > dailyLimit ||
      ((Number) quota.get("count")).longValue() >= 50
    ) throw new PlatformException(429, "UPLOAD_QUOTA");
    long pending = repo
      .jdbc()
      .queryForObject(
        "SELECT count(*) FROM submission_files f WHERE user_id=? AND deleted_at IS NULL AND NOT EXISTS(SELECT 1 FROM submission_revision_files r WHERE r.file_id=f.id)",
        Long.class,
        user
      );
    if (pending >= 10) throw new PlatformException(429, "STAGED_FILE_LIMIT");
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
    repo
      .jdbc()
      .update(
        "INSERT INTO submission_files(id,user_id,assignment_id,request_key,storage_key,original_file_name,mime_type,size,sha256,scan_status) VALUES (?,?,?,?,?,?,?,?,?,'PENDING')",
        id,
        user,
        assignment,
        requestKey,
        key,
        file.getOriginalFilename(),
        mime,
        bytes.length,
        hash
      );
    return scanning.scan(id, false);
  }

  public List<Map<String, Object>> ownFiles(UUID assignment) {
    require(
      actor.user().getRole() == ent.kz.entbackend.entity.UserRole.STUDENT,
      "STUDENT_REQUIRED"
    );
    return repo
      .jdbc()
      .queryForList(
        "SELECT f.id FROM submission_files f WHERE user_id=? AND assignment_id=? AND deleted_at IS NULL AND (NOT EXISTS(SELECT 1 FROM submission_revision_files r WHERE r.file_id=f.id) OR EXISTS(SELECT 1 FROM submission_revision_files r JOIN assignment_submissions s ON s.latest_submission_id=r.submission_id WHERE r.file_id=f.id)) ORDER BY created_at DESC LIMIT 100",
        actor.id(),
        assignment
      )
      .stream()
      .map(r -> repo.metadata((UUID) r.get("id")))
      .toList();
  }

  public Map<String, Object> rescan(UUID id) {
    var row = repo.get(id, false);
    canRead(row);
    return scanning.scan(id, true);
  }

  public MaterialService.Download download(UUID id) {
    var row = repo.get(id, false);
    canRead(row);
    if (!row.get("scan_status").equals("CLEAN")) throw new PlatformException(
      409,
      "FILE_NOT_CLEAN"
    );
    byte[] bytes = storage.get(row.get("storage_key").toString());
    if (
      !FileDigests.sha256(bytes).equals(row.get("sha256"))
    ) throw new PlatformException(409, "FILE_INTEGRITY_ERROR");
    return new MaterialService.Download(
      bytes,
      row.get("original_file_name").toString(),
      row.get("mime_type").toString()
    );
  }

  public void delete(UUID id) {
    var initial = repo.get(id, false);
    access.own((UUID) initial.get("user_id"));
    repo.lockUser(actor.id());
    var row = repo.get(id, true);
    if (
      Boolean.TRUE.equals(
        repo
          .jdbc()
          .queryForObject(
            "SELECT EXISTS(SELECT 1 FROM submission_revision_files WHERE file_id=?)",
            Boolean.class,
            id
          )
      )
    ) throw new PlatformException(409, "FILE_BOUND_TO_REVISION");
    repo
      .jdbc()
      .update("UPDATE submission_files SET deleted_at=now() WHERE id=?", id);
    String key = row.get("storage_key").toString();
    TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          storage.delete(key);
        }
      }
    );
  }

  private void canRead(Map<String, Object> row) {
    access.read((UUID) row.get("assignment_id"), (UUID) row.get("user_id"));
    if (
      !actor.admin() &&
      !actor.id().equals(row.get("user_id")) &&
      !Boolean.TRUE.equals(
        repo
          .jdbc()
          .queryForObject(
            "SELECT EXISTS(SELECT 1 FROM submission_revision_files WHERE file_id=?)",
            Boolean.class,
            row.get("id")
          )
      )
    ) throw PlatformException.missing();
  }
}
