package ent.kz.entbackend.platform.content;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/content")
public class PublishedContentController {

  private final ContentService service;

  public PublishedContentController(ContentService service) {
    this.service = service;
  }

  @GetMapping("/{id}")
  public ContentDtos.Published get(@PathVariable UUID id) {
    return service.published(id);
  }
}
