package ent.kz.entbackend.platform.assessment;

import static ent.kz.entbackend.platform.PlatformException.require;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import ent.kz.entbackend.dto.StartTestResponse;
import ent.kz.entbackend.platform.PlatformException;
import ent.kz.entbackend.service.*;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PracticeService {

  public record Start(
    String mode,
    List<UUID> subjectIds,
    List<UUID> topicIds,
    Integer count,
    Integer minutes,
    Integer profilePair,
    String difficulty
  ) {}

  private final PracticeRepository repo;
  private final TestSessionService sessions;
  private final ContentAccess access;
  private final ObjectMapper json;
  private final JsonNode config;

  public PracticeService(
    PracticeRepository repo,
    TestSessionService sessions,
    ContentAccess access,
    ObjectMapper json
  ) {
    this.repo = repo;
    this.sessions = sessions;
    this.access = access;
    this.json = json;
    try (
      var stream = new ClassPathResource(
        "assessment/ent-2026.json"
      ).getInputStream()
    ) {
      config = json.readTree(stream);
    } catch (Exception e) {
      throw new IllegalStateException("Missing verified exam configuration", e);
    }
  }

  public Object catalog() {
    return Map.of(
      "subjects",
      repo.subjects(),
      "configuration",
      config,
      "topics",
      repo.topics()
    );
  }

  private List<Map<String, Object>> pairSubjects(int pair) {
    require(
      pair >= 0 && pair < config.path("allowedProfilePairs").size(),
      "INVALID_PROFILE_PAIR"
    );
    Set<String> wanted = new LinkedHashSet<>();
    config
      .path("mandatorySubjects")
      .forEach(v -> wanted.add(v.path("subject").asText()));
    config
      .path("allowedProfilePairs")
      .get(pair)
      .path("subjects")
      .forEach(v -> wanted.add(v.asText()));
    List<Map<String, Object>> all = repo.subjects(),
      result = new ArrayList<>();
    for (String name : wanted) {
      var matches = all
        .stream()
        .filter(s -> s.get("titleRu").equals(name))
        .toList();
      if (matches.size() == 1) result.add(matches.getFirst());
      else result.add(Map.of("titleRu", name, "titleKz", name, "available", 0));
    }
    return result;
  }

  public JsonNode availability(int pair) {
    ObjectNode out = json
      .createObjectNode()
      .put("configurationId", config.path("id").asText())
      .put("officialReady", false)
      .put("reason", "REVIEWED_BLUEPRINT_NOT_CERTIFIED")
      .put("requiredTotal", 120)
      .put("durationMinutes", 240);
    ArrayNode details = out.putArray("subjects");
    int total = 0;
    int index = 0;
    for (var subject : pairSubjects(pair)) {
      ObjectNode s = details
        .addObject()
        .put("titleRu", subject.get("titleRu").toString())
        .put("titleKz", subject.get("titleKz").toString());
      if (subject.containsKey("id")) s.put("id", subject.get("id").toString());
      var bank = subject.containsKey("id")
        ? repo.bank((UUID) subject.get("id"))
        : List.<Map<String, Object>>of();
      int required = index < 2 ? 10 : index == 2 ? 20 : 40,
        available = bank
          .stream()
          .mapToInt(b -> ((Number) b.get("available")).intValue())
          .sum();
      total += available;
      s.put("required", required)
        .put("available", available)
        .put("missing", Math.max(0, required - available));
      s.set("bank", json.valueToTree(bank));
      ArrayNode buckets = s.putArray("blueprint");
      if (index >= 3) for (JsonNode item : config.path("profileBlueprint")) {
        int count = bank
          .stream()
          .filter(
            b ->
              b.get("type").equals(item.path("questionType").asText()) &&
              b
                .get("context")
                .equals(item.path("contextRequired").asBoolean(false))
          )
          .mapToInt(b -> ((Number) b.get("available")).intValue())
          .sum();
        buckets
          .addObject()
          .put("type", item.path("questionType").asText())
          .put("context", item.path("contextRequired").asBoolean(false))
          .put("required", item.path("count").asInt())
          .put("available", count)
          .put("missing", Math.max(0, item.path("count").asInt() - count));
      }
      index++;
    }
    out
      .put("availableTotal", total)
      .put("shortenedMaximum", Math.min(50, total));
    return out;
  }

  @Transactional
  public StartTestResponse start(Start req) {
    require(
      req != null &&
        Set.of("MIXED_PRACTICE", "SHORTENED_ENT", "MOCK_ENT").contains(
          Objects.toString(req.mode(), "")
        ),
      "INVALID_MODE"
    );
    require(
      req.count() != null && req.count() >= 1 && req.count() <= 50,
      "INVALID_QUESTION_COUNT"
    );
    List<UUID> subjects,
      topics = req.topicIds() == null ? List.of() : req.topicIds();
    require(
      topics.size() <= 100 &&
        topics.stream().noneMatch(Objects::isNull) &&
        new HashSet<>(topics).size() == topics.size(),
      "INVALID_TOPICS"
    );
    String difficulty = req.difficulty() == null ? "" : req.difficulty();
    require(
      Set.of("", "easy", "medium", "hard").contains(difficulty),
      "INVALID_DIFFICULTY"
    );
    Integer minutes = null;
    ObjectNode frozen = json.createObjectNode();
    if (!req.mode().equals("MIXED_PRACTICE")) {
      require(req.profilePair() != null, "PROFILE_PAIR_REQUIRED");
      var availability = availability(req.profilePair());
      if (req.mode().equals("MOCK_ENT")) throw new PlatformException(
        409,
        "FULL_ENT_BANK_NOT_READY"
      );
      require(
        topics.isEmpty() && difficulty.isEmpty(),
        "EXAM_TOPIC_FILTER_NOT_ALLOWED"
      );
      require(
        req.minutes() != null && req.minutes() >= 1 && req.minutes() <= 240,
        "INVALID_DURATION"
      );
      minutes = req.minutes();
      subjects = pairSubjects(req.profilePair())
        .stream()
        .filter(s -> s.containsKey("id"))
        .map(s -> (UUID) s.get("id"))
        .toList();
      frozen.set("officialConfiguration", config);
      frozen.set("bankAtStart", availability);
      frozen.put("profilePair", req.profilePair());
      frozen
        .put("officialFormat", false)
        .put("shortened", true)
        .put("durationMinutes", minutes);
    } else {
      subjects = req.subjectIds();
      require(
        subjects != null && subjects.size() >= 1 && subjects.size() <= 5,
        "SELECT_SUBJECTS"
      );
      require(
        subjects.stream().noneMatch(Objects::isNull) &&
          new HashSet<>(subjects).size() == subjects.size(),
        "INVALID_SUBJECTS"
      );
      for (UUID id : subjects) access.subject(id);
      for (UUID id : topics)
        require(
          subjects.contains(access.topic(id).getSubject().getId()),
          "TOPIC_SUBJECT_MISMATCH"
        );
      frozen.put("officialFormat", false);
    }
    if (subjects.isEmpty()) throw new PlatformException(
      409,
      "INSUFFICIENT_BANK"
    );
    List<UUID> ids = repo.choose(subjects, topics, difficulty, req.count());
    if (ids.size() != req.count()) throw new PlatformException(
      409,
      "INSUFFICIENT_BANK"
    );
    frozen.set("topicIds", json.valueToTree(topics));
    frozen.put("difficulty", difficulty);
    frozen.set("subjectIds", json.valueToTree(subjects));
    frozen.put("count", ids.size());
    return sessions.startSelection(
      ids,
      req.mode().equals("MIXED_PRACTICE") ? "MIXED_PRACTICE" : "MOCK_ENT",
      minutes,
      frozen
    );
  }
}
