package ent.kz.entbackend.service;

import ent.kz.entbackend.dto.SubjectResponse;
import ent.kz.entbackend.entity.Subject;
import ent.kz.entbackend.repository.SubjectRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SubjectService {

  private final SubjectRepository subjectRepository;

  @org.springframework.beans.factory.annotation.Autowired
  private ent.kz.entbackend.repository.QuestionRepository questions;

  public SubjectService(SubjectRepository subjectRepository) {
    this.subjectRepository = subjectRepository;
  }

  public List<SubjectResponse> getActiveSubjects() {
    return subjectRepository
      .findByIsActiveTrue()
      .stream()
      .sorted(
        java.util.Comparator.comparing(Subject::getCreatedAt).thenComparing(
          Subject::getId
        )
      )
      .map(this::toResponse)
      .toList();
  }

  private SubjectResponse toResponse(Subject subject) {
    return new SubjectResponse(
      subject.getId(),
      subject.getNameRu(),
      subject.getNameKz(),
      subject.getIcon(),
      subject.getColor(),
      Math.toIntExact(
        questions.countBySubjectIdAndIsActiveTrueAndTopicIsActiveTrue(
          subject.getId()
        )
      ),
      subject.getDurationMinutes()
    );
  }
}
