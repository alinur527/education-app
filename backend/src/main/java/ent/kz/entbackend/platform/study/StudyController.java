package ent.kz.entbackend.platform.study;

import ent.kz.entbackend.platform.content.ContentDtos.Page;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/study")
public class StudyController {

  private final StudyPlannerService planner;
  private final StudyNotesService notes;
  private final StudyNotificationService notifications;
  private final StudyDeadlineService deadlines;

  public StudyController(
    StudyPlannerService planner,
    StudyNotesService notes,
    StudyNotificationService notifications,
    StudyDeadlineService deadlines
  ) {
    this.planner = planner;
    this.notes = notes;
    this.notifications = notifications;
    this.deadlines = deadlines;
  }

  @GetMapping("/profile")
  public StudyDtos.Profile profile() {
    return planner.profile();
  }

  @PutMapping("/profile")
  public StudyDtos.Profile profile(@Valid @RequestBody StudyDtos.Profile p) {
    return planner.saveProfile(p);
  }

  @PostMapping("/plan/recompute")
  public StudyDtos.PlanResult recompute() {
    return planner.recompute();
  }

  @GetMapping("/tasks")
  public StudyDtos.Calendar tasks(
    @RequestParam LocalDate from,
    @RequestParam LocalDate to,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "false") boolean unscheduled
  ) {
    return planner.calendar(from, to, page, unscheduled);
  }

  @GetMapping("/deadlines")
  public Page<StudyDtos.Deadline> deadlines(
    @RequestParam LocalDate from,
    @RequestParam LocalDate to,
    @RequestParam(defaultValue = "0") int page
  ) {
    return deadlines.list(from, to, page);
  }

  @PatchMapping("/tasks/{id}")
  public StudyDtos.Task task(
    @PathVariable UUID id,
    @Valid @RequestBody StudyDtos.TaskChange change
  ) {
    return planner.update(id, change);
  }

  @PostMapping("/tasks/{id}/open")
  public StudyDtos.Open openTask(@PathVariable UUID id) {
    return planner.open(id);
  }

  @GetMapping("/notes")
  public Page<StudyDtos.Note> notes(
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "false") boolean bookmarked,
    @RequestParam(defaultValue = "false") boolean due,
    @RequestParam(defaultValue = "0") int page
  ) {
    return notes.list(q, bookmarked, due, page);
  }

  @GetMapping("/notes/{id}")
  public StudyDtos.Note note(@PathVariable UUID id) {
    return notes.get(id);
  }

  @PutMapping("/notes/{id}")
  public StudyDtos.Note note(
    @PathVariable UUID id,
    @Valid @RequestBody StudyDtos.NoteWrite n
  ) {
    return notes.save(id, n);
  }

  @DeleteMapping("/notes/{id}")
  public Map<String, Boolean> delete(
    @PathVariable UUID id,
    @RequestParam long revision
  ) {
    notes.delete(id, revision);
    return Map.of("saved", true);
  }

  @PostMapping("/notes/{id}/review")
  public StudyDtos.Note review(
    @PathVariable UUID id,
    @Valid @RequestBody StudyDtos.Review r
  ) {
    return notes.review(id, r);
  }

  @GetMapping("/notifications/preferences")
  public StudyDtos.Preferences preferences() {
    return notifications.preferences();
  }

  @PutMapping("/notifications/preferences")
  public StudyDtos.Preferences preferences(
    @Valid @RequestBody StudyDtos.Preferences p
  ) {
    return notifications.savePreferences(p);
  }

  @GetMapping("/notifications")
  public StudyDtos.Notifications notifications(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "false") boolean unread
  ) {
    return notifications.list(page, unread);
  }

  @PatchMapping("/notifications/{id}")
  public Map<String, Boolean> read(
    @PathVariable UUID id,
    @RequestBody StudyDtos.ReadChange r
  ) {
    notifications.read(id, r.read());
    return Map.of("saved", true);
  }

  @PostMapping("/notifications/{id}/open")
  public StudyDtos.Open openNotification(@PathVariable UUID id) {
    return notifications.open(id);
  }
}
