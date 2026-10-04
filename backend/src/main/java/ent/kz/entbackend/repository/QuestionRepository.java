package ent.kz.entbackend.repository;

import ent.kz.entbackend.entity.Question;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, UUID> {
  List<Question> findByTopicIdAndIsActiveTrue(UUID topicId);

  long countByTopicIdAndIsActiveTrue(UUID topicId);
  long countBySubjectIdAndIsActiveTrueAndTopicIsActiveTrue(UUID subjectId);
  List<Question> findByTopicId(UUID topicId);
}
