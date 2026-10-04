package ent.kz.entbackend.platform.content;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cms")
public class EditorialController {

  private final EditorialReviewService reviews;
  private final ContentBatchService batches;

  public EditorialController(
    EditorialReviewService reviews,
    ContentBatchService batches
  ) {
    this.reviews = reviews;
    this.batches = batches;
  }

  @GetMapping("/content/{id}/editorial-review")
  public Object reviews(@PathVariable UUID id) {
    return reviews.list(id);
  }

  @PostMapping("/content/{id}/editorial-review")
  public Object review(
    @PathVariable UUID id,
    @RequestBody EditorialReviewService.Review request
  ) {
    return reviews.save(id, request);
  }

  @PostMapping("/content-batches/preview")
  public Object preview(@RequestBody ContentBatchService.Request request) {
    return batches.apply(request, false);
  }

  @PostMapping("/content-batches/confirm")
  public Object confirm(@RequestBody ContentBatchService.Request request) {
    return batches.apply(request, true);
  }
}
