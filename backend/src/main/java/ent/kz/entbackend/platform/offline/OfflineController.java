package ent.kz.entbackend.platform.offline;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class OfflineController {

  private final OfflineService service;

  public OfflineController(OfflineService service) {
    this.service = service;
  }

  @GetMapping("/api/offline/theories/{id}")
  public Object theory(@PathVariable UUID id) {
    return service.export(id);
  }

  @GetMapping("/api/offline/materials/{id}")
  public ResponseEntity<byte[]> file(@PathVariable UUID id) {
    var f = service.file(id);
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(f.mime()))
      .header(
        "Content-Disposition",
        ContentDisposition.attachment()
          .filename(f.name(), StandardCharsets.UTF_8)
          .build()
          .toString()
      )
      .header("Cache-Control", "private, no-store")
      .header("X-Content-Type-Options", "nosniff")
      .body(f.bytes());
  }

  @PostMapping("/api/cms/materials/{id}/offline-rights")
  public Object rights(
    @PathVariable UUID id,
    @RequestBody OfflineService.Rights request
  ) {
    return service.rights(id, request);
  }
}
