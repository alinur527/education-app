package ent.kz.entbackend.controller;

import ent.kz.entbackend.dto.QuestionAdminRequest;
import ent.kz.entbackend.dto.QuestionAdminResponse;
import ent.kz.entbackend.service.AdminQuestionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionController {

  private final AdminQuestionService adminQuestionService;

  public AdminQuestionController(AdminQuestionService adminQuestionService) {
    this.adminQuestionService = adminQuestionService;
  }

  @GetMapping(
    value = "/topic/{topicId}",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public List<QuestionAdminResponse> getQuestionsByTopic(
    @PathVariable UUID topicId
  ) {
    return adminQuestionService.getQuestionsByTopic(topicId);
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
  public QuestionAdminResponse createQuestion(
    @Valid @RequestBody QuestionAdminRequest request
  ) {
    return adminQuestionService.createQuestion(request);
  }

  @PutMapping(
    value = "/{id}",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public QuestionAdminResponse updateQuestion(
    @PathVariable UUID id,
    @Valid @RequestBody QuestionAdminRequest request
  ) {
    return adminQuestionService.updateQuestion(id, request);
  }

  @DeleteMapping("/{id}")
  public void deleteQuestion(@PathVariable UUID id) {
    adminQuestionService.softDeleteQuestion(id);
  }
}
