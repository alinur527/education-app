package ent.kz.entbackend.dto;

import java.util.List;
import java.util.UUID;

public record QuestionAdminResponse(
  UUID id,
  UUID topicId,
  String questionRu,
  String questionKz,
  List<QuestionOptionResponse> options,
  String correctOptionId,
  String explanationRu,
  String explanationKz,
  String difficulty,
  Integer year,
  Boolean isActive
) {}
