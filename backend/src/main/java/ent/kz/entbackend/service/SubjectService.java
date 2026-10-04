package ent.kz.entbackend.service;

import ent.kz.entbackend.dto.SubjectResponse;
import ent.kz.entbackend.repository.CurriculumQueryRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SubjectService {

  private final CurriculumQueryRepository queries;

  public SubjectService(CurriculumQueryRepository queries) {
    this.queries = queries;
  }

  public List<SubjectResponse> getActiveSubjects() {
    return queries.subjects();
  }
}
