package ent.kz.entbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionOptionRequest(
  @NotBlank @Size(max = 10) String id,

  @NotBlank String textRu,

  @NotBlank String textKz
) {}
