package ent.kz.entbackend.platform.materials;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MaterialController {

  private final MaterialService service;

  public MaterialController(MaterialService service) {
    this.service = service;
  }

  @PostMapping(
    value = "/api/cms/materials",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public Map<String, Object> upload(
    @RequestParam UUID contentId,
    @RequestParam String titleRu,
    @RequestParam(defaultValue = "") String titleKz,
    @RequestParam MultipartFile file
  ) {
    return service.upload(contentId, titleRu, titleKz, file);
  }

  @GetMapping("/api/content/{id}/materials")
  public List<Map<String, Object>> list(@PathVariable UUID id) {
    return service.list(id);
  }

  @GetMapping("/api/materials/{id}/download")
  public ResponseEntity<byte[]> download(@PathVariable UUID id) {
    var file = service.download(id);
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
}
