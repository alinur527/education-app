package ent.kz.entbackend.platform.assessment;

import static ent.kz.entbackend.platform.PlatformException.require;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Shared validation, canonical answers and frozen scoring for ENT and lesson quizzes. */
public final class Assessment {

  private static final ObjectMapper JSON = new ObjectMapper();
  public static final Set<String> TYPES = Set.of(
    "SINGLE_CHOICE",
    "MULTIPLE_SELECT",
    "MATCHING"
  );

  public record Grade(JsonNode answer, int earned, int maximum) {
    public boolean correct() {
      return earned == maximum;
    }
  }

  private Assessment() {}

  public static String type(JsonNode q) {
    return q.path("questionType").asText("SINGLE_CHOICE");
  }

  public static void validate(JsonNode q, boolean publish) {
    require(q != null && q.isObject(), "INVALID_QUESTION");
    String type = type(q);
    require(TYPES.contains(type), "INVALID_QUESTION_TYPE");
    Set<String> right = options(q.path("options"), publish, 2, 10);
    if (type.equals("SINGLE_CHOICE")) {
      require(
        q.path("correctOptionId").isTextual() &&
          right.contains(q.path("correctOptionId").asText()),
        "CORRECT_OPTION_MISSING"
      );
      require(
        !q.hasNonNull("correctOptionIds") && !q.hasNonNull("correctPairs"),
        "AMBIGUOUS_ANSWER_KEY"
      );
    } else if (type.equals("MULTIPLE_SELECT")) {
      Set<String> key = ids(q.path("correctOptionIds"));
      require(
        key.size() >= 1 && key.size() <= 3 && right.containsAll(key),
        "INVALID_MULTIPLE_KEY"
      );
      require(
        !q.hasNonNull("correctOptionId") && !q.hasNonNull("correctPairs"),
        "AMBIGUOUS_ANSWER_KEY"
      );
    } else {
      Set<String> left = options(q.path("leftOptions"), publish, 2, 2);
      pairs(q.path("correctPairs"), left, right);
      require(
        !q.hasNonNull("correctOptionId") && !q.hasNonNull("correctOptionIds"),
        "AMBIGUOUS_ANSWER_KEY"
      );
    }
    String policy = q.path("scoringPolicy").asText(defaultPolicy(type));
    require(policy.equals(defaultPolicy(type)), "UNSUPPORTED_SCORING_POLICY");
    for (String key : List.of("explanationRu", "explanationKz")) {
      JsonNode v = q.get(key);
      require(
        v == null ||
          v.isNull() ||
          (v.isTextual() && v.asText().length() <= 50000),
        "INVALID_EXPLANATION"
      );
    }
  }

  private static Set<String> options(
    JsonNode array,
    boolean publish,
    int min,
    int max
  ) {
    require(
      array.isArray() && array.size() >= min && array.size() <= max,
      "OPTIONS_COUNT"
    );
    Set<String> ids = new HashSet<>();
    for (JsonNode o : array) {
      require(
        o.isObject() &&
          o.path("id").isTextual() &&
          o.path("id").asText().matches("[A-Za-z0-9_-]{1,10}"),
        "INVALID_OPTION_ID"
      );
      require(ids.add(o.path("id").asText()), "DUPLICATE_OPTION");
      for (String lang : List.of("textRu", "textKz")) {
        JsonNode v = o.get(lang);
        require(
          v == null || v.isNull() || v.isTextual(),
          "INVALID_OPTION_TEXT"
        );
        String text = o.path(lang).asText("");
        require(
          text.length() <= 5000 &&
            (!(publish || lang.equals("textRu")) || !text.isBlank()),
          "OPTION_TRANSLATION_REQUIRED"
        );
      }
    }
    return ids;
  }

  private static Set<String> ids(JsonNode array) {
    require(
      array.isArray() && array.size() >= 1 && array.size() <= 10,
      "INVALID_SELECTION"
    );
    Set<String> ids = new TreeSet<>();
    for (JsonNode id : array)
      require(
        id.isTextual() && ids.add(id.asText()),
        "DUPLICATE_OR_INVALID_SELECTION"
      );
    return ids;
  }

  private static Map<String, String> pairs(
    JsonNode array,
    Set<String> left,
    Set<String> right
  ) {
    require(array.isArray() && array.size() == left.size(), "PAIR_COUNT");
    Map<String, String> out = new TreeMap<>();
    for (JsonNode p : array) {
      require(
        p.isObject() &&
          p.size() == 2 &&
          p.path("leftId").isTextual() &&
          p.path("rightId").isTextual(),
        "INVALID_PAIR"
      );
      String l = p.path("leftId").asText(),
        r = p.path("rightId").asText();
      require(
        left.contains(l) && right.contains(r) && out.putIfAbsent(l, r) == null,
        "UNKNOWN_OR_DUPLICATE_PAIR"
      );
    }
    return out;
  }

