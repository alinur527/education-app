package ent.kz.entbackend.repository;

import ent.kz.entbackend.entity.Theory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TheoryRepository extends JpaRepository<Theory, UUID> {
  List<Theory> findByTopicIdAndIsActiveTrueOrderBySortOrderAsc(UUID topicId);

  long countByTopicIdAndIsActiveTrue(UUID topicId);
  List<Theory> findByTopicIdOrderBySortOrderAsc(UUID topicId);
}
