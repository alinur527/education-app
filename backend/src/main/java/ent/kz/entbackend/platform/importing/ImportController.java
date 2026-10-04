package ent.kz.entbackend.platform.importing;

import ent.kz.entbackend.platform.content.ContentDtos.Page;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/cms/imports")
public class ImportController {

  private final ImportService service;

  public ImportController(ImportService service) {
    this.service = service;
  }

  @PostMapping(consumes = "multipart/form-data")
  public ImportService.Preview preview(@RequestParam MultipartFile file) {
    return service.preview(file);
  }

  @GetMapping
  public Page<Map<String, Object>> history(
    @RequestParam(defaultValue = "0") int page
  ) {
    return service.history(page);
  }

  @GetMapping("/{id}")
  public ImportService.Preview get(@PathVariable UUID id) {
    return service.get(id, false);
  }

  @PostMapping("/{id}/confirm")
  public ImportService.Preview confirm(@PathVariable UUID id) {
    return service.confirm(id);
  }
}
