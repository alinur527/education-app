package ent.kz.entbackend.platform.submissions;

import ent.kz.entbackend.platform.materials.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionFileScanning {

  private final SubmissionFileRepository repo;
  private final StorageService storage;
  private final MalwareScanner scanner;

  public SubmissionFileScanning(
    SubmissionFileRepository repo,
    StorageService storage,
    MalwareScanner scanner
  ) {
    this.repo = repo;
    this.storage = storage;
    this.scanner = scanner;
  }

  @Transactional
  public Map<String, Object> scan(UUID id, boolean manual) {
    var row = repo.get(id, true);
    String status = row.get("scan_status").toString();
    if (
      !manual && (status.equals("CLEAN") || status.equals("INFECTED"))
    ) return repo.metadata(id);
    var scanned = row.get("scanned_at");
    if (
      scanned != null &&
      ((java.sql.Timestamp) scanned)
        .toInstant()
        .isAfter(Instant.now().minusSeconds(30))
    ) return repo.metadata(id);
    MalwareScanner.Result result;
    try {
      byte[] bytes = storage.get(row.get("storage_key").toString());
      result = FileDigests.sha256(bytes).equals(row.get("sha256"))
        ? scanner.scan(bytes, row.get("mime_type").toString())
        : new MalwareScanner.Result(
            MalwareScanner.Status.SCAN_FAILED,
            "integrity",
            "HASH_MISMATCH"
          );
    } catch (RuntimeException ex) {
      result = new MalwareScanner.Result(
        MalwareScanner.Status.SCAN_FAILED,
        "storage",
        "SCAN_UNAVAILABLE"
      );
    }
    int attempts = ((Number) row.get("scan_attempts")).intValue() + 1;
    repo
      .jdbc()
      .update(
        "UPDATE submission_files SET scan_status=?,scan_engine=?,scan_message=?,scan_attempts=?,scanned_at=now(),next_scan_at=? WHERE id=?",
        result.status().name(),
        result.engine(),
        result.message(),
        attempts,
        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(
          Math.min(60, 1L << Math.min(attempts, 5))
        ),
        id
      );
    return repo.metadata(id);
  }
}
