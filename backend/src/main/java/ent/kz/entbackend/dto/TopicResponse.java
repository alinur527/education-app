package ent.kz.entbackend.dto;

import java.util.UUID;

public record TopicResponse(
  UUID id,
  UUID subjectId,
  String titleRu,
  String titleKz,
  String descriptionRu,
  String descriptionKz,
  Integer sortOrder,
  long questionCount,
  long theoryCount,
  String contentRole,
  com.fasterxml.jackson.databind.JsonNode curriculum,
  String documentLanguage,
  String sourceUrl
) {}
