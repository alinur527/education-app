package ent.kz.entbackend.dto;

import java.util.UUID;

public record TopicAdminResponse(
  UUID id,
  UUID subjectId,
  String titleRu,
  String titleKz,
  String descriptionRu,
  String descriptionKz,
  Integer sortOrder,
  Boolean isActive
) {}
