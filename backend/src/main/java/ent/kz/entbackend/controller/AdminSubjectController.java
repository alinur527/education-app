package ent.kz.entbackend.controller;

import ent.kz.entbackend.dto.SubjectAdminRequest;
import ent.kz.entbackend.dto.SubjectAdminResponse;
import ent.kz.entbackend.service.AdminSubjectService;
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
@RequestMapping("/api/admin/subjects")
public class AdminSubjectController {

  private final AdminSubjectService adminSubjectService;

  public AdminSubjectController(AdminSubjectService adminSubjectService) {
    this.adminSubjectService = adminSubjectService;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
  public List<SubjectAdminResponse> getSubjects() {
    return adminSubjectService.getAllSubjects();
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
  public SubjectAdminResponse createSubject(
    @Valid @RequestBody SubjectAdminRequest request
  ) {
    return adminSubjectService.createSubject(request);
  }

  @PutMapping(
    value = "/{id}",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public SubjectAdminResponse updateSubject(
    @PathVariable UUID id,
    @Valid @RequestBody SubjectAdminRequest request
  ) {
    return adminSubjectService.updateSubject(id, request);
  }

  @DeleteMapping("/{id}")
  public void deleteSubject(@PathVariable UUID id) {
    adminSubjectService.softDeleteSubject(id);
  }
}
