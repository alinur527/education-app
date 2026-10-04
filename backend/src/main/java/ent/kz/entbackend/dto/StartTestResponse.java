package ent.kz.entbackend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record StartTestResponse(
  UUID sessionId,
  Integer totalQuestions,
  LocalDateTime startedAt
) {}
