package ent.kz.entbackend.repository;

import ent.kz.entbackend.entity.Topic;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, UUID> {
  List<Topic> findBySubjectIdAndIsActiveTrueOrderBySortOrderAsc(UUID subjectId);

  List<Topic> findBySubjectIdOrderBySortOrderAsc(UUID subjectId);
}
