package ent.kz.entbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "test_answers")
public class TestAnswer {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "session_id", nullable = false)
  private TestSession session;

  @ManyToOne
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  @Column(name = "selected_option_id", length = 10)
  private String selectedOptionId;

  @Column(name = "answer_payload", columnDefinition = "jsonb")
  @org.hibernate.annotations.ColumnTransformer(
    read = "answer_payload::text",
    write = "?::jsonb"
  )
  private String answerPayload;

  @Column(name = "earned_points")
  private Integer earnedPoints;

  @Column(name = "max_points")
  private Integer maxPoints;

  @Column(name = "is_correct")
  private Boolean isCorrect;

  @Column(name = "time_spent_secs")
  private Integer timeSpentSecs;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
