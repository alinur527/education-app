package ent.kz.entbackend.platform.submissions;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class SubmissionFileController {

  private final SubmissionFileService files;
  private final SubmissionRevisionService revisions;

  public SubmissionFileController(
    SubmissionFileService files,
    SubmissionRevisionService revisions
  ) {
    this.files = files;
    this.revisions = revisions;
  }

  @PostMapping(
    value = "/api/assignments/{id}/files",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public Map<String, Object> upload(
    @PathVariable UUID id,
    @RequestParam UUID requestKey,
    @RequestParam MultipartFile file
  ) {
    return files.upload(id, requestKey, file);
  }

  @GetMapping("/api/assignments/{id}/files")
  public List<Map<String, Object>> list(@PathVariable UUID id) {
    return files.ownFiles(id);
  }

  @PostMapping("/api/submission-files/{id}/scan")
  public Map<String, Object> scan(@PathVariable UUID id) {
    return files.rescan(id);
  }

  @DeleteMapping("/api/submission-files/{id}")
  public Map<String, Boolean> delete(@PathVariable UUID id) {
    files.delete(id);
    return Map.of("saved", true);
  }

  @GetMapping("/api/submission-files/{id}/download")
  public ResponseEntity<byte[]> download(@PathVariable UUID id) {
    var file = files.download(id);
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(file.mime()))
      .header(
        "Content-Disposition",
        ContentDisposition.attachment()
          .filename(file.name(), StandardCharsets.UTF_8)
          .build()
          .toString()
      )
      .header("Cache-Control", "private, no-store")
      .header("X-Content-Type-Options", "nosniff")
      .body(file.bytes());
  }

  @GetMapping("/api/assignments/{id}/submission-history")
  public Object history(
    @PathVariable UUID id,
    @RequestParam(required = false) UUID userId,
    @RequestParam(defaultValue = "0") int page
  ) {
    return revisions.history(id, userId, page);
  }
}
