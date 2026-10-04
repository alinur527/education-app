package ent.kz.entbackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubjectAdminRequest(
  @NotBlank @Size(max = 200) String nameRu,

  @NotBlank @Size(max = 200) String nameKz,

  @Size(max = 50) String icon,

  @Size(max = 20) String color,

  @NotNull @Min(0) Integer questionCount,

  @NotNull @Min(0) Integer durationMinutes,

  @NotNull Boolean isActive
) {}
