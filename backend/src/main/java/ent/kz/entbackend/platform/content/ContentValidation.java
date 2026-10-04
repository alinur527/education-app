package ent.kz.entbackend.platform.content;

import static ent.kz.entbackend.platform.PlatformException.require;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ContentValidation {

  private static final Set<String> FIELDS = Set.of(
    "titleRu",
    "titleKz",
    "descriptionRu",
    "descriptionKz",
    "contentRu",
    "contentKz",
    "sortOrder",
    "icon",
    "color",
    "durationMinutes",
    "options",
    "correctOptionId",
    "explanationRu",
    "explanationKz",
    "difficulty",
    "year",
    "sourceType",
    "sourceUrl",
    "sourceName",
    "language",
    "verified",
    "blocks",
    "visibility",
    "selfEnroll",
    "dueAt",
    "maxScore",
    "questions"
  );

  public void validate(ContentKind kind, JsonNode p, boolean publish) {
    require(
      p != null && p.isObject() && p.toString().length() <= 200000,
      "CONTENT_TOO_LARGE"
    );
    p.fieldNames().forEachRemaining(k ->
      require(FIELDS.contains(k), "UNKNOWN_FIELD")
    );
    text(
      p,
      "titleRu",
      kind == ContentKind.QUESTION
        ? 10000
        : kind == ContentKind.SUBJECT
          ? 200
          : 300,
      true
    );
    text(
      p,
      "titleKz",
      kind == ContentKind.QUESTION
        ? 10000
        : kind == ContentKind.SUBJECT
          ? 200
          : 300,
      publish
    );
    for (String k : List.of(
      "descriptionRu",
      "descriptionKz",
      "contentRu",
      "contentKz",
      "explanationRu",
      "explanationKz"
    ))
      text(p, k, 50000, false);
    number(p, "sortOrder", 0, 10000);
    number(p, "durationMinutes", 1, 1440);
    number(p, "year", 1900, 2200);
    number(p, "maxScore", 1, 10000);
    for (String k : List.of("verified", "selfEnroll"))
      if (p.hasNonNull(k)) require(p.get(k).isBoolean(), "INVALID_BOOLEAN");
    enumeration(
      p,
      "sourceType",
      Set.of("OFFICIAL_SAMPLE", "EDITOR_CREATED", "AI_GENERATED", "IMPORTED")
    );
    enumeration(p, "visibility", Set.of("PUBLIC", "PRIVATE"));
    enumeration(p, "language", Set.of("ru", "kz", "both"));
    enumeration(p, "difficulty", Set.of("easy", "medium", "hard"));
    text(p, "correctOptionId", 10, false);
    text(p, "sourceName", 300, false);
    text(p, "sourceUrl", 2000, false);
    text(p, "dueAt", 100, false);
    text(p, "icon", 50, false);
    text(p, "color", 20, false);
    if (!p.path("sourceUrl").asText("").isBlank()) safeUrl(
      p.path("sourceUrl").asText()
    );
    if (!p.path("dueAt").asText("").isBlank()) try {
      java.time.OffsetDateTime.parse(p.path("dueAt").asText());
    } catch (Exception e) {
      require(false, "INVALID_DATE");
    }
    if (kind == ContentKind.QUESTION || p.has("options")) question(p, publish);
    if (kind == ContentKind.QUIZ || p.has("questions")) {
      require(
        p.path("questions").isArray() &&
          p.path("questions").size() >= 1 &&
          p.path("questions").size() <= 100,
        "QUIZ_QUESTIONS_REQUIRED"
      );
      for (JsonNode q : p.path("questions")) {
        text(q, "titleRu", 10000, true);
        text(q, "titleKz", 10000, publish);
        question(q, publish);
      }
    }
    if (p.has("blocks")) {
      require(
        p.path("blocks").isArray() && p.path("blocks").size() <= 100,
        "INVALID_BLOCKS"
      );
      for (JsonNode b : p.path("blocks")) {
        require(
          Set.of(
            "TEXT",
            "HEADING",
            "IMAGE",
            "FILE",
            "VIDEO",
            "QUOTE",
            "FORMULA",
            "CALLOUT",
            "PRACTICE"
          ).contains(b.path("type").asText()),
          "INVALID_BLOCK_TYPE"
        );
        text(b, "materialId", 36, false);
        text(b, "url", 2000, false);
        text(b, "topicId", 36, false);
        text(b, "textRu", 20000, false);
        text(b, "textKz", 20000, false);
        if (b.path("type").asText().equals("VIDEO")) safeUrl(
          b.path("url").asText()
        );
        if (Set.of("FILE", "IMAGE").contains(b.path("type").asText())) uuid(
          b.path("materialId").asText()
        );
        if (b.path("type").asText().equals("PRACTICE")) uuid(
          b.path("topicId").asText()
        );
      }
    }
  }

  private void question(JsonNode p, boolean publish) {
    text(p, "correctOptionId", 10, true);
    text(p, "explanationRu", 50000, false);
    text(p, "explanationKz", 50000, false);
    JsonNode options = p.path("options");
    require(
      options.isArray() && options.size() >= 2 && options.size() <= 8,
      "OPTIONS_COUNT"
    );
    Set<String> ids = new HashSet<>();
    for (JsonNode o : options) {
      text(o, "id", 10, true);
      text(o, "textRu", 5000, true);
      text(o, "textKz", 5000, publish);
      require(ids.add(o.path("id").asText()), "DUPLICATE_OPTION");
    }
    require(
      ids.contains(p.path("correctOptionId").asText()),
      "CORRECT_OPTION_MISSING"
    );
  }

  public static UUID uuid(String s) {
    try {
      return UUID.fromString(s);
    } catch (Exception e) {
      require(false, "INVALID_ID");
      return null;
    }
  }

  public static void safeUrl(String s) {
    try {
      URI u = URI.create(s);
      require(
        s.length() <= 2000 &&
          Set.of("https", "http").contains(u.getScheme()) &&
          u.getHost() != null &&
          u.getUserInfo() == null,
        "INVALID_URL"
      );
    } catch (IllegalArgumentException e) {
      require(false, "INVALID_URL");
    }
  }

  private void text(JsonNode p, String k, int max, boolean required) {
    JsonNode n = p.get(k);
    require(n == null || n.isNull() || n.isTextual(), "INVALID_TEXT");
    String s = p.path(k).asText("");
    require(
      s.length() <= max && (!required || !s.isBlank()),
      "INVALID_" + k.toUpperCase(Locale.ROOT)
    );
  }

  private void number(JsonNode p, String k, int min, int max) {
    if (p.hasNonNull(k)) require(
      p.get(k).isIntegralNumber() &&
        p.get(k).canConvertToInt() &&
        p.get(k).asLong() >= min &&
        p.get(k).asLong() <= max,
      "INVALID_NUMBER"
    );
  }

  private void enumeration(JsonNode p, String k, Set<String> values) {
    if (p.hasNonNull(k)) require(
      values.contains(p.get(k).asText()),
      "INVALID_" + k.toUpperCase(Locale.ROOT)
    );
  }
}
