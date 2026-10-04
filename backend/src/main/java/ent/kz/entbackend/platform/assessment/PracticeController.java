package ent.kz.entbackend.platform.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import ent.kz.entbackend.dto.StartTestResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/practice")
public class PracticeController {

  private final PracticeService service;

  public PracticeController(PracticeService service) {
    this.service = service;
  }

  @GetMapping
  public Object catalog() {
    return service.catalog();
  }

  @GetMapping("/exam-bank")
  public JsonNode bank(@RequestParam int profilePair) {
    return service.availability(profilePair);
  }

  @PostMapping("/sessions")
  public StartTestResponse start(@RequestBody PracticeService.Start request) {
    return service.start(request);
  }
}
