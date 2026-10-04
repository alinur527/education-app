package ent.kz.entbackend.platform.content;

import static ent.kz.entbackend.platform.PlatformException.require;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public final class ProvenanceValidation {

  private ProvenanceValidation() {}

  public static void validate(JsonNode p) {
    for (String key : List.of(
      "contentLanguage",
      "studiedLanguage",
      "curriculumVariant",
      "examVersion"
    ))
      if (p.hasNonNull(key)) require(
        p.get(key).isTextual() && p.get(key).asText().length() <= 100,
        "INVALID_PROVENANCE"
      );
    if (p.hasNonNull("category")) require(
      Set.of("MANDATORY", "PROFILE", "OTHER").contains(
        p.path("category").asText()
      ),
      "INVALID_CATEGORY"
    );
    if (p.hasNonNull("offlineAllowed")) require(
      p.get("offlineAllowed").isBoolean(),
      "INVALID_OFFLINE_POLICY"
    );
    if (p.hasNonNull("contextId")) ContentValidation.uuid(
      p.path("contextId").asText()
    );
    if (p.hasNonNull("contextVersion")) require(
      p.get("contextVersion").isIntegralNumber() &&
        p.get("contextVersion").asLong() > 0,
      "INVALID_CONTEXT_VERSION"
    );
    if (p.hasNonNull("sourceIds")) {
      require(
        p.get("sourceIds").isArray() && p.get("sourceIds").size() <= 20,
        "INVALID_SOURCE_IDS"
      );
      for (JsonNode id : p.get("sourceIds"))
        require(
          id.isTextual() && id.asText().matches("[A-Za-z0-9_.:-]{1,100}"),
          "INVALID_SOURCE_ID"
        );
    }
    if (p.hasNonNull("reviewChecks")) {
      require(p.get("reviewChecks").isObject(), "INVALID_REVIEW_CHECKS");
      p.get("reviewChecks")
        .fields()
        .forEachRemaining(e -> {
          require(
            Set.of(
              "sourceRead",
              "extractionChecked",
              "answerChecked",
              "translationChecked",
              "explanationChecked"
            ).contains(e.getKey()) && e.getValue().isBoolean(),
            "INVALID_REVIEW_CHECK"
          );
        });
      // Human review has an authenticated audit record, never an imported boolean.
    }
    if (p.hasNonNull("curriculum")) {
      JsonNode c = p.get("curriculum");
      require(c.isObject() && c.size() <= 9, "INVALID_CURRICULUM");
      c.fields().forEachRemaining(e -> {
        require(
          Set.of(
            "sourceId",
            "page",
            "sectionRu",
            "sectionKz",
            "officialCode",
            "variant",
            "examVersion",
            "platformCode",
            "extractionStatus"
          ).contains(e.getKey()) &&
            e.getValue().isTextual() &&
            e.getValue().asText().length() <= 1000,
          "INVALID_CURRICULUM"
        );
      });
    }
  }
}
