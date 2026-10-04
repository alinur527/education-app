package ent.kz.entbackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TheoryAdminRequest(
  @NotNull UUID topicId,

  @NotBlank @Size(max = 300) String titleRu,

  @NotBlank @Size(max = 300) String titleKz,

  @NotBlank String contentRu,

  @NotBlank String contentKz,

  @NotNull @Min(0) Integer sortOrder,

  @NotNull Boolean isActive
) {}
