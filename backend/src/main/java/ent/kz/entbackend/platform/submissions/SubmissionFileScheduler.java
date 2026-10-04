package ent.kz.entbackend.platform.submissions;

import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
  name = "app.submission-files.worker-enabled",
  havingValue = "true",
  matchIfMissing = true
)
public class SubmissionFileScheduler {

  private final SubmissionFileRepository repo;
  private final SubmissionFileScanning scanning;

  public SubmissionFileScheduler(
    SubmissionFileRepository repo,
    SubmissionFileScanning scanning
  ) {
    this.repo = repo;
    this.scanning = scanning;
  }

  @Scheduled(
    fixedDelayString = "${app.submission-files.retry-ms:60000}",
    initialDelayString = "${app.submission-files.initial-delay-ms:60000}"
  )
  public void retry() {
    for (UUID id : repo
      .jdbc()
      .queryForList(
        "SELECT id FROM submission_files WHERE deleted_at IS NULL AND scan_status IN ('PENDING','SCAN_FAILED','UNSCANNED') AND scan_attempts<5 AND next_scan_at<=now() ORDER BY next_scan_at,id LIMIT 25",
        UUID.class
      ))
      try {
        scanning.scan(id, false);
      } catch (RuntimeException e) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn(
          "Submission scan retry failed: {}",
          e.getClass().getSimpleName()
        );
      }
  }
}