  public static String defaultPolicy(String type) {
    return switch (type) {
      case "MULTIPLE_SELECT" -> "ENT_MULTIPLE_2026_V1";
      case "MATCHING" -> "ENT_MATCHING_2026_V1";
      default -> "LEGACY_SINGLE_V1";
    };
  }

  public static int maximum(JsonNode q) {
    return type(q).equals("SINGLE_CHOICE") ? 1 : 2;
  }

  public static ObjectNode freeze(JsonNode source) {
    validate(source, false);
    ObjectNode q = JSON.createObjectNode();
    for (String k : List.of(
      "questionType",
      "options",
      "leftOptions",
      "correctOptionId",
      "correctOptionIds",
      "correctPairs",
      "contextId"
    ))
      if (source.hasNonNull(k)) q.set(k, source.get(k).deepCopy());
    q.put("questionType", type(source));
    q.put(
      "scoringPolicy",
      source.path("scoringPolicy").asText(defaultPolicy(type(source)))
    );
    q.put("maxPoints", maximum(source));
    return q;
  }

  /** Allowlist: new server-only key fields cannot leak merely by extending stored content. */
  public static ObjectNode publicView(JsonNode q) {
    ObjectNode out = JSON.createObjectNode();
    for (String key : List.of(
      "questionType",
      "options",
      "leftOptions",
      "contextKey",
      "scoringPolicy",
      "maxPoints"
    ))
      if (q.hasNonNull(key)) out.set(key, q.get(key).deepCopy());
    out.put("questionType", type(q));
    return out;
  }

  public static Grade grade(JsonNode q, JsonNode answer) {
    require(answer != null && answer.isObject(), "INVALID_ANSWER");
    String type = type(q);
    ObjectNode canonical = JSON.createObjectNode();
    int earned;
    // Stored snapshots already passed their version-specific authoring validation.
    // Do not retroactively reject legacy Cyrillic option IDs or old option counts.
    Set<String> right = new HashSet<>();
    q.path("options").forEach(o -> right.add(o.path("id").asText()));
    switch (type) {
      case "SINGLE_CHOICE" -> {
        require(
          answer.size() == 1 && answer.path("selectedOptionId").isTextual(),
          "INVALID_SINGLE_ANSWER"
        );
        String selected = answer.path("selectedOptionId").asText();
        require(right.contains(selected), "INVALID_OPTION");
        canonical.put("selectedOptionId", selected);
        earned = selected.equals(q.path("correctOptionId").asText()) ? 1 : 0;
      }
      case "MULTIPLE_SELECT" -> {
        require(
          answer.size() == 1 && answer.has("selectedOptionIds"),
          "INVALID_MULTIPLE_ANSWER"
        );
        Set<String> selected = ids(answer.path("selectedOptionIds"));
        require(right.containsAll(selected), "INVALID_OPTION");
        canonical.set("selectedOptionIds", JSON.valueToTree(selected));
        Set<String> key = ids(q.path("correctOptionIds"));
        long c = selected.stream().filter(key::contains).count(),
          w = selected.size() - c;
        // Order 204, paragraph 18 (2024-02-28 no.92), verified current in 2026.
        earned = selected.equals(key)
          ? 2
          : (key.size() == 1 ? c == 1 && w == 1 : c >= key.size() - 1 && w <= 1)
            ? 1
            : 0;
      }
      case "MATCHING" -> {
        require(
          answer.size() == 1 && answer.has("pairs"),
          "INVALID_MATCHING_ANSWER"
        );
        Set<String> left = options(q.path("leftOptions"), false, 2, 2);
        Map<String, String> selected = pairs(answer.path("pairs"), left, right),
          key = pairs(q.path("correctPairs"), left, right);
        ArrayNode arr = canonical.putArray("pairs");
        selected.forEach((l, r) ->
          arr.addObject().put("leftId", l).put("rightId", r)
        );
        earned = (int) selected
          .entrySet()
          .stream()
          .filter(e -> e.getValue().equals(key.get(e.getKey())))
          .count();
      }
      default -> throw new IllegalStateException(
        "Unsupported stored question type"
      );
    }
    return new Grade(canonical, earned, maximum(q));
  }
}
