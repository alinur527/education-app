package ent.kz.entbackend.platform.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ent.kz.entbackend.platform.assessment.ContextPublicView;
import java.util.List;

/** Published lesson data is distinct from the editor's payload and answer-review metadata. */
public final class PublicLearningPayload {

  private PublicLearningPayload() {}

  public static JsonNode of(JsonNode payload) {
    if (payload == null || payload.isNull()) return null;
    ObjectNode result = (ObjectNode) ContextPublicView.of(payload);
    for (String key : List.of(
      "icon",
      "color",
      "sortOrder",
      "durationMinutes",
      "visibility",
      "selfEnroll",
      "dueAt",
      "maxScore",
      "offlineAllowed",
      "category",
      "difficulty",
      "year",
      "verified",
      "sourceIds"
    ))
      if (payload.hasNonNull(key)) result.set(key, payload.get(key).deepCopy());
    return result;
  }
}
