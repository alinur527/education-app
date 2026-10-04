package ent.kz.entbackend.platform.content;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class ContentDtos {

  private ContentDtos() {}

  public record Write(
    @NotNull ContentKind kind,
    UUID parentId,
    @NotNull JsonNode payload,
    Long version
  ) {}

  public record Transition(
    @NotNull @Pattern(regexp = "DRAFT|REVIEW|PUBLISHED|ARCHIVED") String status,
    @Min(1) long version
  ) {}

  public record View(
    UUID id,
    ContentKind kind,
    UUID parentId,
    UUID ownerId,
    String titleRu,
    String titleKz,
    JsonNode payload,
    String status,
    long version,
    Long publishedVersion,
    UUID createdBy,
    UUID updatedBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
  ) {
    public static View of(ContentRecord c) {
      return new View(
        c.id(),
        c.kind(),
        c.parentId(),
        c.ownerId(),
        c.titleRu(),
        c.titleKz(),
        c.payload(),
        c.status(),
        c.version(),
        c.publishedVersion(),
        c.createdBy(),
        c.updatedBy(),
        c.createdAt(),
        c.updatedAt()
      );
    }
  }

  public record Summary(
    UUID id,
    ContentKind kind,
    UUID parentId,
    UUID ownerId,
    String titleRu,
    String titleKz,
    String status,
    long version,
    Long publishedVersion,
    UUID createdBy,
    UUID updatedBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
  ) {}

  public record Page<T>(List<T> items, int page, int size, long total) {}

  public record Published(
    UUID id,
    ContentKind kind,
    UUID parentId,
    JsonNode content
  ) {}
}
