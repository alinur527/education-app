package ent.kz.entbackend.controller;

import ent.kz.entbackend.dto.SubjectResponse;
import ent.kz.entbackend.service.SubjectService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

  private final SubjectService subjectService;

  public SubjectController(SubjectService subjectService) {
    this.subjectService = subjectService;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
  public List<SubjectResponse> getSubjects() {
    return subjectService.getActiveSubjects();
  }
}
