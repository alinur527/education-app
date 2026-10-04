package ent.kz.entbackend.platform.learning;

import ent.kz.entbackend.dto.StartTestResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learning")
public class LearningController {

  private final LearningService service;

  public LearningController(LearningService service) {
    this.service = service;
  }

  @GetMapping("/me")
  public Map<String, Object> me() {
    return service.overview();
  }

  @PostMapping("/theories/{id}/read")
  public Map<String, Boolean> read(@PathVariable UUID id) {
    service.read(id);
    return Map.of("saved", true);
  }

  public record Review(@NotNull UUID topicId) {}

  @PostMapping("/errors/practice")
  public StartTestResponse review(@Valid @RequestBody Review req) {
    return service.review(req.topicId());
  }
}
