package ent.kz.entbackend.service;

import ent.kz.entbackend.dto.TopicResponse;
import ent.kz.entbackend.entity.Topic;
import ent.kz.entbackend.repository.TopicRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TopicService {

  @org.springframework.beans.factory.annotation.Autowired
  private ContentAccess access;

  private final TopicRepository topicRepository;

  @org.springframework.beans.factory.annotation.Autowired
  private ent.kz.entbackend.repository.QuestionRepository questions;

  @org.springframework.beans.factory.annotation.Autowired
  private ent.kz.entbackend.repository.TheoryRepository theories;

  public TopicService(TopicRepository topicRepository) {
    this.topicRepository = topicRepository;
  }

  public List<TopicResponse> getActiveTopicsBySubjectId(UUID subjectId) {
    access.subject(subjectId);
    return topicRepository
      .findBySubjectIdAndIsActiveTrueOrderBySortOrderAsc(subjectId)
      .stream()
      .map(this::toResponse)
      .toList();
  }

  public TopicResponse getTopic(UUID id) {
    return toResponse(access.topic(id));
  }

  private TopicResponse toResponse(Topic topic) {
    return new TopicResponse(
      topic.getId(),
      topic.getSubject().getId(),
      topic.getTitleRu(),
      topic.getTitleKz(),
      topic.getDescriptionRu(),
      topic.getDescriptionKz(),
      topic.getSortOrder(),
      questions.countByTopicIdAndIsActiveTrue(topic.getId()),
      theories.countByTopicIdAndIsActiveTrue(topic.getId())
    );
  }
}
