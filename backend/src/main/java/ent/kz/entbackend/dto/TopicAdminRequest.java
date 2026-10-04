package ent.kz.entbackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TopicAdminRequest(
  @NotNull UUID subjectId,

  @NotBlank @Size(max = 300) String titleRu,

  @NotBlank @Size(max = 300) String titleKz,

  String descriptionRu,
  String descriptionKz,

  @NotNull @Min(0) Integer sortOrder,

  @NotNull Boolean isActive
) {}
