package ent.kz.entbackend.platform.courses;

import ent.kz.entbackend.platform.content.ContentDtos.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class CourseController {

  private final CourseService service;

  public CourseController(CourseService service) {
    this.service = service;
  }

  @GetMapping("/api/courses")
  public Page<Map<String, Object>> list(
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "0") int page
  ) {
    return service.list(q, page);
  }

  @GetMapping("/api/courses/{id}")
  public CourseService.Detail get(@PathVariable UUID id) {
    return service.detail(id);
  }

  @PostMapping("/api/courses/{id}/enroll")
  public Map<String, Boolean> enroll(@PathVariable UUID id) {
    service.selfEnroll(id);
    return Map.of("saved", true);
  }

  @PostMapping("/api/lessons/{id}/complete")
  public Map<String, Boolean> complete(@PathVariable UUID id) {
    service.completeLesson(id);
    return Map.of("saved", true);
  }

  @GetMapping("/api/lessons/{id}/activities")
  public List<Map<String, Object>> activities(@PathVariable UUID id) {
    return service.lessonActivities(id);
  }

  public record Enrollment(
    @NotBlank @Email String email,
    @NotNull @Pattern(regexp = "ACTIVE|CANCELLED") String status
  ) {}

  @PostMapping("/api/teacher/courses/{id}/enrollments")
  public Map<String, Boolean> manage(
    @PathVariable UUID id,
    @Valid @RequestBody Enrollment req
  ) {
    service.manageEnrollment(id, req.email(), req.status());
    return Map.of("saved", true);
  }
}
