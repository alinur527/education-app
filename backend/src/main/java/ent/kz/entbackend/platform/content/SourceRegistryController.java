package ent.kz.entbackend.platform.content;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cms/sources")
public class SourceRegistryController {

  private final SourceRegistryService service;

  public SourceRegistryController(SourceRegistryService service) {
    this.service = service;
  }

  @GetMapping
  public List<SourceRegistryService.Source> list(
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "0") int page
  ) {
    return service.list(q, page);
  }

  @PostMapping
  public List<SourceRegistryService.Source> save(
    @RequestBody List<SourceRegistryService.Source> sources
  ) {
    return service.save(sources);
  }
}
