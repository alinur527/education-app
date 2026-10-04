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
  String explanationKz
) {}
