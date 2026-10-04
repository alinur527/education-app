package ent.kz.entbackend.controller;

import ent.kz.entbackend.dto.TheoryResponse;
import ent.kz.entbackend.service.TheoryService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/theories")
public class TheoryController {

  private final TheoryService theoryService;

  public TheoryController(TheoryService theoryService) {
    this.theoryService = theoryService;
  }

  @GetMapping(
    value = "/topic/{topicId}",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public List<TheoryResponse> getTheoriesByTopic(@PathVariable UUID topicId) {
    return theoryService.getActiveTheoriesByTopicId(topicId);
  }
}
