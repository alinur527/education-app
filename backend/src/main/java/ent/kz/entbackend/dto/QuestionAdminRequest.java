package ent.kz.entbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;

public record QuestionAdminRequest(
  @NotNull UUID topicId,

  @NotBlank String questionRu,

  @NotBlank String questionKz,

  @NotEmpty
  @jakarta.validation.constraints.Size(min = 2, max = 8)
  List<@NotNull @Valid QuestionOptionRequest> options,

  @NotBlank String correctOptionId,

  String explanationRu,
  String explanationKz,

  @NotBlank
  @Pattern(regexp = "easy|medium|hard", flags = Pattern.Flag.CASE_INSENSITIVE)
  String difficulty,

  Integer year,

  @NotNull Boolean isActive
) {}
