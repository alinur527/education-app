package ent.kz.entbackend.controller;

import ent.kz.entbackend.dto.TopicResponse;
import ent.kz.entbackend.service.TopicService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

  private final TopicService topicService;

  public TopicController(TopicService topicService) {
    this.topicService = topicService;
  }

  @GetMapping("/{topicId}")
  public TopicResponse getTopic(@PathVariable UUID topicId) {
    return topicService.getTopic(topicId);
  }

  @GetMapping(
    value = "/subject/{subjectId}",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public List<TopicResponse> getTopicsBySubject(@PathVariable UUID subjectId) {
    return topicService.getActiveTopicsBySubjectId(subjectId);
  }
}
