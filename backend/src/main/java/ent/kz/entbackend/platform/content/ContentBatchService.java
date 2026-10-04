package ent.kz.entbackend.platform.content;

import static ent.kz.entbackend.platform.PlatformException.require;

import com.fasterxml.jackson.databind.ObjectMapper;
import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.importing.ContentPackService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ContentBatchService {

  public record Item(UUID id, long version) {}

  public record Request(List<Item> items, String target, String confirmation) {}

  private final Actor actor;
  private final ContentRepository records;
  private final ContentValidation validation;
  private final ContentService content;
  private final ObjectMapper json;

  public ContentBatchService(
    Actor actor,
    ContentRepository records,
    ContentValidation validation,
    ContentService content,
    ObjectMapper json
  ) {
    this.actor = actor;
    this.records = records;
    this.validation = validation;
    this.content = content;
    this.json = json;
  }

  public Object apply(Request req, boolean confirm) {
    actor.editorOnly();
    require(
      req.items() != null &&
        !req.items().isEmpty() &&
        req.items().size() <= 100,
      "BULK_SIZE"
    );
    require(
      Set.of("REVIEW", "PUBLISHED").contains(
        Objects.toString(req.target(), "")
      ),
      "BULK_TARGET"
    );
    Set<UUID> ids = new HashSet<>();
    for (Item item : req.items())
      require(
        item != null && item.id() != null && ids.add(item.id()),
        "BULK_DUPLICATE"
      );
    Map<UUID, ContentRecord> locked = new HashMap<>();
    ids
      .stream()
      .sorted()
      .forEach(id -> locked.put(id, records.get(id, confirm)));
    List<Map<String, Object>> rows = new ArrayList<>();
    boolean valid = true;
    for (Item item : req.items()) {
      var c = locked.get(item.id());
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", c.id());
      row.put("version", c.version());
      row.put("titleRu", c.titleRu());
      row.put("titleKz", c.titleKz());
      row.put("from", c.status());
      row.put("to", req.target());
      row.put("checksum", ContentPackService.checksum(c.payload()));
      try {
        require(c.version() == item.version(), "REVISION_CONFLICT");
        require(!c.status().equals("ARCHIVED"), "ARCHIVED_CONTENT");
        validation.validate(c.kind(), c.payload(), true);
      } catch (PlatformException e) {
        row.put("error", e.code());
        valid = false;
      }
      rows.add(row);
    }
    String token = ContentPackService.checksum(
      json.valueToTree(Map.of("actor", actor.id(), "rows", rows))
    );
    if (confirm) {
      if (
        !valid || !token.equals(req.confirmation())
      ) throw new PlatformException(409, "BULK_PREVIEW_STALE");
      // The provided order is deliberate: parents must publish before children.
      for (Item item : req.items()) {
        var c = content.get(item.id());
        if (c.status().equals(req.target())) continue;
        if (c.status().equals("PUBLISHED")) c = content.transition(
          c.id(),
          new ContentDtos.Transition("DRAFT", c.version())
        );
        if (c.status().equals("DRAFT")) c = content.transition(
          c.id(),
          new ContentDtos.Transition("REVIEW", c.version())
        );
        if (req.target().equals("PUBLISHED")) content.transition(
          c.id(),
          new ContentDtos.Transition("PUBLISHED", c.version())
        );
      }
    }
    return Map.of(
      "valid",
      valid,
      "confirmation",
      token,
      "rows",
      rows,
      "applied",
      confirm
    );
  }
}
