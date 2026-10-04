package ent.kz.entbackend.platform.teaching;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

  private final TeachingService service;

  public AssignmentController(TeachingService service) {
    this.service = service;
  }

  @GetMapping
  public List<Map<String, Object>> list() {
    return service.studentAssignments();
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable UUID id) {
    return service.assignment(id);
  }

  @PostMapping("/{id}/submit")
  public Map<String, Object> submit(
    @PathVariable UUID id,
    @Valid @RequestBody TeachingService.Submission req
  ) {
    return service.submit(id, req);
  }
}
