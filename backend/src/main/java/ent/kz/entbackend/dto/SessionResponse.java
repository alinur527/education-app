package ent.kz.entbackend.dto;

import java.time.LocalDateTime;
import java.util.*;

public record SessionResponse(
  UUID sessionId,
  UUID topicId,
  UUID subjectId,
  String status,
  int totalQuestions,
  LocalDateTime startedAt,
  List<SavedAnswer> answers,
  String practiceMode,
  java.time.OffsetDateTime deadlineAt,
  Integer maxPoints,
  List<UUID> questionIds
) {
  public record SavedAnswer(
    UUID questionId,
    String selectedOptionId,
    com.fasterxml.jackson.databind.JsonNode answer
  ) {}
}
