package ent.kz.entbackend.platform.content;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cms/content")
public class ContentController {

  private final ContentService service;

  public ContentController(ContentService service) {
    this.service = service;
  }

  @GetMapping
  public ContentDtos.Page<ContentDtos.Summary> list(
    @RequestParam(defaultValue = "") String kind,
    @RequestParam(defaultValue = "") String status,
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "25") int size
  ) {
    return service.list(kind, status, q, page, size);
  }

  @GetMapping("/{id}")
  public ContentDtos.View get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  public ContentDtos.View create(@Valid @RequestBody ContentDtos.Write req) {
    return service.create(req);
  }

  @PutMapping("/{id}")
  public ContentDtos.View update(
    @PathVariable UUID id,
    @Valid @RequestBody ContentDtos.Write req
  ) {
    return service.update(id, req);
  }

  @PostMapping("/{id}/transition")
  public ContentDtos.View transition(
    @PathVariable UUID id,
    @Valid @RequestBody ContentDtos.Transition req
  ) {
    return service.transition(id, req);
  }

  @GetMapping("/{id}/history")
  public List<Map<String, Object>> history(@PathVariable UUID id) {
    return service.history(id);
  }
}
