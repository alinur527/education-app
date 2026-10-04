package ent.kz.entbackend.repository;

import ent.kz.entbackend.entity.TestAnswer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestAnswerRepository extends JpaRepository<TestAnswer, UUID> {
  long countBySessionId(UUID sessionId);

  long countBySessionIdAndIsCorrectTrue(UUID sessionId);

  boolean existsBySessionIdAndQuestionId(UUID sessionId, UUID questionId);

  Optional<TestAnswer> findBySessionIdAndQuestionId(
    UUID sessionId,
    UUID questionId
  );

  List<TestAnswer> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
