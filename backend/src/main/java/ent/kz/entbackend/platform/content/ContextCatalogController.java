package ent.kz.entbackend.platform.content;

import ent.kz.entbackend.platform.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class ContextCatalogController {

  private final Actor actor;
  private final ContentRepository records;
  private final JdbcTemplate db;

  public ContextCatalogController(
    Actor actor,
    ContentRepository records,
    JdbcTemplate db
  ) {
    this.actor = actor;
    this.records = records;
    this.db = db;
  }

  @GetMapping("/api/cms/contexts")
  public Object list(
    @RequestParam UUID topicId,
    @RequestParam(defaultValue = "") String q
  ) {
    actor.editorOnly();
    if (
      records.get(topicId, false).kind() != ContentKind.TOPIC
    ) throw PlatformException.missing();
    PlatformException.require(q.length() <= 200, "QUERY_TOO_LONG");
    return db.queryForList(
      "SELECT id,published_version AS version,published_payload->>'titleRu' AS \"titleRu\",published_payload->>'titleKz' AS \"titleKz\",published_payload->>'contentRu' AS \"contentRu\",published_payload->>'contentKz' AS \"contentKz\" FROM content_records WHERE kind='CONTEXT' AND parent_id=? AND published_payload IS NOT NULL AND status<>'ARCHIVED' AND (published_payload->>'titleRu' ILIKE ? OR published_payload->>'titleKz' ILIKE ?) ORDER BY title_ru,id LIMIT 50",
      topicId,
      "%" + q + "%",
      "%" + q + "%"
    );
  }
}
