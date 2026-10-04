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
  List<SavedAnswer> answers
) {
  public record SavedAnswer(UUID questionId, String selectedOptionId) {}
}
