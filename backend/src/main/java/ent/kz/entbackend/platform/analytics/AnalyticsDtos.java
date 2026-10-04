package ent.kz.entbackend.platform.analytics;

import java.time.*;
import java.util.*;

public final class AnalyticsDtos {

  private AnalyticsDtos() {}

  public record Day(
    LocalDate date,
    long questionsAnswered,
    long fullyCorrect,
    Double accuracy,
    long earnedPoints,
    long maxPoints,
    long testsCompleted,
    long testTimeSecs,
    long theoryReads,
    long lessonCompletions,
    long plannerTasksCompleted,
    long assignmentsSubmitted,
    long errorsResolved,
    long studyActions
  ) {}

  public record Summary(
    long questionsAnswered,
    long fullyCorrectAnswers,
    Double accuracyPercent,
    long earnedPoints,
    long maxPoints,
    Double pointsPercent,
    long testsCompleted,
    long activeDays,
    long currentStreak,
    long errorsResolved,
    long theoriesRead,
    long lessonsCompleted,
    long plannerTasksCompleted,
    long assignmentsSubmitted,
    long testTimeSecs
  ) {}

  public record Comparison(
    boolean available,
    LocalDate from,
    LocalDate to,
    Long questionsDelta,
    Double accuracyDelta,
    Double pointsPercentDelta,
    Long testsCompletedDelta,
    Long activeDaysDelta
  ) {}

  public record Subject(
    UUID subjectId,
    String titleRu,
    String titleKz,
    long questionsAnswered,
    Double accuracyPercent,
    Double pointsPercent,
    long attempts,
    Double accuracyDelta
  ) {}

  public record Overview(
    String period,
    String timeZone,
    Instant asOf,
    LocalDate from,
    LocalDate to,
    boolean hasPracticeHistory,
    Summary summary,
    Comparison comparison,
    List<Day> daily,
    List<Day> heatmap,
    List<Subject> subjects,
    List<Map<String, Object>> weakTopics,
    List<Map<String, Object>> strongTopics
  ) {}

  public record Event(
    String id,
    String kind,
    Instant occurredAt,
    String titleRu,
    String titleKz,
    String href
  ) {}

  public record History(List<Event> items, long total, int page, int size) {}
}
