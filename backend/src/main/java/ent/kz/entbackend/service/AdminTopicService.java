package ent.kz.entbackend.service;

import ent.kz.entbackend.dto.TopicAdminRequest;
import ent.kz.entbackend.dto.TopicAdminResponse;
import ent.kz.entbackend.entity.Subject;
import ent.kz.entbackend.entity.Topic;
import ent.kz.entbackend.repository.SubjectRepository;
import ent.kz.entbackend.repository.TopicRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@org.springframework.transaction.annotation.Transactional
public class AdminTopicService {

  private final TopicRepository topicRepository;
  private final SubjectRepository subjectRepository;
  private final ent.kz.entbackend.repository.QuestionRepository questions;
  private final ent.kz.entbackend.repository.TheoryRepository theories;

  public AdminTopicService(
    TopicRepository topicRepository,
    SubjectRepository subjectRepository,
    ent.kz.entbackend.repository.QuestionRepository questions,
    ent.kz.entbackend.repository.TheoryRepository theories
  ) {
    this.topicRepository = topicRepository;
    this.subjectRepository = subjectRepository;
    this.questions = questions;
    this.theories = theories;
  }

  public List<TopicAdminResponse> getTopicsBySubject(UUID subjectId) {
    ensureSubjectExists(subjectId);
    return topicRepository
      .findBySubjectIdOrderBySortOrderAsc(subjectId)
      .stream()
      .map(this::toResponse)
      .toList();
  }

  public TopicAdminResponse createTopic(TopicAdminRequest request) {
    Subject subject = subjectRepository
      .findById(request.subjectId())
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found")
      );

    LocalDateTime now = LocalDateTime.now();
    Topic topic = new Topic();
    topic.setSubject(subject);
    applyRequest(topic, request);
    topic.setCreatedAt(now);
    topic.setUpdatedAt(now);

    return toResponse(topicRepository.save(topic));
  }

  public TopicAdminResponse updateTopic(UUID id, TopicAdminRequest request) {
    Topic topic = topicRepository
      .findById(id)
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found")
      );
    Subject subject = subjectRepository
      .findById(request.subjectId())
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found")
      );

    topic.setSubject(subject);
    applyRequest(topic, request);
    topic.setUpdatedAt(LocalDateTime.now());
    questions.findByTopicId(id).forEach(q -> {
      q.setSubject(subject);
      q.setTopicRu(topic.getTitleRu());
      q.setTopicKz(topic.getTitleKz());
      q.setUpdatedAt(LocalDateTime.now());
    });
    theories.findByTopicIdOrderBySortOrderAsc(id).forEach(t -> {
      t.setSubject(subject);
      t.setUpdatedAt(LocalDateTime.now());
    });

    return toResponse(topicRepository.save(topic));
  }

  public void softDeleteTopic(UUID id) {
    Topic topic = topicRepository
      .findById(id)
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found")
      );

    topic.setIsActive(false);
    topic.setUpdatedAt(LocalDateTime.now());
    topicRepository.save(topic);
  }

  private void ensureSubjectExists(UUID subjectId) {
    if (!subjectRepository.existsById(subjectId)) {
      throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Subject not found"
      );
    }
  }

  private void applyRequest(Topic topic, TopicAdminRequest request) {
    topic.setTitleRu(request.titleRu());
    topic.setTitleKz(request.titleKz());
    topic.setDescriptionRu(request.descriptionRu());
    topic.setDescriptionKz(request.descriptionKz());
    topic.setSortOrder(request.sortOrder());
    topic.setIsActive(request.isActive());
  }

  private TopicAdminResponse toResponse(Topic topic) {
    return new TopicAdminResponse(
      topic.getId(),
      topic.getSubject().getId(),
      topic.getTitleRu(),
      topic.getTitleKz(),
      topic.getDescriptionRu(),
      topic.getDescriptionKz(),
      topic.getSortOrder(),
      topic.getIsActive()
    );
  }
}
