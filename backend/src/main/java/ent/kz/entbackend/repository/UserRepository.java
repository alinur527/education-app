package ent.kz.entbackend.repository;

import ent.kz.entbackend.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.transaction.annotation.Transactional
  @org.springframework.data.jpa.repository.Query(
    "update User u set u.lastLoginAt=:at where u.id=:id"
  )
  void recordLogin(UUID id, java.time.LocalDateTime at);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.transaction.annotation.Transactional
  @org.springframework.data.jpa.repository.Query(
    "update User u set u.language=:language where u.id=:id and u.isActive=true"
  )
  void changeLanguage(UUID id, String language);
}
