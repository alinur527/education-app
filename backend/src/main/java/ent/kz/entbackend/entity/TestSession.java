package ent.kz.entbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "test_sessions")
public class TestSession {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne
  @JoinColumn(name = "subject_id", nullable = false)
  private Subject subject;

  @ManyToOne
  @JoinColumn(name = "topic_id")
  private Topic topic;

  @Column(name = "question_snapshot", columnDefinition = "jsonb")
  @ColumnTransformer(read = "question_snapshot::text", write = "?::jsonb")
  private String questionSnapshot;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "question_ids", nullable = false, columnDefinition = "jsonb")
  @ColumnTransformer(read = "question_ids::text", write = "?::jsonb")
  private String questionIds;

  @Column(name = "total_questions", nullable = false)
  private Integer totalQuestions;

  @Column(name = "correct_answers", nullable = false)
  private Integer correctAnswers;

  @Column(name = "score", precision = 5, scale = 2)
  private BigDecimal score;

  @Column(name = "time_taken_secs")
  private Integer timeTakenSecs;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
