package ent.kz.entbackend.service;

import ent.kz.entbackend.dto.TheoryResponse;
import ent.kz.entbackend.entity.Theory;
import ent.kz.entbackend.repository.TheoryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TheoryService {

  @org.springframework.beans.factory.annotation.Autowired
  private ContentAccess access;

  private final TheoryRepository theoryRepository;

  public TheoryService(TheoryRepository theoryRepository) {
    this.theoryRepository = theoryRepository;
  }

  public List<TheoryResponse> getActiveTheoriesByTopicId(UUID topicId) {
    access.topic(topicId);
    return theoryRepository
      .findByTopicIdAndIsActiveTrueOrderBySortOrderAsc(topicId)
      .stream()
      .map(this::toResponse)
      .toList();
  }

  private TheoryResponse toResponse(Theory theory) {
    return new TheoryResponse(
      theory.getId(),
      theory.getTopic().getId(),
      theory.getTitleRu(),
      theory.getTitleKz(),
      theory.getContentRu(),
      theory.getContentKz(),
      theory.getSortOrder()
    );
  }
}
