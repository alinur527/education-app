package ent.kz.entbackend.platform.courses;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class QuizController {

  private final QuizService service;

  public QuizController(QuizService service) {
    this.service = service;
  }

  @PostMapping("/api/quizzes/{id}/attempts")
  public Map<String, Object> start(@PathVariable UUID id) {
    return service.start(id);
  }

  @GetMapping("/api/quiz-attempts/{id}")
  public Map<String, Object> get(@PathVariable UUID id) {
    return service.get(id);
  }

  public record Answers(List<String> answers) {}

  @PostMapping("/api/quiz-attempts/{id}/finish")
  public Map<String, Object> finish(
    @PathVariable UUID id,
    @RequestBody Answers req
  ) {
    return service.finish(id, req.answers());
  }
}
