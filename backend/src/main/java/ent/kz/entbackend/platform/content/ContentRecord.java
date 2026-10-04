package ent.kz.entbackend.platform.content;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Internal editorial aggregate; student responses are constructed separately. */
public record ContentRecord(
  UUID id,
  ContentKind kind,
  UUID parentId,
  UUID ownerId,
  String titleRu,
  String titleKz,
  JsonNode payload,
  JsonNode publishedPayload,
  String status,
  long version,
  Long publishedVersion,
  UUID createdBy,
  UUID updatedBy,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {}
