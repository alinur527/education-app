package ent.kz.entbackend.dto;

import java.util.UUID;

public record TheoryAdminResponse(
  UUID id,
  UUID topicId,
  String titleRu,
  String titleKz,
  String contentRu,
  String contentKz,
  Integer sortOrder,
  Boolean isActive
) {}
