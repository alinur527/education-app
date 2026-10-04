package ent.kz.entbackend.platform.learning;

import ent.kz.entbackend.dto.StartTestResponse;
import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.service.TestSessionService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LearningService {

  private final LearningRepository repo;
  private final Actor actor;
  private final TestSessionService tests;

  public LearningService(
    LearningRepository repo,
    Actor actor,
    TestSessionService tests
  ) {
    this.repo = repo;
    this.actor = actor;
    this.tests = tests;
  }

  public Map<String, Object> overview() {
    UUID id = actor.id();
    var result = new LinkedHashMap<>(repo.overview(id));
    var topics = repo.topics(id);
    result.put("topics", topics);
    result.put("continueTopic", repo.continueTopic(id));
    var subjects = new LinkedHashMap<UUID, Map<String, Object>>();
    for (var t : topics) {
      UUID s = (UUID) t.get("subjectId");
      subjects.computeIfAbsent(s, k ->
        new LinkedHashMap<>(
          Map.of(
            "id",
            s,
            "titleRu",
            t.get("subjectRu"),
            "titleKz",
            t.get("subjectKz"),
            "mastery",
            0.0,
            "topics",
            0
          )
        )
      );
      var row = subjects.get(s);
      row.put(
        "mastery",
        (Double) row.get("mastery") + ((Number) t.get("mastery")).doubleValue()
      );
      row.put("topics", (Integer) row.get("topics") + 1);
    }
    subjects
      .values()
      .forEach(s ->
        s.put(
          "mastery",
          Math.round(
            ((Double) s.get("mastery") / (Integer) s.get("topics")) * 10
          ) / 10.0
        )
      );
    result.put("subjects", subjects.values());
    var errors = repo.errors(id);
    result.put("errorTopics", errors);
    result.put(
      "errorCount",
      errors
        .stream()
        .mapToLong(e -> ((Number) e.get("count")).longValue())
        .sum()
    );
    return result;
  }

  public void read(UUID id) {
    repo.read(actor.id(), id);
  }

  public StartTestResponse review(UUID topic) {
    var items = repo.review(actor.id(), topic);
    if (items.isEmpty()) throw new PlatformException(409, "NO_ERRORS");
    return tests.startReview(topic, items);
  }
}
