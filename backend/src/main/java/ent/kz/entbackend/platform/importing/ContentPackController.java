package ent.kz.entbackend.platform.importing;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cms/content-packs")
public class ContentPackController {

  private final ContentPackService service;

  public ContentPackController(ContentPackService service) {
    this.service = service;
  }

  @PostMapping
  public JsonNode preview(@RequestBody JsonNode request) {
    return service.preview(request);
  }

  @GetMapping
  public List<Map<String, Object>> history(
    @RequestParam(defaultValue = "0") int page
  ) {
    return service.history(page);
  }

  @GetMapping("/conflicts")
  public List<Map<String, Object>> conflicts(
    @RequestParam(defaultValue = "0") int page
  ) {
    return service.conflicts(page);
  }

  @GetMapping("/mappings")
  public List<Map<String, Object>> mappings(
    @RequestParam String namespace,
    @RequestParam(defaultValue = "0") int page
  ) {
    return service.mappings(namespace, page);
  }

  @GetMapping("/conflicts/{id}")
  public Object candidate(@PathVariable UUID id) {
    return service.candidate(id);
  }

  @PostMapping("/conflicts/{id}/resolve")
  public Object resolve(
    @PathVariable UUID id,
    @RequestBody ContentPackService.Resolution request
  ) {
    return service.resolve(id, request);
  }

  @GetMapping("/{id}")
  public JsonNode get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/confirm")
  public JsonNode confirm(@PathVariable UUID id) {
    return service.confirm(id);
  }
}
