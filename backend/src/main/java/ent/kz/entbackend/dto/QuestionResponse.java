package ent.kz.entbackend.dto;

import java.util.List;
import java.util.UUID;

public record QuestionResponse(
  UUID id,
  UUID topicId,
  String topicRu,
  String topicKz,
  String questionRu,
  String questionKz,
  List<QuestionOptionResponse> options,
  String difficulty,
  Integer year
) {}
