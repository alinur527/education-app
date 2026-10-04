package ent.kz.entbackend.dto;

import java.util.List;
import java.util.UUID;

public record TestQuestionResponse(
  UUID sessionId,
  Integer index,
  Integer totalQuestions,
  UUID questionId,
  UUID topicId,
  String topicRu,
  String topicKz,
  String questionRu,
  String questionKz,
  List<QuestionOptionResponse> options,
  String difficulty,
  Integer year,
  com.fasterxml.jackson.databind.JsonNode assessment,
  com.fasterxml.jackson.databind.JsonNode context
) {}
