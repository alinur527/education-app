package ent.kz.entbackend.platform.assessment;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.List;

/** Student-facing passage data only. Internal snapshots retain the complete editorial record. */
public final class ContextPublicView {

  private static final ObjectMapper JSON = new ObjectMapper();

  private ContextPublicView() {}

  public static JsonNode of(JsonNode context) {
    if (context == null || context.isNull()) return null;
    ObjectNode result = JSON.createObjectNode();
    for (String key : List.of(
      "titleRu",
      "titleKz",
      "descriptionRu",
      "descriptionKz",
      "contentRu",
      "contentKz",
      "sourceType",
      "sourceName",
      "sourceUrl",
      "language",
      "contentLanguage",
      "studiedLanguage",
      "curriculumVariant",
      "examVersion",
      "curriculum"
    ))
      if (context.hasNonNull(key)) result.set(key, context.get(key).deepCopy());
    if (context.path("blocks").isArray()) {
      ArrayNode blocks = result.putArray("blocks");
      for (JsonNode block : context.path("blocks")) {
        ObjectNode safe = blocks.addObject();
        for (String key : List.of(
          "type",
          "textRu",
          "textKz",
          "materialId",
          "url",
          "topicId"
        ))
          if (block.hasNonNull(key)) safe.set(key, block.get(key).deepCopy());
      }
    }
    return result;
  }
}
