package ent.kz.entbackend.dto;

import java.util.UUID;

public record SubjectResponse(
  UUID id,
  String nameRu,
  String nameKz,
  String icon,
  String color,
  Integer questionCount,
  Integer durationMinutes
) {}
