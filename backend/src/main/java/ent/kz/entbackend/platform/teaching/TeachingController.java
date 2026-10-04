package ent.kz.entbackend.platform.teaching;

import ent.kz.entbackend.platform.content.ContentDtos.Page;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teacher")
public class TeachingController {

  private final TeachingService service;

  public TeachingController(TeachingService service) {
    this.service = service;
  }

  @GetMapping("/groups")
  public Page<Map<String, Object>> groups(
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "0") int page
  ) {
    return service.groups(q, page);
  }

  @PostMapping("/groups")
  public Map<String, UUID> create(
    @Valid @RequestBody TeachingService.NewGroup req
  ) {
    return Map.of("id", service.create(req));
  }

  @GetMapping("/groups/{id}")
  public Map<String, Object> group(@PathVariable UUID id) {
    return service.detail(id);
  }

  @PostMapping("/groups/{id}/members")
  public Map<String, Boolean> add(
    @PathVariable UUID id,
    @Valid @RequestBody TeachingService.Member req
  ) {
    service.add(id, req);
    return Map.of("saved", true);
  }

  @DeleteMapping("/groups/{id}/members/{user}")
  public Map<String, Boolean> remove(
    @PathVariable UUID id,
    @PathVariable UUID user
  ) {
    service.remove(id, user);
    return Map.of("saved", true);
  }

  public record Assign(
    @jakarta.validation.constraints.NotNull UUID assignmentId
  ) {}

  @PostMapping("/groups/{id}/assignments")
  public Map<String, Boolean> assign(
    @PathVariable UUID id,
    @Valid @RequestBody Assign req
  ) {
    service.assign(id, req.assignmentId());
    return Map.of("saved", true);
  }

  @GetMapping("/assignments/{id}/submissions")
  public Page<Map<String, Object>> submissions(
    @PathVariable UUID id,
    @RequestParam(defaultValue = "0") int page
  ) {
    return service.submissions(id, page);
  }

  @PostMapping("/assignments/{id}/submissions/{user}/grade")
  public Map<String, Boolean> grade(
    @PathVariable UUID id,
    @PathVariable UUID user,
    @Valid @RequestBody TeachingService.Grade req
  ) {
    service.grade(id, user, req);
    return Map.of("saved", true);
  }
}
