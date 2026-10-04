package ent.kz.entbackend.platform.analytics;

import static ent.kz.entbackend.platform.analytics.AnalyticsDtos.*;
import static ent.kz.entbackend.platform.analytics.AnalyticsRepository.*;

import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.learning.LearningRepository;
import java.time.*;
import java.util.*;
import java.util.function.ToLongFunction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AnalyticsService {

  private final AnalyticsRepository repo;
  private final LearningRepository learning;
  private final Actor actor;
  private final Clock clock;

  public AnalyticsService(
    AnalyticsRepository repo,
    LearningRepository learning,
    Actor actor,
    @Qualifier("analyticsClock") Clock clock
  ) {
    this.repo = repo;
    this.learning = learning;
    this.actor = actor;
    this.clock = clock;
  }

  record Window(
    UUID user,
    ZoneId zone,
    Instant now,
    LocalDate today,
    LocalDate first,
    int length
  ) {}

  Window window(String period) {
    PlatformException.require(
      Set.of("7d", "30d", "all").contains(period),
      "INVALID_PERIOD"
    );
    UUID user = actor.id();
    ZoneId zone = ZoneId.of(repo.zone(user));
    Instant now = clock.instant();
    LocalDate today = now.atZone(zone).toLocalDate();
    int length = period.equals("all") ? 0 : period.equals("7d") ? 7 : 30;
    return new Window(
      user,
      zone,
      now,
      today,
      length == 0
        ? repo.firstDate(user, zone, now)
        : today.minusDays(length - 1),
      length
    );
  }

  public Overview overview(String period) {
    Window w = window(period);
    LocalDate fetchFrom =
      w.length == 0 && w.first.isBefore(w.today.minusDays(83))
        ? w.first
        : w.today.minusDays(83);
    if (
      w.length > 0 && w.first.minusDays(w.length).isBefore(fetchFrom)
    ) fetchFrom = w.first.minusDays(w.length);
    var all = repo.days(w.user, w.zone, fetchFrom, w.today, w.now);
    var selected = all
      .stream()
      .filter(d -> !d.date().isBefore(w.first))
      .toList();
    Set<LocalDate> active = repo.activeDates(w.user, w.zone, w.now);
    long streak = 0;
    LocalDate tail = active.contains(w.today) ? w.today : w.today.minusDays(1);
    while (active.contains(tail)) {
      streak++;
      tail = tail.minusDays(1);
    }
    Summary current = summary(selected, streak);
    Comparison comparison = null;
    Instant priorStart = null;
    if (w.length > 0) {
      LocalDate priorFrom = w.first.minusDays(w.length),
        priorTo = w.first.minusDays(1);
      var prior = summary(
        all
          .stream()
          .filter(
            d -> !d.date().isBefore(priorFrom) && !d.date().isAfter(priorTo)
          )
          .toList(),
        0
      );
      boolean available = prior.activeDays() > 0;
      comparison = new Comparison(
        available,
        priorFrom,
        priorTo,
        available
          ? current.questionsAnswered() - prior.questionsAnswered()
          : null,
        delta(current.accuracyPercent(), prior.accuracyPercent(), available),
        delta(current.pointsPercent(), prior.pointsPercent(), available),
        available ? current.testsCompleted() - prior.testsCompleted() : null,
        available ? current.activeDays() - prior.activeDays() : null
      );
      priorStart = priorFrom.atStartOfDay(w.zone).toInstant();
    }
    var topics = learning.topics(w.user, w.now);
    var sufficient = repo.sufficientTopics(w.user, w.now);
    return new Overview(
      period,
      w.zone.getId(),
      w.now,
      w.first,
      w.today,
      repo.hasPractice(w.user, w.now),
      current,
      comparison,
      selected,
      all
        .stream()
        .filter(d -> !d.date().isBefore(w.today.minusDays(83)))
        .toList(),
      repo.subjects(
        w.user,
        w.first.atStartOfDay(w.zone).toInstant(),
        w.now,
        priorStart
      ),
      topics
        .stream()
        .filter(
          t ->
            n(t, "attempts") > 0 &&
            ((Number) t.get("mastery")).doubleValue() < 70
        )
        .limit(5)
        .toList(),
      topics
        .stream()
        .filter(
          t ->
            sufficient.contains(t.get("topicId")) &&
            ((Number) t.get("mastery")).doubleValue() >= 80
        )
        .sorted(
          Comparator.comparingDouble((Map<String, Object> t) ->
            ((Number) t.get("mastery")).doubleValue()
          ).reversed()
        )
        .limit(5)
        .toList()
    );
  }

  public History history(String period, String kind, int page, int size) {
    PlatformException.require(
      Set.of(
        "ALL",
        "TEST",
        "THEORY",
        "LESSON",
        "ERROR_RESOLVED",
        "PLANNER_TASK",
        "ASSIGNMENT"
      ).contains(kind),
      "INVALID_ACTIVITY_KIND"
    );
    PlatformException.require(
      page >= 0 && page <= 100000 && size >= 1 && size <= 50,
      "INVALID_PAGINATION"
    );
    Window w = window(period);
    return repo.history(
      w.user,
      w.first.atStartOfDay(w.zone).toInstant(),
      w.now,
      kind,
      page,
      size
    );
  }

  static Double delta(Double current, Double prior, boolean available) {
    return !available || current == null || prior == null
      ? null
      : round(current - prior);
  }

  static long sum(List<Day> days, ToLongFunction<Day> metric) {
    return days.stream().mapToLong(metric).sum();
  }

  static Summary summary(List<Day> days, long streak) {
    long questions = sum(days, Day::questionsAnswered),
      correct = sum(days, Day::fullyCorrect),
      earned = sum(days, Day::earnedPoints),
      max = sum(days, Day::maxPoints);
    return new Summary(
      questions,
      correct,
      percent(correct, questions),
      earned,
      max,
      percent(earned, max),
      sum(days, Day::testsCompleted),
      days
        .stream()
        .filter(d -> d.studyActions() > 0)
        .count(),
      streak,
      sum(days, Day::errorsResolved),
      sum(days, Day::theoryReads),
      sum(days, Day::lessonCompletions),
      sum(days, Day::plannerTasksCompleted),
      sum(days, Day::assignmentsSubmitted),
      sum(days, Day::testTimeSecs)
    );
  }
}
