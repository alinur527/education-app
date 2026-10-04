package ent.kz.entbackend.service;

import ent.kz.entbackend.dto.SubjectAdminRequest;
import ent.kz.entbackend.dto.SubjectAdminResponse;
import ent.kz.entbackend.entity.Subject;
import ent.kz.entbackend.repository.SubjectRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminSubjectService {

  private final SubjectRepository subjectRepository;

  public AdminSubjectService(SubjectRepository subjectRepository) {
    this.subjectRepository = subjectRepository;
  }

  public List<SubjectAdminResponse> getAllSubjects() {
    return subjectRepository
      .findAll()
      .stream()
      .sorted(
        Comparator.comparing(
          Subject::getCreatedAt,
          Comparator.nullsLast(Comparator.naturalOrder())
        ).thenComparing(Subject::getId)
      )
      .map(this::toResponse)
      .toList();
  }

  public SubjectAdminResponse createSubject(SubjectAdminRequest request) {
    LocalDateTime now = LocalDateTime.now();

    Subject subject = new Subject();
    applyRequest(subject, request);
    subject.setCreatedAt(now);
    subject.setUpdatedAt(now);

    return toResponse(subjectRepository.save(subject));
  }

  public SubjectAdminResponse updateSubject(
    UUID id,
    SubjectAdminRequest request
  ) {
    Subject subject = subjectRepository
      .findById(id)
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found")
      );

    applyRequest(subject, request);
    subject.setUpdatedAt(LocalDateTime.now());

    return toResponse(subjectRepository.save(subject));
  }

  public void softDeleteSubject(UUID id) {
    Subject subject = subjectRepository
      .findById(id)
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found")
      );

    subject.setIsActive(false);
    subject.setUpdatedAt(LocalDateTime.now());
    subjectRepository.save(subject);
  }

  private void applyRequest(Subject subject, SubjectAdminRequest request) {
    subject.setNameRu(request.nameRu());
    subject.setNameKz(request.nameKz());
    subject.setIcon(request.icon());
    subject.setColor(request.color());
    subject.setQuestionCount(request.questionCount());
    subject.setDurationMinutes(request.durationMinutes());
    subject.setIsActive(request.isActive());
  }

  private SubjectAdminResponse toResponse(Subject subject) {
    return new SubjectAdminResponse(
      subject.getId(),
      subject.getNameRu(),
      subject.getNameKz(),
      subject.getIcon(),
      subject.getColor(),
      subject.getQuestionCount(),
      subject.getDurationMinutes(),
      subject.getIsActive()
    );
  }
}
