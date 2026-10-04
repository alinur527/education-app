package ent.kz.entbackend.dto;

import java.util.List;
import java.util.UUID;

public record TestAnswerResultResponse(
  UUID questionId,
  String questionRu,
  String questionKz,
  List<QuestionOptionResponse> options,
  String selectedOptionId,
  String correctOptionId,
  Boolean isCorrect,
  String explanationRu,
  String explanationKz,
  com.fasterxml.jackson.databind.JsonNode assessment,
  com.fasterxml.jackson.databind.JsonNode answer,
  com.fasterxml.jackson.databind.JsonNode context,
  int earnedPoints,
  int maxPoints,
  UUID topicId,
  UUID subjectId
) {}
