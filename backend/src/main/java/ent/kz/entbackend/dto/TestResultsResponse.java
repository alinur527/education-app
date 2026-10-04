package ent.kz.entbackend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TestResultsResponse(
  UUID sessionId,
  UUID topicId,
  UUID subjectId,
  String status,
  Integer totalQuestions,
  Integer correctAnswers,
  BigDecimal score,
  Integer timeTakenSecs,
  List<TestAnswerResultResponse> answers,
  Integer earnedPoints,
  Integer maxPoints,
  String practiceMode
) {}
