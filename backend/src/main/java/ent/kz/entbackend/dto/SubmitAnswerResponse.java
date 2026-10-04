package ent.kz.entbackend.dto;

import java.util.UUID;

public record SubmitAnswerResponse(
  UUID questionId,
  String selectedOptionId,
  com.fasterxml.jackson.databind.JsonNode answer
) {}
