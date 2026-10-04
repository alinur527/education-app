package ent.kz.entbackend.platform.study;

import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
  name = "app.study.notifications.enabled",
  havingValue = "true",
  matchIfMissing = true
)
public class StudyNotificationScheduler {

  private final StudyRepository repo;
  private final StudyNotificationService service;
  private UUID cursor;

  public StudyNotificationScheduler(
    StudyRepository repo,
    StudyNotificationService service
  ) {
    this.repo = repo;
    this.service = service;
  }

  @Scheduled(
    fixedDelayString = "${app.study.notifications.interval-ms:60000}",
    initialDelayString = "${app.study.notifications.initial-delay-ms:30000}"
  )
  public void run() {
    List<UUID> users = repo
      .jdbc()
      .queryForList(
        "SELECT id FROM users WHERE is_active AND (?::uuid IS NULL OR id>?::uuid) ORDER BY id LIMIT 100",
        UUID.class,
        cursor,
        cursor
      );
    if (users.isEmpty()) {
      cursor = null;
      return;
    }
    for (UUID user : users) {
      try {
        service.process(user, Instant.now());
      } catch (RuntimeException e) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn(
          "Study notification processing failed: {}",
          e.getClass().getSimpleName()
        );
      }
      cursor = user;
    }
  }
}
