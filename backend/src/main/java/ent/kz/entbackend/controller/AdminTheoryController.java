package ent.kz.entbackend.controller;

import ent.kz.entbackend.dto.TheoryAdminRequest;
import ent.kz.entbackend.dto.TheoryAdminResponse;
import ent.kz.entbackend.service.AdminTheoryService;
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
@RequestMapping("/api/admin/theories")
public class AdminTheoryController {

  private final AdminTheoryService adminTheoryService;

  public AdminTheoryController(AdminTheoryService adminTheoryService) {
    this.adminTheoryService = adminTheoryService;
  }

  @GetMapping(
    value = "/topic/{topicId}",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public List<TheoryAdminResponse> getTheoriesByTopic(
    @PathVariable UUID topicId
  ) {
    return adminTheoryService.getTheoriesByTopic(topicId);
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
  public TheoryAdminResponse createTheory(
    @Valid @RequestBody TheoryAdminRequest request
  ) {
    return adminTheoryService.createTheory(request);
  }

  @PutMapping(
    value = "/{id}",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public TheoryAdminResponse updateTheory(
    @PathVariable UUID id,
    @Valid @RequestBody TheoryAdminRequest request
  ) {
    return adminTheoryService.updateTheory(id, request);
  }

  @DeleteMapping("/{id}")
  public void deleteTheory(@PathVariable UUID id) {
    adminTheoryService.softDeleteTheory(id);
  }
}
