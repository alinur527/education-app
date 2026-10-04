package ent.kz.entbackend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SubmitAnswerRequest(
  @NotNull UUID questionId,
  @NotBlank @Size(max = 10) String selectedOptionId,
  @Min(0) @Max(86400) Integer timeSpentSecs
) {}
