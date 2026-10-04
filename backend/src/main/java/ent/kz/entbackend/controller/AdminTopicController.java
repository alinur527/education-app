package ent.kz.entbackend.controller;

import ent.kz.entbackend.dto.TopicAdminRequest;
import ent.kz.entbackend.dto.TopicAdminResponse;
import ent.kz.entbackend.service.AdminTopicService;
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

@org.springframework.transaction.annotation.Transactional
@RestController
@RequestMapping("/api/admin/topics")
public class AdminTopicController {

  private final AdminTopicService adminTopicService;

  @org.springframework.beans.factory.annotation.Autowired
  private ent.kz.entbackend.platform.content.LegacyContentBridge editorial;

  public AdminTopicController(AdminTopicService adminTopicService) {
    this.adminTopicService = adminTopicService;
  }

  @GetMapping(
    value = "/subject/{subjectId}",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public List<TopicAdminResponse> getTopicsBySubject(
    @PathVariable UUID subjectId
  ) {
    return adminTopicService.getTopicsBySubject(subjectId);
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
  public TopicAdminResponse createTopic(
    @Valid @RequestBody TopicAdminRequest request
  ) {
    var result = adminTopicService.createTopic(request);
    editorial.sync(
      ent.kz.entbackend.platform.content.ContentKind.TOPIC,
      result.id()
    );
    return result;
  }

  @PutMapping(
    value = "/{id}",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public TopicAdminResponse updateTopic(
    @PathVariable UUID id,
    @Valid @RequestBody TopicAdminRequest request
  ) {
    editorial.lockExisting(id);
    var result = adminTopicService.updateTopic(id, request);
    editorial.sync(
      ent.kz.entbackend.platform.content.ContentKind.TOPIC,
      result.id()
    );
    return result;
  }

  @DeleteMapping("/{id}")
  public void deleteTopic(@PathVariable UUID id) {
    editorial.lockExisting(id);
    adminTopicService.softDeleteTopic(id);
    editorial.sync(ent.kz.entbackend.platform.content.ContentKind.TOPIC, id);
  }
}
