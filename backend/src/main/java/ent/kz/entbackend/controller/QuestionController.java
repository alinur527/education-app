package ent.kz.entbackend.controller;

import ent.kz.entbackend.dto.QuestionResponse;
import ent.kz.entbackend.service.QuestionService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

  private final QuestionService questionService;

  public QuestionController(QuestionService questionService) {
    this.questionService = questionService;
  }

  @GetMapping(
    value = "/topic/{topicId}",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public List<QuestionResponse> getQuestionsByTopic(
    @PathVariable UUID topicId
  ) {
    return questionService.getActiveQuestionsByTopicId(topicId);
  }
}
