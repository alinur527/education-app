package ent.kz.entbackend.platform.content;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {

  private final SearchService service;

  public SearchController(SearchService service) {
    this.service = service;
  }

  @GetMapping
  public List<Map<String, Object>> search(
    @RequestParam(defaultValue = "") String q
  ) {
    return service.search(q);
  }
}
