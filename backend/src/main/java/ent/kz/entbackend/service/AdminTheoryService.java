package ent.kz.entbackend.service;

import ent.kz.entbackend.dto.TheoryAdminRequest;
import ent.kz.entbackend.dto.TheoryAdminResponse;
import ent.kz.entbackend.entity.Theory;
import ent.kz.entbackend.entity.Topic;
import ent.kz.entbackend.repository.TheoryRepository;
import ent.kz.entbackend.repository.TopicRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminTheoryService {

  private final TheoryRepository theoryRepository;
  private final TopicRepository topicRepository;

  public AdminTheoryService(
    TheoryRepository theoryRepository,
    TopicRepository topicRepository
  ) {
    this.theoryRepository = theoryRepository;
    this.topicRepository = topicRepository;
  }

  public List<TheoryAdminResponse> getTheoriesByTopic(UUID topicId) {
    ensureTopicExists(topicId);
    return theoryRepository
      .findByTopicIdOrderBySortOrderAsc(topicId)
      .stream()
      .map(this::toResponse)
      .toList();
  }

  public TheoryAdminResponse createTheory(TheoryAdminRequest request) {
    Topic topic = topicRepository
      .findById(request.topicId())
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found")
      );

    LocalDateTime now = LocalDateTime.now();
    Theory theory = new Theory();
    theory.setTopic(topic);
    theory.setSubject(topic.getSubject());
    applyRequest(theory, request);
    theory.setCreatedAt(now);
    theory.setUpdatedAt(now);

    return toResponse(theoryRepository.save(theory));
  }

  public TheoryAdminResponse updateTheory(UUID id, TheoryAdminRequest request) {
    Theory theory = theoryRepository
      .findById(id)
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Theory not found")
      );
    Topic topic = topicRepository
      .findById(request.topicId())
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found")
      );

    theory.setTopic(topic);
    theory.setSubject(topic.getSubject());
    applyRequest(theory, request);
    theory.setUpdatedAt(LocalDateTime.now());

    return toResponse(theoryRepository.save(theory));
  }

  public void softDeleteTheory(UUID id) {
    Theory theory = theoryRepository
      .findById(id)
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Theory not found")
      );

    theory.setIsActive(false);
    theory.setUpdatedAt(LocalDateTime.now());
    theoryRepository.save(theory);
  }

  private void ensureTopicExists(UUID topicId) {
    if (!topicRepository.existsById(topicId)) {
      throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Topic not found"
      );
    }
  }

  private void applyRequest(Theory theory, TheoryAdminRequest request) {
    theory.setTitleRu(request.titleRu());
    theory.setTitleKz(request.titleKz());
    theory.setContentRu(request.contentRu());
    theory.setContentKz(request.contentKz());
    theory.setSortOrder(request.sortOrder());
    theory.setIsActive(request.isActive());
  }

  private TheoryAdminResponse toResponse(Theory theory) {
    return new TheoryAdminResponse(
      theory.getId(),
      theory.getTopic() != null ? theory.getTopic().getId() : null,
      theory.getTitleRu(),
      theory.getTitleKz(),
      theory.getContentRu(),
      theory.getContentKz(),
      theory.getSortOrder(),
      theory.getIsActive()
    );
  }
}
