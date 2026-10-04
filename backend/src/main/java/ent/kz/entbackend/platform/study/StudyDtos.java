package ent.kz.entbackend.platform.study;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class StudyDtos {

  private StudyDtos() {}

  public record Profile(
    @NotNull @Size(max = 300) String goal,
    @NotNull LocalDate targetDate,
    @NotEmpty
    @Size(max = 7)
    List<@NotNull @Min(1) @Max(7) Integer> availableDays,
    @Min(10) @Max(360) int minutesPerDay,
    @NotBlank @Size(max = 80) String timeZone,
    @NotNull @Size(max = 30) List<@NotNull UUID> selectedSubjects,
    @Min(0) long revision
  ) {}

  public record Task(
    UUID id,
    String kind,
    String targetKind,
    UUID targetId,
    String titleRu,
    String titleKz,
    String reason,
    OffsetDateTime scheduledAt,
    int durationMinutes,
    String status,
    boolean pinned,
    boolean manuallyMoved,
    long revision,
    boolean available,
    String url
  ) {}

  public record Calendar(
    List<Task> items,
    int page,
    int size,
    long total,
    long unscheduled
  ) {}

  public record Deadline(
    UUID id,
    String titleRu,
    String titleKz,
    OffsetDateTime dueAt,
    boolean submitted,
    String url
  ) {}

  public record PlanResult(
    int planned,
    int preserved,
    int unscheduled,
    int requiredMinutes,
    int capacityMinutes,
    boolean capacityWarning
  ) {}

  public record TaskChange(
    OffsetDateTime scheduledAt,
    Boolean pinned,
    @Pattern(regexp = "PLANNED|COMPLETED|SKIPPED") String status,
    @Min(1) long revision
  ) {}

  public record NoteWrite(
    @NotBlank @Pattern(regexp = "TOPIC|LESSON") String targetKind,
    @NotNull UUID targetId,
    @NotBlank @Size(max = 300) String title,
    @NotNull @Size(max = 20000) String body,
    boolean bookmarked,
    @NotNull @Size(max = 4000) String cardFront,
    @NotNull @Size(max = 4000) String cardBack,
    @Min(0) long revision
  ) {}

  public record Note(
    UUID id,
    String targetKind,
    UUID targetId,
    String title,
    String body,
    boolean bookmarked,
    String cardFront,
    String cardBack,
    OffsetDateTime nextReviewAt,
    int intervalDays,
    int reviewCount,
    long revision,
    boolean available,
    String url,
    OffsetDateTime updatedAt
  ) {}

  public record Review(
    @NotNull @Pattern(regexp = "AGAIN|GOOD|EASY") String rating,
    @Min(1) long revision
  ) {}

  public record Preferences(
    boolean enabled,
    boolean plans,
    boolean deadlines,
    boolean grades,
    boolean materials,
    boolean quietEnabled,
    @NotNull LocalTime quietStart,
    @NotNull LocalTime quietEnd,
    @Min(0) long revision
  ) {}

  public record Notification(
    UUID id,
    String kind,
    String titleRu,
    String titleKz,
    boolean read,
    OffsetDateTime createdAt,
    boolean available
  ) {}

  public record Notifications(
    List<Notification> items,
    int page,
    int size,
    long total,
    long unread
  ) {}

  public record ReadChange(boolean read) {}

  public record Open(String url) {}
}
