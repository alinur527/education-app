package ent.kz.entbackend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FinishTestResponse(
  UUID sessionId,
  Integer correctAnswers,
  Integer totalQuestions,
  BigDecimal score,
  Integer timeTakenSecs
) {}
