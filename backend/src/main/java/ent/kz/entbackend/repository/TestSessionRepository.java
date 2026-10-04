package ent.kz.entbackend.repository;

import ent.kz.entbackend.entity.TestSession;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TestSessionRepository
  extends JpaRepository<TestSession, UUID>
{
  Optional<TestSession> findByIdAndUserId(UUID id, UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from TestSession s where s.id=:id and s.user.id=:userId")
  Optional<TestSession> findOwnedForUpdate(
    @Param("id") UUID id,
    @Param("userId") UUID userId
  );

  List<TestSession> findByUserIdAndStatusOrderByCompletedAtDesc(
    UUID userId,
    String status
  );
  List<TestSession> findTop5ByUserIdAndStatusOrderByUpdatedAtDesc(
    UUID userId,
    String status
  );
}
